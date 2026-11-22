package com.example.ui.components

import com.example.data.remote.simkl.SimklQrMatrix

/**
 * Generatore puro Kotlin conforme allo standard ISO/IEC 18004 (QR Code Model 2, Byte Mode, ECC M)
 * senza dipendenze esterne. Supporta V1..V20 (fino a 666 byte UTF-8) per qualsiasi URI `verification_uri`
 * o `verification_uri_complete` di Simkl AUTH V2.
 */
object SimklQrGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalDataBytes: Int,
        val ecBytesPerBlock: Int,
        val group1Blocks: Int,
        val group1DataBytes: Int,
        val group2Blocks: Int,
        val group2DataBytes: Int,
        val alignmentCenters: IntArray
    )

    // Tabella ufficiale ISO-18004 Livello M (Error Correction ~15%, ideale per lettura da TV)
    private val VERSION_SPECS_M = listOf(
        VersionSpec(1, 16, 10, 1, 16, 0, 0, intArrayOf()),
        VersionSpec(2, 28, 16, 1, 28, 0, 0, intArrayOf(6, 18)),
        VersionSpec(3, 44, 26, 1, 44, 0, 0, intArrayOf(6, 22)),
        VersionSpec(4, 64, 18, 2, 32, 0, 0, intArrayOf(6, 26)),
        VersionSpec(5, 86, 24, 2, 43, 0, 0, intArrayOf(6, 30)),
        VersionSpec(6, 108, 16, 4, 27, 0, 0, intArrayOf(6, 34)),
        VersionSpec(7, 124, 18, 4, 31, 0, 0, intArrayOf(6, 22, 38)),
        VersionSpec(8, 154, 22, 2, 38, 2, 39, intArrayOf(6, 24, 42)),
        VersionSpec(9, 182, 22, 3, 36, 2, 37, intArrayOf(6, 26, 46)),
        VersionSpec(10, 216, 26, 4, 43, 1, 44, intArrayOf(6, 28, 50)),
        VersionSpec(11, 254, 30, 1, 50, 4, 51, intArrayOf(6, 30, 54)),
        VersionSpec(12, 290, 22, 6, 36, 2, 37, intArrayOf(6, 32, 58)),
        VersionSpec(13, 334, 22, 8, 37, 1, 38, intArrayOf(6, 34, 62)),
        VersionSpec(14, 365, 24, 4, 40, 5, 41, intArrayOf(6, 26, 46, 66)),
        VersionSpec(15, 415, 24, 5, 41, 5, 42, intArrayOf(6, 26, 48, 70)),
        VersionSpec(16, 453, 28, 7, 45, 3, 46, intArrayOf(6, 26, 50, 74)),
        VersionSpec(17, 507, 28, 10, 46, 1, 47, intArrayOf(6, 30, 54, 78)),
        VersionSpec(18, 563, 26, 9, 43, 4, 44, intArrayOf(6, 30, 56, 82)),
        VersionSpec(19, 627, 26, 3, 44, 11, 45, intArrayOf(6, 30, 58, 86)),
        VersionSpec(20, 669, 26, 3, 41, 13, 42, intArrayOf(6, 34, 62, 90))
    )

    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    fun generate(text: String): SimklQrMatrix {
        val payload = text.toByteArray(Charsets.UTF_8)
        val spec = VERSION_SPECS_M.firstOrNull { spec ->
            val bitCapacity = spec.totalDataBytes * 8
            val countBits = if (spec.version >= 10) 16 else 8
            val requiredBits = 4 + countBits + payload.size * 8
            requiredBits <= bitCapacity
        } ?: throw IllegalArgumentException("Contenuto QR troppo lungo (${payload.size} byte)")

        val dataCodewords = encodeByteModeData(payload, spec.totalDataBytes, spec.version)
        val finalStream = buildInterleavedCodewords(dataCodewords, spec)

        val size = spec.version * 4 + 17
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        placeFinderPattern(modules, isFunction, 0, 0)
        placeFinderPattern(modules, isFunction, 0, size - 7)
        placeFinderPattern(modules, isFunction, size - 7, 0)
        placeSeparators(modules, isFunction, size)
        placeAlignmentPatterns(modules, isFunction, spec.alignmentCenters)
        placeTimingPatterns(modules, isFunction, size)
        // Dark module fisso (4 * V + 9, 8)
        modules[4 * spec.version + 9][8] = true
        isFunction[4 * spec.version + 9][8] = true

        reserveFormatAreas(isFunction, size)
        if (spec.version >= 7) {
            placeVersionInfo(modules, isFunction, size, spec.version)
        }

        placeDataBits(modules, isFunction, size, finalStream)

        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules: Array<BooleanArray>? = null
        for (mask in 0 until 8) {
            val candidate = Array(size) { r -> modules[r].copyOf() }
            applyMask(candidate, isFunction, size, mask)
            placeFormatInfo(candidate, size, eccLevelBits = 0b00, maskPattern = mask)
            val penalty = calculatePenalty(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }
        val chosen = bestModules ?: modules
        placeFormatInfo(chosen, size, eccLevelBits = 0b00, maskPattern = bestMask)

        val flat = ArrayList<Boolean>(size * size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                flat.add(chosen[r][c])
            }
        }
        return SimklQrMatrix(size = size, modules = flat)
    }

    private fun encodeByteModeData(payload: ByteArray, totalDataBytes: Int, version: Int): IntArray {
        val bits = ArrayList<Int>(totalDataBytes * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Mode indicator Byte = 0100
        appendBits(0b0100, 4)
        // Character count indicator: 8 bit per V1..V9, 16 bit per V10..V26 (ISO/IEC 18004)
        val countBits = if (version >= 10) 16 else 8
        appendBits(payload.size, countBits)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val maxBits = totalDataBytes * 8
        val terminatorCount = minOf(4, maxBits - bits.size)
        repeat(terminatorCount) { bits.add(0) }
        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        val codewords = IntArray(totalDataBytes)
        var idx = 0
        var bitIdx = 0
        while (bitIdx + 8 <= bits.size && idx < totalDataBytes) {
            var byteVal = 0
            for (k in 0 until 8) {
                byteVal = (byteVal shl 1) or bits[bitIdx + k]
            }
            codewords[idx++] = byteVal
            bitIdx += 8
        }
        var padToggle = true
        while (idx < totalDataBytes) {
            codewords[idx++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }
        return codewords
    }

    private fun buildInterleavedCodewords(data: IntArray, spec: VersionSpec): IntArray {
        val dataBlocks = ArrayList<IntArray>()
        val ecBlocks = ArrayList<IntArray>()
        var offset = 0
        repeat(spec.group1Blocks) {
            val block = data.copyOfRange(offset, offset + spec.group1DataBytes)
            offset += spec.group1DataBytes
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecBytesPerBlock))
        }
        repeat(spec.group2Blocks) {
            val block = data.copyOfRange(offset, offset + spec.group2DataBytes)
            offset += spec.group2DataBytes
            dataBlocks.add(block)
            ecBlocks.add(computeReedSolomon(block, spec.ecBytesPerBlock))
        }

        val totalData = dataBlocks.sumOf { it.size }
        val totalEc = ecBlocks.sumOf { it.size }
        val result = IntArray(totalData + totalEc)
        var pos = 0

        val maxDataLen = dataBlocks.maxOf { it.size }
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) {
                    result[pos++] = block[i]
                }
            }
        }
        for (i in 0 until spec.ecBytesPerBlock) {
            for (ec in ecBlocks) {
                if (i < ec.size) {
                    result[pos++] = ec[i]
                }
            }
        }
        return result
    }

    private fun computeReedSolomon(data: IntArray, ecCount: Int): IntArray {
        val gen = rsGeneratorPoly(ecCount)
        val rem = IntArray(ecCount)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecCount - 1)
            rem[ecCount - 1] = 0
            if (factor != 0) {
                for (j in 0 until ecCount) {
                    rem[j] = rem[j] xor gfMul(gen[j], factor)
                }
            }
        }
        return rem
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = IntArray(degree)
        poly[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                poly[j] = gfMul(poly[j], root)
                if (j + 1 < degree) {
                    poly[j] = poly[j] xor poly[j + 1]
                }
            }
            root = gfMul(root, 2)
        }
        return poly
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun placeFinderPattern(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, row: Int, col: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isDark = r == 0 || r == 6 || c == 0 || c == 6 || (r in 2..4 && c in 2..4)
                modules[row + r][col + c] = isDark
                isFunc[row + r][col + c] = true
            }
        }
    }

    private fun placeSeparators(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, size: Int) {
        for (i in 0 until 8) {
            // Top-left
            markFunc(modules, isFunc, 7, i, false)
            markFunc(modules, isFunc, i, 7, false)
            // Top-right
            markFunc(modules, isFunc, 7, size - 8 + i, false)
            markFunc(modules, isFunc, i, size - 8, false)
            // Bottom-left
            markFunc(modules, isFunc, size - 8, i, false)
            markFunc(modules, isFunc, size - 8 + i, 7, false)
        }
    }

    private fun placeAlignmentPatterns(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, centers: IntArray) {
        if (centers.isEmpty()) return
        for (rCenter in centers) {
            for (cCenter in centers) {
                if (isFunc[rCenter][cCenter]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val isDark = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc)) != 1
                        modules[rCenter + dr][cCenter + dc] = isDark
                        isFunc[rCenter + dr][cCenter + dc] = true
                    }
                }
            }
        }
    }

    private fun placeTimingPatterns(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, size: Int) {
        for (i in 8 until size - 8) {
            val isDark = i % 2 == 0
            if (!isFunc[6][i]) {
                modules[6][i] = isDark
                isFunc[6][i] = true
            }
            if (!isFunc[i][6]) {
                modules[i][6] = isDark
                isFunc[i][6] = true
            }
        }
    }

    private fun reserveFormatAreas(isFunc: Array<BooleanArray>, size: Int) {
        for (i in 0..8) {
            if (i != 6) {
                isFunc[8][i] = true
                isFunc[i][8] = true
            }
        }
        for (i in 0 until 8) {
            isFunc[8][size - 1 - i] = true
            isFunc[size - 1 - i][8] = true
        }
    }

    private fun placeVersionInfo(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, size: Int, version: Int) {
        var rem = version
        repeat(12) {
            rem = (rem shl 1) xor (((rem ushr 11) and 1) * 0x1F25)
        }
        val bits = (version shl 12) or rem
        for (i in 0 until 18) {
            val bit = ((bits ushr i) and 1) == 1
            val a = size - 11 + (i % 3)
            val b = i / 3
            modules[a][b] = bit
            isFunc[a][b] = true
            modules[b][a] = bit
            isFunc[b][a] = true
        }
    }

    private fun placeFormatInfo(modules: Array<BooleanArray>, size: Int, eccLevelBits: Int, maskPattern: Int) {
        val data = (eccLevelBits shl 3) or maskPattern
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor (((rem ushr 9) and 1) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412

        // Prima copia attorno al finder pattern in alto a sinistra:
        // bit 0..5 verticali (col 8, righe 0..5), bit 6 (7,8), bit 7 (8,8), bit 8 (8,7), bit 9..14 orizzontali (riga 8, col 5..0)
        for (i in 0..5) modules[i][8] = ((bits ushr i) and 1) == 1
        modules[7][8] = ((bits ushr 6) and 1) == 1
        modules[8][8] = ((bits ushr 7) and 1) == 1
        modules[8][7] = ((bits ushr 8) and 1) == 1
        for (i in 9..14) modules[8][14 - i] = ((bits ushr i) and 1) == 1

        // Seconda copia:
        // bit 0..7 orizzontali sotto al finder in alto a destra (riga 8, col size-1..size-8)
        // bit 8..14 verticali accanto al finder in basso a sinistra (righe size-7..size-1, col 8)
        // Preserva intatto il dark module fisso (4 * V + 9, 8) = (size - 8, 8).
        for (i in 0..7) modules[8][size - 1 - i] = ((bits ushr i) and 1) == 1
        for (i in 8..14) modules[size - 15 + i][8] = ((bits ushr i) and 1) == 1
    }

    private fun placeDataBits(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, size: Int, codewords: IntArray) {
        var bitIndex = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val col = right - j
                    val upward = ((right + 1) and 2) == 0
                    val row = if (upward) size - 1 - vert else vert
                    if (!isFunc[row][col] && bitIndex < totalBits) {
                        val byteVal = codewords[bitIndex ushr 3]
                        val bit = ((byteVal ushr (7 - (bitIndex and 7))) and 1) == 1
                        modules[row][col] = bit
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, size: Int, mask: Int) {
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isFunc[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> ((r / 2) + (c / 3)) % 2 == 0
                    5 -> (r * c) % 2 + (r * c) % 3 == 0
                    6 -> ((r * c) % 2 + (r * c) % 3) % 2 == 0
                    7 -> ((r + c) % 2 + (r * c) % 3) % 2 == 0
                    else -> false
                }
                if (invert) modules[r][c] = !modules[r][c]
            }
        }
    }

    private fun calculatePenalty(modules: Array<BooleanArray>, size: Int): Int {
        var penalty = 0
        // Regola 1: run >= 5 orizzontali e verticali
        for (r in 0 until size) {
            var runColor = modules[r][0]
            var runLen = 1
            for (c in 1 until size) {
                if (modules[r][c] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        for (c in 0 until size) {
            var runColor = modules[0][c]
            var runLen = 1
            for (r in 1 until size) {
                if (modules[r][c] == runColor) {
                    runLen++
                } else {
                    if (runLen >= 5) penalty += 3 + (runLen - 5)
                    runColor = modules[r][c]
                    runLen = 1
                }
            }
            if (runLen >= 5) penalty += 3 + (runLen - 5)
        }
        // Regola 2: blocchi 2x2 dello stesso colore
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val color = modules[r][c]
                if (color == modules[r][c + 1] && color == modules[r + 1][c] && color == modules[r + 1][c + 1]) {
                    penalty += 3
                }
            }
        }
        // Regola 3: pattern 1:1:3:1:1 con 4 moduli chiari adiacenti (orizzontali e verticali)
        for (r in 0 until size) {
            for (c in 0..size - 11) {
                if (matchesFinderPenalty(modules, r, c, horizontal = true)) penalty += 40
            }
        }
        for (c in 0 until size) {
            for (r in 0..size - 11) {
                if (matchesFinderPenalty(modules, r, c, horizontal = false)) penalty += 40
            }
        }
        // Regola 4: bilanciamento moduli scuri ~50%
        var darkCount = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (modules[r][c]) darkCount++
            }
        }
        val total = size * size
        val k = kotlin.math.abs(darkCount * 20 - total * 10) / total
        penalty += k * 10
        return penalty
    }

    private fun matchesFinderPenalty(
        modules: Array<BooleanArray>,
        startRow: Int,
        startCol: Int,
        horizontal: Boolean
    ): Boolean {
        fun get(offset: Int): Boolean =
            if (horizontal) modules[startRow][startCol + offset] else modules[startRow + offset][startCol]

        val patternA = get(0) && !get(1) && get(2) && get(3) && get(4) && !get(5) && get(6) &&
            !get(7) && !get(8) && !get(9) && !get(10)
        val patternB = !get(0) && !get(1) && !get(2) && !get(3) && get(4) && !get(5) && get(6) &&
            get(7) && get(8) && !get(9) && get(10)
        return patternA || patternB
    }

    private fun markFunc(modules: Array<BooleanArray>, isFunc: Array<BooleanArray>, r: Int, c: Int, value: Boolean) {
        modules[r][c] = value
        isFunc[r][c] = true
    }
}
