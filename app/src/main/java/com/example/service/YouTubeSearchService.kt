package com.example.service

import android.util.Log
import com.example.model.DefaultTracks
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeSearchService {

    private val tag = "YouTubeSearchService"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Searches YouTube tracks with instant live mobile scrape and multi-renderer parser.
     * Supports song names, artists, lyrics fragments, and movie tracks.
     */
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext DefaultTracks.youtubeTracks
        }

        val results = mutableListOf<Track>()

        // 1. Live Mobile YouTube search scrape (extracts videoWithContextRenderer & compactVideoRenderer)
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url("https://m.youtube.com/results?search_query=$encoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            val html = client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }

            if (html.isNotBlank()) {
                val parsed = parseMobileYouTubeHtml(html)
                if (parsed.isNotEmpty()) {
                    results.addAll(parsed)
                    Log.d(tag, "Successfully parsed ${parsed.size} tracks from mobile YouTube for '$trimmed'")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Mobile YouTube scrape error: ${e.message}")
        }

        // 2. Desktop YouTube fallback scrape if mobile returned empty
        if (results.isEmpty()) {
            try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val request = Request.Builder()
                    .url("https://www.youtube.com/results?search_query=$encoded")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
                    .build()

                val html = client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string().orEmpty() else ""
                }

                if (html.isNotBlank()) {
                    val parsed = parseDesktopYouTubeHtml(html)
                    if (parsed.isNotEmpty()) {
                        results.addAll(parsed)
                        Log.d(tag, "Parsed ${parsed.size} tracks from desktop YouTube")
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Desktop YouTube scrape error: ${e.message}")
            }
        }

        // 3. Fallback matching from curated catalog or dynamic search track
        if (results.isEmpty()) {
            val matchingCurated = DefaultTracks.youtubeTracks.filter {
                it.title.contains(trimmed, ignoreCase = true) ||
                        it.artist.contains(trimmed, ignoreCase = true) ||
                        it.genre.contains(trimmed, ignoreCase = true)
            }
            if (matchingCurated.isNotEmpty()) {
                results.addAll(matchingCurated)
            }

            // Always add a playable dynamic YouTube search item that triggers Iframe search-play
            results.add(
                0,
                Track(
                    id = "yt_search_${System.currentTimeMillis()}",
                    title = trimmed.replaceFirstChar { it.uppercase() },
                    artist = "YouTube Music Audio",
                    album = "YouTube Top Result",
                    durationMs = 240000L,
                    sourceUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(trimmed, "UTF-8")}",
                    artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                    isOnline = true,
                    genre = "YouTube",
                    bitRate = "YouTube HQ",
                    isYouTube = true,
                    youtubeVideoId = null
                )
            )
        }

        results.distinctBy { it.id }
    }

    private fun parseMobileYouTubeHtml(html: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            // Mobile YouTube embeds ytInitialData = '...'
            val pattern = Pattern.compile("ytInitialData\\s*=\\s*['\"]([^'\"]+)['\"]")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val rawData = matcher.group(1) ?: return tracks
                val decodedJson = decodeHexAndUnicode(rawData)
                val root = JSONObject(decodedJson)
                extractTracksRecursively(root, tracks)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing mobile YouTube JSON: ${e.message}")
        }
        return tracks
    }

    private fun parseDesktopYouTubeHtml(html: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val pattern = Pattern.compile("var ytInitialData\\s*=\\s*(\\{.*?\\});</script>")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonString = matcher.group(1) ?: return tracks
                val root = JSONObject(jsonString)
                extractTracksRecursively(root, tracks)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error parsing desktop YouTube JSON: ${e.message}")
        }
        return tracks
    }

    private fun extractTracksRecursively(obj: Any, out: MutableList<Track>) {
        if (out.size >= 25) return
        when (obj) {
            is JSONObject -> {
                // Check videoWithContextRenderer (Mobile)
                if (obj.has("videoWithContextRenderer")) {
                    val v = obj.optJSONObject("videoWithContextRenderer")
                    val vid = v?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                    val title = v?.optJSONObject("headline")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text")
                    val artist = v?.optJSONObject("shortBylineText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "YouTube Music"
                    val durationStr = v?.optJSONObject("lengthText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "3:30"

                    if (!vid.isNullOrBlank() && !title.isNullOrBlank()) {
                        out.add(
                            Track(
                                id = "yt_$vid",
                                title = title,
                                artist = artist,
                                album = "YouTube Audio",
                                durationMs = parseDurationMs(durationStr),
                                sourceUrl = "https://www.youtube.com/watch?v=$vid",
                                artworkUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                                isOnline = true,
                                genre = "YouTube",
                                bitRate = "YouTube HQ",
                                isYouTube = true,
                                youtubeVideoId = vid
                            )
                        )
                    }
                }

                // Check videoRenderer (Desktop)
                if (obj.has("videoRenderer")) {
                    val v = obj.optJSONObject("videoRenderer")
                    val vid = v?.optString("videoId")
                    val title = v?.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text")
                    val artist = v?.optJSONObject("ownerText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "YouTube Artist"
                    val durationStr = v?.optJSONObject("lengthText")?.optString("simpleText") ?: "3:30"

                    if (!vid.isNullOrBlank() && !title.isNullOrBlank()) {
                        out.add(
                            Track(
                                id = "yt_$vid",
                                title = title,
                                artist = artist,
                                album = "YouTube Audio",
                                durationMs = parseDurationMs(durationStr),
                                sourceUrl = "https://www.youtube.com/watch?v=$vid",
                                artworkUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                                isOnline = true,
                                genre = "YouTube",
                                bitRate = "YouTube HQ",
                                isYouTube = true,
                                youtubeVideoId = vid
                            )
                        )
                    }
                }

                // Check compactVideoRenderer (Older Mobile)
                if (obj.has("compactVideoRenderer")) {
                    val v = obj.optJSONObject("compactVideoRenderer")
                    val vid = v?.optString("videoId")
                    val title = v?.optJSONObject("title")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text")
                    val artist = v?.optJSONObject("shortBylineText")
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optString("text") ?: "YouTube Music"
                    val durationStr = v?.optJSONObject("lengthText")?.optString("simpleText") ?: "3:30"

                    if (!vid.isNullOrBlank() && !title.isNullOrBlank()) {
                        out.add(
                            Track(
                                id = "yt_$vid",
                                title = title,
                                artist = artist,
                                album = "YouTube Audio",
                                durationMs = parseDurationMs(durationStr),
                                sourceUrl = "https://www.youtube.com/watch?v=$vid",
                                artworkUrl = "https://img.youtube.com/vi/$vid/hqdefault.jpg",
                                isOnline = true,
                                genre = "YouTube",
                                bitRate = "YouTube HQ",
                                isYouTube = true,
                                youtubeVideoId = vid
                            )
                        )
                    }
                }

                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = obj.opt(key)
                    if (child != null) {
                        extractTracksRecursively(child, out)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val item = obj.opt(i)
                    if (item != null) {
                        extractTracksRecursively(item, out)
                    }
                }
            }
        }
    }

    private fun decodeHexAndUnicode(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        val len = input.length
        while (i < len) {
            val c = input[i]
            if (c == '\\' && i + 1 < len) {
                val next = input[i + 1]
                if (next == 'x' && i + 3 < len) {
                    try {
                        val hex = input.substring(i + 2, i + 4)
                        sb.append(hex.toInt(16).toChar())
                        i += 4
                        continue
                    } catch (_: Exception) {}
                } else if (next == 'u' && i + 5 < len) {
                    try {
                        val hex = input.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 6
                        continue
                    } catch (_: Exception) {}
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun parseDurationMs(durationStr: String): Long {
        val parts = durationStr.split(":")
        var totalSec = 0L
        try {
            if (parts.size == 2) {
                totalSec = parts[0].toLong() * 60 + parts[1].toLong()
            } else if (parts.size == 3) {
                totalSec = parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
            }
        } catch (_: Exception) {}
        return if (totalSec > 0) totalSec * 1000 else 210000L
    }
}
