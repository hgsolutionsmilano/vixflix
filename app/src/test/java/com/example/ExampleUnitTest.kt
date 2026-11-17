package com.example

import com.example.data.remote.GitHubUpdateService
import com.example.data.remote.VixSrcStreamResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    private val okHttp = OkHttpClient()
    private val resolver = VixSrcStreamResolver(okHttp)
    private val updater = GitHubUpdateService(okHttp)

    @Test
    fun `vixsrc movie and tv url builder includes startAt seconds and startMinutes`() {
        val movieUrl = resolver.buildVixSrcApiUrl(
            tmdbId = 120,
            mediaType = "movie",
            startSeconds = 840L,
            lang = "it"
        )
        assertTrue(movieUrl.startsWith("https://vixsrc.to/movie/120"))
        assertTrue(movieUrl.contains("startAt=840"))
        assertTrue(movieUrl.contains("startMinutes=14"))
        assertTrue(movieUrl.contains("lang=it"))

        val tvUrl = resolver.buildVixSrcApiUrl(
            tmdbId = 84773,
            mediaType = "tv",
            season = 1,
            episode = 1,
            startSeconds = 1260L,
            lang = "it"
        )
        assertTrue(tvUrl.startsWith("https://vixsrc.to/tv/84773/1/1"))
        assertTrue(tvUrl.contains("startAt=1260"))
        assertTrue(tvUrl.contains("startMinutes=21"))
    }

    @Test
    fun `vixsrc live resolution for Il Signore degli Anelli (TMDB 120) extracts real HLS masterPlaylist`() = runBlocking {
        val result = resolver.resolveStreamForExoPlayer(
            tmdbId = 120,
            mediaType = "movie",
            startSeconds = 300L,
            lang = "it"
        )
        assertTrue("Expected live resolution of TMDB 120 to succeed: ${result.exceptionOrNull()}", result.isSuccess)
        val stream = result.getOrThrow()
        assertTrue(stream.streamUrl.startsWith("https://vixsrc.to/playlist/"))
        assertTrue(stream.streamUrl.contains("token="))
        assertTrue(stream.streamUrl.contains("expires="))
        assertTrue(stream.streamUrl.contains("startAt=300"))

        // Verify exoOkHttpClient fetches the live #EXTM3U master playlist via VixSrcOkHttpInterceptor
        val playlistReq = Request.Builder().url(stream.streamUrl).build()
        resolver.exoOkHttpClient.newCall(playlistReq).execute().use { resp ->
            assertEquals(200, resp.code)
            val m3u8 = resp.body?.string().orEmpty()
            assertTrue("Expected #EXTM3U header in playlist", m3u8.startsWith("#EXTM3U"))
            assertTrue("Expected Italian audio track in playlist", m3u8.contains("LANGUAGE=\"ita\""))
        }

        // Verify exoOkHttpClient fetches the 16-byte AES-128 key from https://vixsrc.to/storage/enc.key
        val keyReq = Request.Builder().url("https://vixsrc.to/storage/enc.key").build()
        resolver.exoOkHttpClient.newCall(keyReq).execute().use { keyResp ->
            assertEquals(200, keyResp.code)
            val keyBytes = keyResp.body?.bytes()
            assertNotNull(keyBytes)
            assertEquals(16, keyBytes!!.size)
        }
    }

    @Test
    fun `github semantic version comparison detects newer releases`() {
        assertTrue(updater.isRemoteVersionNewer("v1.0.2", "1.0.1"))
        assertTrue(updater.isRemoteVersionNewer("1.2.0", "1.0.9"))
        assertFalse(updater.isRemoteVersionNewer("v1.0.1", "1.0.1"))
        assertFalse(updater.isRemoteVersionNewer("v1.0.0", "1.0.1"))
    }
}
