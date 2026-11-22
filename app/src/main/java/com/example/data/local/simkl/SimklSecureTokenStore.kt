package com.example.data.local.simkl

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class SimklTokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMs: Long?,
    val tokenType: String = "Bearer",
    val scope: String? = null,
    val savedAtMs: Long = System.currentTimeMillis()
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAtMs ?: return false
        return nowMs >= exp
    }

    fun isExpiringSoon(leadTimeMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
        val exp = expiresAtMs ?: return false
        return nowMs + leadTimeMs >= exp
    }
}

interface SimklCipherEngine {
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray
    fun decrypt(alias: String, encrypted: ByteArray): ByteArray
}

class AndroidKeystoreSimklCipherEngine : SimklCipherEngine {
    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateKey(alias)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(1 + 1 + iv.size + ciphertext.size).apply {
            this[0] = FORMAT_VERSION
            this[1] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 2, iv.size)
            System.arraycopy(ciphertext, 0, this, 2 + iv.size, ciphertext.size)
        }
    }

    override fun decrypt(alias: String, encrypted: ByteArray): ByteArray {
        require(encrypted.size > 16) { "Payload cifrato non valido" }
        require(encrypted[0] == FORMAT_VERSION) { "Versione cifratura non supportata" }
        val ivLen = encrypted[1].toInt() and 0xFF
        require(ivLen in 12..16 && encrypted.size > 2 + ivLen) { "IV AES-GCM non valido" }
        val iv = encrypted.copyOfRange(2, 2 + ivLen)
        val ciphertext = encrypted.copyOfRange(2 + ivLen, encrypted.size)
        val secretKey = getExistingKey(alias)
            ?: throw IllegalStateException("Chiave Android Keystore non disponibile")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun getExistingKey(alias: String): SecretKey? {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey(alias, null) as? SecretKey
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        getExistingKey(alias)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val FORMAT_VERSION: Byte = 1
    }
}

/**
 * Motore AES-256-GCM su chiave in-memory (fallback sicuro o test Robolectric quando
 * AndroidKeyStore non è disponibile nella JVM locale).
 */
class SoftwareAesGcmSimklCipherEngine : SimklCipherEngine {
    private val key: SecretKey by lazy {
        val gen = KeyGenerator.getInstance("AES")
        gen.init(256)
        gen.generateKey()
    }

    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(2 + iv.size + ciphertext.size).apply {
            this[0] = 1
            this[1] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 2, iv.size)
            System.arraycopy(ciphertext, 0, this, 2 + iv.size, ciphertext.size)
        }
    }

    override fun decrypt(alias: String, encrypted: ByteArray): ByteArray {
        val ivLen = encrypted[1].toInt() and 0xFF
        val iv = encrypted.copyOfRange(2, 2 + ivLen)
        val ciphertext = encrypted.copyOfRange(2 + ivLen, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}

/**
 * Archivio sicuro dei token OAuth2 Simkl per singolo profilo utente (`profileId`).
 * I token non vengono mai scritti in chiaro su disco, né salvati su Room o DataStore.
 */
class SimklSecureTokenStore(
    private val vaultDir: File,
    private val cipherEngine: SimklCipherEngine = createDefaultCipherEngine()
) {
    constructor(
        context: Context,
        cipherEngine: SimklCipherEngine = createDefaultCipherEngine()
    ) : this(
        vaultDir = File(context.noBackupFilesDir ?: context.filesDir, "simkl_vault"),
        cipherEngine = cipherEngine
    )

    private val mutex = Mutex()

    suspend fun saveTokens(profileId: String, bundle: SimklTokenBundle) = mutex.withLock {
        val cleanProfileId = profileId.trim()
        require(cleanProfileId.isNotEmpty()) { "profileId non valido" }
        require(bundle.accessToken.isNotBlank()) { "accessToken non può essere vuoto" }
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val json = JSONObject().apply {
            put("access_token", bundle.accessToken)
            put("refresh_token", bundle.refreshToken ?: JSONObject.NULL)
            put("expires_at_ms", bundle.expiresAtMs ?: JSONObject.NULL)
            put("token_type", bundle.tokenType)
            put("scope", bundle.scope ?: JSONObject.NULL)
            put("saved_at_ms", bundle.savedAtMs)
        }.toString().toByteArray(Charsets.UTF_8)

        val encrypted = cipherEngine.encrypt(keyAliasFor(cleanProfileId), json)
        val targetFile = vaultFileFor(cleanProfileId)
        val tmpFile = File(vaultDir, "${targetFile.name}.tmp")
        tmpFile.writeBytes(encrypted)
        if (!tmpFile.renameTo(targetFile)) {
            targetFile.writeBytes(encrypted)
            tmpFile.delete()
        }
    }

    suspend fun readTokens(profileId: String): SimklTokenBundle? = mutex.withLock {
        val cleanProfileId = profileId.trim()
        if (cleanProfileId.isEmpty()) return@withLock null
        val file = vaultFileFor(cleanProfileId)
        if (!file.exists()) return@withLock null
        return@withLock runCatching {
            val encrypted = file.readBytes()
            val plainBytes = cipherEngine.decrypt(keyAliasFor(cleanProfileId), encrypted)
            val json = JSONObject(String(plainBytes, Charsets.UTF_8))
            val accessToken = json.optString("access_token", "").trim()
            if (accessToken.isEmpty()) return@runCatching null
            val refreshToken = if (json.isNull("refresh_token")) null else json.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresAtMs = if (json.isNull("expires_at_ms")) null else json.optLong("expires_at_ms").takeIf { it > 0L }
            val tokenType = json.optString("token_type", "Bearer").ifBlank { "Bearer" }
            val scope = if (json.isNull("scope")) null else json.optString("scope").takeIf { it.isNotBlank() }
            val savedAtMs = json.optLong("saved_at_ms", 0L)
            SimklTokenBundle(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMs = expiresAtMs,
                tokenType = tokenType,
                scope = scope,
                savedAtMs = savedAtMs
            )
        }.getOrNull()
    }

    suspend fun clearTokens(profileId: String) = mutex.withLock {
        val cleanProfileId = profileId.trim()
        if (cleanProfileId.isEmpty()) return@withLock
        vaultFileFor(cleanProfileId).delete()
    }

    suspend fun hasTokens(profileId: String): Boolean = readTokens(profileId) != null

    fun inspectRawVaultFile(profileId: String): File = vaultFileFor(profileId.trim())

    private fun vaultFileFor(profileId: String): File {
        val hash = sha256Hex(profileId).take(32)
        return File(vaultDir, "vix_simkl_$hash.bin")
    }

    private fun keyAliasFor(profileId: String): String {
        val hash = sha256Hex(profileId).take(24)
        return "vixflix_simkl_aes_$hash"
    }

    companion object {
        private fun createDefaultCipherEngine(): SimklCipherEngine {
            return runCatching {
                val ks = KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                AndroidKeystoreSimklCipherEngine()
            }.getOrElse {
                SoftwareAesGcmSimklCipherEngine()
            }
        }

        private fun sha256Hex(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
