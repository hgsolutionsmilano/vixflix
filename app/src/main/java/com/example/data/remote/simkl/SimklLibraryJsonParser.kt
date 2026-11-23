package com.example.data.remote.simkl

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Parser resiliente per le risposte `/sync/all-items`, `/sync/all-items/{type}` e
 * `/sync/all-items/{type}/{status}` delle API Simkl.
 *
 * Gestisce in sicurezza:
 * - risposte vuote / `{}` / `[]` su delta incrementali senza modifiche
 * - campi numerici codificati sia come numeri che come stringhe (es. `"tmdb": "1399"`)
 * - anime annidati sotto `"show"` oppure `"anime"`
 * - `seasons` ed `episodes` rappresentati sia come JSONArray che come JSONObject
 * - isolamento per singolo elemento: un record malformato viene tracciato nei diagnostici
 *   senza mai bloccare l'importazione degli altri elementi della libreria.
 */
object SimklLibraryJsonParser {

    data class ParseResult(
        val shows: List<SimklLibraryParsedItem> = emptyList(),
        val movies: List<SimklLibraryParsedItem> = emptyList(),
        val anime: List<SimklLibraryParsedItem> = emptyList(),
        val errors: List<SimklSyncErrorEntry> = emptyList()
    )

    fun parseAllItemsPayload(
        rawJson: String?,
        defaultCategory: String? = null,
        forcedStatus: String? = null
    ): ParseResult {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "null" || trimmed == "{}" || trimmed == "[]") {
            return ParseResult()
        }

        val shows = ArrayList<SimklLibraryParsedItem>()
        val movies = ArrayList<SimklLibraryParsedItem>()
        val anime = ArrayList<SimklLibraryParsedItem>()
        val errors = ArrayList<SimklSyncErrorEntry>()

        val parsedRoot = try {
            JSONTokener(trimmed).nextValue()
        } catch (e: Exception) {
            errors.add(
                SimklSyncErrorEntry(
                    source = defaultCategory ?: "all-items",
                    errorType = "JSON_PARSE",
                    message = "Risposta JSON Simkl non valida (${e.javaClass.simpleName})."
                )
            )
            return ParseResult(errors = errors)
        }

        when (parsedRoot) {
            is JSONObject -> {
                val showsArray = parsedRoot.optJSONArray("shows")
                    ?: parsedRoot.optJSONArray("tv_shows")
                val moviesArray = parsedRoot.optJSONArray("movies")
                val animeArray = parsedRoot.optJSONArray("anime")

                showsArray?.let {
                    parseCategoryArray(it, CATEGORY_SHOWS, forcedStatus, shows, errors)
                }
                moviesArray?.let {
                    parseCategoryArray(it, CATEGORY_MOVIES, forcedStatus, movies, errors)
                }
                animeArray?.let {
                    parseCategoryArray(it, CATEGORY_ANIME, forcedStatus, anime, errors)
                }
            }
            is JSONArray -> {
                val category = defaultCategory ?: CATEGORY_SHOWS
                val target = when (category) {
                    CATEGORY_MOVIES -> movies
                    CATEGORY_ANIME -> anime
                    else -> shows
                }
                parseCategoryArray(parsedRoot, category, forcedStatus, target, errors)
            }
            else -> {
                errors.add(
                    SimklSyncErrorEntry(
                        source = defaultCategory ?: "all-items",
                        errorType = "JSON_SHAPE",
                        message = "Formato radice JSON inatteso da Simkl."
                    )
                )
            }
        }

        return ParseResult(
            shows = shows,
            movies = movies,
            anime = anime,
            errors = errors
        )
    }

    /**
     * Estrae le `stableKey` presenti in una risposta `/sync/all-items/{type}?extended=simkl_ids_only`
     * (oppure payload completo di fallback) per confrontarle con la libreria locale in caso di rimozioni.
     * Restituisce `null` se la risposta non è interpretabile come lista valida (così da evitare cancellazioni errate).
     */
    fun extractKeysForRemoval(
        rawJson: String?,
        category: String
    ): Set<String>? {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed == "null") return null
        if (trimmed == "{}" || trimmed == "[]") return emptySet()

        val parsedRoot = runCatching { JSONTokener(trimmed).nextValue() }.getOrNull() ?: return null
        val keys = LinkedHashSet<String>()

        fun processArray(array: JSONArray) {
            for (i in 0 until array.length()) {
                val el = array.opt(i) ?: continue
                when (el) {
                    is Number -> {
                        val id = el.toLong()
                        if (id > 0L) keys.add(buildStableKey(category, id, null, null, null, null))
                    }
                    is String -> {
                        val id = el.trim().toLongOrNull()
                        if (id != null && id > 0L) keys.add(buildStableKey(category, id, null, null, null, null))
                    }
                    is JSONObject -> {
                        val meta = extractMetaObject(el, category)
                        val ids = meta.optJSONObject("ids") ?: el.optJSONObject("ids")
                        val simklId = parseFlexibleLong(ids, "simkl")
                            ?: parseFlexibleLong(ids, "simkl_id")
                            ?: parseFlexibleLong(el, "simkl_id")
                            ?: parseFlexibleLong(el, "simkl")
                            ?: parseFlexibleLong(el, "id")
                            ?: 0L
                        val tmdbId = parseFlexibleInt(ids, "tmdb")
                        val tvdbId = parseFlexibleInt(ids, "tvdb")
                        val imdbId = optNonBlankString(ids, "imdb")
                        val slug = optNonBlankString(ids, "slug")
                        buildStableKeyOrNull(category, simklId, tmdbId, tvdbId, imdbId, slug)?.let {
                            keys.add(it)
                        }
                    }
                }
            }
        }

        when (parsedRoot) {
            is JSONArray -> processArray(parsedRoot)
            is JSONObject -> {
                val arr = when (category) {
                    CATEGORY_MOVIES -> parsedRoot.optJSONArray("movies")
                    CATEGORY_ANIME -> parsedRoot.optJSONArray("anime")
                    else -> parsedRoot.optJSONArray("shows") ?: parsedRoot.optJSONArray("tv_shows")
                } ?: return if (parsedRoot.length() == 0) emptySet() else null
                processArray(arr)
            }
            else -> return null
        }

        return keys
    }

    fun serializeSeasonsJson(seasons: List<SimklParsedSeason>): String? {
        if (seasons.isEmpty()) return null
        val root = JSONArray()
        for (season in seasons) {
            val sObj = JSONObject()
            sObj.put("season", season.seasonNumber)
            val epsArr = JSONArray()
            for (ep in season.episodes) {
                val eObj = JSONObject()
                eObj.put("number", ep.episodeNumber)
                eObj.put("watched", ep.watched)
                if (!ep.watchedAt.isNullOrBlank()) {
                    eObj.put("watched_at", ep.watchedAt)
                }
                if (ep.simklEpisodeId != null && ep.simklEpisodeId > 0L) {
                    eObj.put("simkl_id", ep.simklEpisodeId)
                }
                epsArr.put(eObj)
            }
            sObj.put("episodes", epsArr)
            root.put(sObj)
        }
        return root.toString()
    }

    fun deserializeSeasonsJson(rawJson: String?): List<SimklParsedSeason> {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        return parseSeasons(arr)
    }

    fun serializeBatchEpisodes(episodes: List<Pair<Int, Int>>): String? {
        if (episodes.isEmpty()) return null
        val arr = JSONArray()
        for ((s, e) in episodes.distinct()) {
            val obj = JSONObject()
            obj.put("season", s)
            obj.put("episode", e)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun deserializeBatchEpisodes(rawJson: String?): List<Pair<Int, Int>> {
        val trimmed = rawJson?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        val arr = runCatching { JSONArray(trimmed) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Pair<Int, Int>>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val s = parseFlexibleInt(obj, "season", allowZero = true) ?: continue
            val e = parseFlexibleInt(obj, "episode", allowZero = s == 0)
                ?: parseFlexibleInt(obj, "number", allowZero = s == 0)
                ?: continue
            out.add(s to e)
        }
        return out
    }

    private fun parseCategoryArray(
        array: JSONArray,
        category: String,
        forcedStatus: String?,
        out: MutableList<SimklLibraryParsedItem>,
        errors: MutableList<SimklSyncErrorEntry>
    ) {
        for (i in 0 until array.length()) {
            val itemObj = array.optJSONObject(i)
            if (itemObj == null) {
                errors.add(
                    SimklSyncErrorEntry(
                        source = category,
                        errorType = "ITEM_NOT_OBJECT",
                        message = "Elemento #$i in $category non è un oggetto JSON valido."
                    )
                )
                continue
            }
            try {
                val parsed = parseSingleItem(itemObj, category, forcedStatus)
                if (parsed != null) {
                    out.add(parsed)
                } else {
                    errors.add(
                        SimklSyncErrorEntry(
                            source = category,
                            errorType = "ITEM_MISSING_ID",
                            message = "Elemento #$i in $category privo di identificativo stabile."
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add(
                    SimklSyncErrorEntry(
                        source = category,
                        errorType = "ITEM_PARSE_ERROR",
                        message = "Errore parsing elemento #$i in $category (${e.javaClass.simpleName})."
                    )
                )
            }
        }
    }

    private fun parseSingleItem(
        itemObj: JSONObject,
        category: String,
        forcedStatus: String?
    ): SimklLibraryParsedItem? {
        val metaObj = extractMetaObject(itemObj, category)
        val idsObj = metaObj.optJSONObject("ids") ?: itemObj.optJSONObject("ids")

        val simklId = parseFlexibleLong(idsObj, "simkl")
            ?: parseFlexibleLong(idsObj, "simkl_id")
            ?: parseFlexibleLong(itemObj, "simkl_id")
            ?: 0L
        val tmdbId = parseFlexibleInt(idsObj, "tmdb")
        val tvdbId = parseFlexibleInt(idsObj, "tvdb")
        val imdbId = optNonBlankString(idsObj, "imdb")
        val slug = optNonBlankString(idsObj, "slug")

        val stableKey = buildStableKeyOrNull(category, simklId, tmdbId, tvdbId, imdbId, slug)
            ?: return null

        val rawTitle = optNonBlankString(metaObj, "title")
            ?: optNonBlankString(metaObj, "name")
            ?: optNonBlankString(itemObj, "title")
            ?: optNonBlankString(idsObj, "slug")
            ?: "Simkl #$simklId"

        val year = parseFlexibleInt(metaObj, "year") ?: parseFlexibleInt(itemObj, "year")
        val explicitWatchedAt = optNonBlankString(itemObj, "last_watched_at")
            ?: optNonBlankString(itemObj, "watched_at")
        val addedToWatchlistAt = optNonBlankString(itemObj, "added_to_watchlist_at")
        val lastWatchedAt = explicitWatchedAt ?: addedToWatchlistAt

        val rawStatusField = forcedStatus ?: optNonBlankString(itemObj, "status")
        val normalizedStatus = normalizeStatus(
            rawStatus = rawStatusField ?: if (explicitWatchedAt.isNullOrBlank() && !addedToWatchlistAt.isNullOrBlank()) {
                STATUS_PLAN_TO_WATCH
            } else {
                null
            },
            category = category,
            hasWatchedAt = !explicitWatchedAt.isNullOrBlank()
        )

        val tmdbMediaType = if (category == CATEGORY_MOVIES) "movie" else "tv"
        val hasSeasonsField = category != CATEGORY_MOVIES && itemObj.has("seasons") && !itemObj.isNull("seasons")
        val parsedSeasons = if (hasSeasonsField) {
            parseSeasons(itemObj.opt("seasons"))
        } else {
            emptyList()
        }

        val countedWatchedFromSeasons = parsedSeasons.sumOf { s -> s.episodes.count { it.watched } }
        val explicitWatchedCount = parseFlexibleInt(itemObj, "watched_episodes_count", allowZero = true)
        val explicitTotalCount = parseFlexibleInt(itemObj, "total_episodes_count", allowZero = true)
        val countedTotalFromSeasons = parsedSeasons.sumOf { it.episodes.size }

        val watchedCount = if (hasSeasonsField && explicitWatchedCount != null) {
            maxOf(explicitWatchedCount, countedWatchedFromSeasons)
        } else if (hasSeasonsField) {
            countedWatchedFromSeasons
        } else {
            explicitWatchedCount ?: 0
        }
        val totalCount = maxOf(explicitTotalCount ?: 0, countedTotalFromSeasons, watchedCount)

        val nextToWatchInfoObj = itemObj.optJSONObject("next_to_watch_info")
        val nextToWatchSeason = parseFlexibleInt(nextToWatchInfoObj, "season", allowZero = true)
        val nextToWatchEpisode = parseFlexibleInt(
            nextToWatchInfoObj,
            "episode",
            allowZero = nextToWatchSeason == 0
        ) ?: parseFlexibleInt(nextToWatchInfoObj, "number", allowZero = nextToWatchSeason == 0)
        val nextToWatch = optNonBlankString(itemObj, "next_to_watch")
            ?: optNonBlankString(nextToWatchInfoObj, "code")
            ?: if (nextToWatchSeason != null && nextToWatchEpisode != null) {
                "S${nextToWatchSeason.toString().padStart(2, '0')}E${nextToWatchEpisode.toString().padStart(2, '0')}"
            } else {
                null
            }
        val lastWatchedCode = optNonBlankString(itemObj, "last_watched")

        return SimklLibraryParsedItem(
            stableKey = stableKey,
            simklId = simklId,
            mediaCategory = category,
            tmdbMediaType = tmdbMediaType,
            tmdbId = tmdbId,
            imdbId = imdbId,
            tvdbId = tvdbId,
            slug = slug,
            title = rawTitle,
            year = year,
            status = normalizedStatus,
            watchedEpisodesCount = watchedCount,
            totalEpisodesCount = totalCount,
            lastWatchedAt = lastWatchedAt,
            nextToWatch = nextToWatch,
            lastWatchedEpisodeCode = lastWatchedCode,
            seasons = parsedSeasons,
            nextToWatchSeason = nextToWatchSeason,
            nextToWatchEpisode = nextToWatchEpisode,
            hasSeasonsField = hasSeasonsField,
            hasExplicitWatchedCount = explicitWatchedCount != null
        )
    }

    private fun extractMetaObject(itemObj: JSONObject, category: String): JSONObject {
        return when (category) {
            CATEGORY_MOVIES -> itemObj.optJSONObject("movie") ?: itemObj
            CATEGORY_ANIME -> itemObj.optJSONObject("show")
                ?: itemObj.optJSONObject("anime")
                ?: itemObj
            else -> itemObj.optJSONObject("show") ?: itemObj
        }
    }

    private fun parseSeasons(rawSeasons: Any?): List<SimklParsedSeason> {
        if (rawSeasons == null || rawSeasons === JSONObject.NULL) return emptyList()
        val result = ArrayList<SimklParsedSeason>()

        when (rawSeasons) {
            is JSONArray -> {
                for (i in 0 until rawSeasons.length()) {
                    val sObj = rawSeasons.optJSONObject(i) ?: continue
                    val seasonNum = parseFlexibleInt(sObj, "number", allowZero = true)
                        ?: parseFlexibleInt(sObj, "season", allowZero = true)
                        ?: continue
                    val eps = parseEpisodes(sObj.opt("episodes"), seasonNum)
                    if (eps.isNotEmpty()) {
                        result.add(SimklParsedSeason(seasonNumber = seasonNum, episodes = eps))
                    }
                }
            }
            is JSONObject -> {
                val keys = rawSeasons.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val sObj = rawSeasons.optJSONObject(key) ?: continue
                    val seasonNum = key.trim().toIntOrNull()
                        ?: parseFlexibleInt(sObj, "number", allowZero = true)
                        ?: parseFlexibleInt(sObj, "season", allowZero = true)
                        ?: continue
                    val eps = parseEpisodes(sObj.opt("episodes"), seasonNum)
                    if (eps.isNotEmpty()) {
                        result.add(SimklParsedSeason(seasonNumber = seasonNum, episodes = eps))
                    }
                }
            }
        }
        return result.sortedBy { it.seasonNumber }
    }

    private fun parseEpisodes(rawEpisodes: Any?, seasonNum: Int): List<SimklParsedEpisode> {
        if (rawEpisodes == null || rawEpisodes === JSONObject.NULL) return emptyList()
        val result = ArrayList<SimklParsedEpisode>()

        fun parseSingleEp(epObj: JSONObject, fallbackEpNumber: Int?): SimklParsedEpisode? {
            val allowZeroEp = seasonNum == 0
            val epNum = parseFlexibleInt(epObj, "number", allowZero = allowZeroEp)
                ?: parseFlexibleInt(epObj, "episode", allowZero = allowZeroEp)
                ?: fallbackEpNumber?.takeIf { if (allowZeroEp) it >= 0 else it > 0 }
                ?: return null
            val watchedAt = optNonBlankString(epObj, "watched_at")
            val epIdsObj = epObj.optJSONObject("ids")
            val simklEpId = parseFlexibleLong(epIdsObj, "simkl")
                ?: parseFlexibleLong(epIdsObj, "simkl_id")
                ?: parseFlexibleLong(epObj, "simkl_id")
                ?: parseFlexibleLong(epObj, "id")
            val isWatched = when {
                epObj.has("watched") -> {
                    val raw = epObj.opt("watched")
                    when (raw) {
                        is Boolean -> raw
                        is Number -> raw.toInt() != 0
                        is String -> raw.equals("true", ignoreCase = true) || raw == "1"
                        else -> !watchedAt.isNullOrBlank()
                    }
                }
                epObj.has("watched_at") -> !watchedAt.isNullOrBlank()
                else -> true
            }
            return SimklParsedEpisode(
                seasonNumber = seasonNum,
                episodeNumber = epNum,
                watched = isWatched,
                watchedAt = watchedAt,
                simklEpisodeId = simklEpId
            )
        }

        when (rawEpisodes) {
            is JSONArray -> {
                for (i in 0 until rawEpisodes.length()) {
                    val epObj = rawEpisodes.optJSONObject(i) ?: continue
                    parseSingleEp(epObj, null)?.let { result.add(it) }
                }
            }
            is JSONObject -> {
                val keys = rawEpisodes.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val epObj = rawEpisodes.optJSONObject(key) ?: continue
                    parseSingleEp(epObj, key.trim().toIntOrNull())?.let { result.add(it) }
                }
            }
        }

        return result.sortedBy { it.episodeNumber }
    }

    private fun normalizeStatus(
        rawStatus: String?,
        category: String,
        hasWatchedAt: Boolean
    ): String {
        return when (rawStatus?.trim()?.lowercase()?.replace("-", "")?.replace("_", "")) {
            "watching", "currentlywatching" -> STATUS_WATCHING
            "seen" -> if (category == CATEGORY_MOVIES) STATUS_COMPLETED else STATUS_SEEN
            "plantowatch", "watchlist" -> STATUS_PLAN_TO_WATCH
            "hold", "onhold" -> STATUS_HOLD
            "completed" -> STATUS_COMPLETED
            "dropped" -> STATUS_DROPPED
            else -> if (category == CATEGORY_MOVIES && hasWatchedAt) STATUS_COMPLETED else STATUS_WATCHING
        }
    }

    private fun buildStableKeyOrNull(
        category: String,
        simklId: Long,
        tmdbId: Int?,
        tvdbId: Int?,
        imdbId: String?,
        slug: String?
    ): String? {
        if (simklId > 0L || tmdbId != null || tvdbId != null || !imdbId.isNullOrBlank() || !slug.isNullOrBlank()) {
            return buildStableKey(category, simklId, tmdbId, tvdbId, imdbId, slug)
        }
        return null
    }

    fun buildStableKey(
        category: String,
        simklId: Long,
        tmdbId: Int?,
        tvdbId: Int?,
        imdbId: String?,
        slug: String?
    ): String {
        if (simklId > 0L) return "simkl_${category}_${simklId}"
        if (tmdbId != null && tmdbId > 0) return "ext_${category}_tmdb_${tmdbId}"
        if (tvdbId != null && tvdbId > 0) return "ext_${category}_tvdb_${tvdbId}"
        if (!imdbId.isNullOrBlank()) return "ext_${category}_imdb_${imdbId.trim()}"
        return "ext_${category}_slug_${slug?.trim().orEmpty()}"
    }

    private fun parseFlexibleInt(
        obj: JSONObject?,
        key: String,
        allowZero: Boolean = false
    ): Int? {
        val value = parseFlexibleLong(obj, key, allowZero) ?: return null
        return if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
    }

    private fun parseFlexibleLong(
        obj: JSONObject?,
        key: String,
        allowZero: Boolean = false
    ): Long? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val raw = obj.opt(key) ?: return null
        val parsed = when (raw) {
            is Number -> raw.toLong()
            is String -> raw.trim().toLongOrNull()
            else -> null
        } ?: return null
        return if (allowZero) parsed.takeIf { it >= 0L } else parsed.takeIf { it > 0L }
    }

    private fun optNonBlankString(obj: JSONObject?, key: String): String? {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return null
        val value = obj.optString(key, "").trim()
        return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    }

    const val CATEGORY_SHOWS = "shows"
    const val CATEGORY_MOVIES = "movies"
    const val CATEGORY_ANIME = "anime"

    const val STATUS_WATCHING = "watching"
    const val STATUS_SEEN = "seen"
    const val STATUS_PLAN_TO_WATCH = "plantowatch"
    const val STATUS_HOLD = "hold"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_DROPPED = "dropped"

    val ALL_SHOW_ANIME_STATUSES = listOf(
        STATUS_WATCHING,
        STATUS_PLAN_TO_WATCH,
        STATUS_HOLD,
        STATUS_COMPLETED,
        STATUS_DROPPED
    )

    val ALL_MOVIE_STATUSES = listOf(
        STATUS_COMPLETED,
        STATUS_PLAN_TO_WATCH,
        STATUS_DROPPED
    )
}
