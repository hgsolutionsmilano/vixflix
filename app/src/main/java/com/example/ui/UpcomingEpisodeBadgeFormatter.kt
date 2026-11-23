package com.example.ui

import com.example.data.local.WatchProgressEntity
import com.example.data.remote.TmdbEpisodeDto
import java.util.Calendar
import java.util.Locale

/**
 * Rappresentazione pura di una data di calendario (YYYY-MM-DD) compatibile con minSdk 24
 * senza dipendere da `java.time.LocalDate` (API 26+).
 */
data class CalendarDate(
    val year: Int,
    val month: Int,
    val day: Int
) : Comparable<CalendarDate> {

    override fun compareTo(other: CalendarDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    companion object {
        fun today(): CalendarDate {
            val cal = Calendar.getInstance()
            return CalendarDate(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH) + 1,
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}

/**
 * Formattatore e risolutore per il badge della data di uscita di un episodio futuro
 * nella sezione "Continua a guardare".
 *
 * Regole:
 * - Mostra il badge SOLO per serie TV (`mediaType == "tv"`), NON per film o serie completate.
 * - Usa ESCLUSIVAMENTE la data reale dell'episodio specifico (`TmdbEpisodeDto.airDate`)
 *   corrispondente al prossimo episodio (`seasonNumber`, `episodeNumber`).
 * - Mostra il badge SOLO se `airDate > today` (episodio non ancora trasmesso).
 * - Se l'episodio è già uscito (`airDate <= today`), la data manca o è invalida → `null`.
 * - Formato in italiano maiuscolo comprensivo di anno:
 *   - `GG MMM YYYY` (es. `26 NOV 2026`, `08 GEN 2027`)
 */
object UpcomingEpisodeBadgeFormatter {

    private val ITALIAN_MONTHS_SHORT = arrayOf(
        "GEN", "FEB", "MAR", "APR", "MAG", "GIU",
        "LUG", "AGO", "SET", "OTT", "NOV", "DIC"
    )

    private val ISO_DATE_REGEX = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    fun parseIsoAirDate(rawAirDate: String?): CalendarDate? {
        val cleaned = rawAirDate?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        val match = ISO_DATE_REGEX.matchEntire(cleaned) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        if (year < 1900 || year > 2999) return null
        if (month !in 1..12) return null
        val maxDay = daysInMonth(year, month)
        if (day !in 1..maxDay) return null
        return CalendarDate(year = year, month = month, day = day)
    }

    private fun daysInMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        else -> 0
    }

    fun isUpcomingAirDate(
        rawAirDate: String?,
        today: CalendarDate = CalendarDate.today()
    ): Boolean {
        val parsed = parseIsoAirDate(rawAirDate) ?: return false
        return parsed > today
    }

    /**
     * Formatta una data ISO `YYYY-MM-DD` se e solo se è strettamente futura rispetto a [today].
     * Ritorna `null` se la data è assente, malformata, passata o uguale a [today].
     * Include sempre l'anno esteso (`GG MMM YYYY`, es. `26 NOV 2026`).
     */
    fun formatUpcomingEpisodeAirDate(
        rawAirDate: String?,
        today: CalendarDate = CalendarDate.today()
    ): String? {
        val parsed = parseIsoAirDate(rawAirDate) ?: return null
        if (parsed <= today) return null
        val dayFormatted = String.format(Locale.US, "%02d", parsed.day)
        val monthLabel = ITALIAN_MONTHS_SHORT[parsed.month - 1]
        return "$dayFormatted $monthLabel ${parsed.year}"
    }

    /**
     * Risolve il testo del badge futuro per una voce di "Continua a guardare" usando
     * esclusivamente gli episodi reali della stagione TMDB corrispondente.
     */
    fun resolveUpcomingEpisodeBadgeForEntry(
        entry: WatchProgressEntity,
        seasonEpisodes: List<TmdbEpisodeDto>,
        isSeriesCompleted: Boolean,
        today: CalendarDate = CalendarDate.today()
    ): String? {
        if (isSeriesCompleted) return null
        if (!entry.mediaType.equals("tv", ignoreCase = true)) return null
        val seasonNum = entry.seasonNumber ?: return null
        val episodeNum = entry.episodeNumber ?: return null
        if (seasonNum <= 0 || episodeNum <= 0) return null

        val matchingEpisode = seasonEpisodes.firstOrNull { ep ->
            ep.episodeNumber == episodeNum &&
                (ep.seasonNumber <= 0 || ep.seasonNumber == seasonNum)
        } ?: return null

        return formatUpcomingEpisodeAirDate(matchingEpisode.airDate, today)
    }
}
