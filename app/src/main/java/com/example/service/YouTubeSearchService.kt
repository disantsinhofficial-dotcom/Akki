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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Searches YouTube tracks. Falls back to curated YouTube catalog if network is unavailable.
     */
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext DefaultTracks.youtubeTracks
        }

        val results = mutableListOf<Track>()

        try {
            // First attempt: Fast YouTube HTML search scrape
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url("https://www.youtube.com/results?search_query=$encoded")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9,hi;q=0.8")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val html = response.body?.string().orEmpty()
                    val parsed = parseYouTubeHtml(html)
                    if (parsed.isNotEmpty()) {
                        results.addAll(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "YouTube live scrape error: ${e.message}")
        }

        // If live search returned empty or error, filter curated YouTube tracks by query
        if (results.isEmpty()) {
            val matchingCurated = DefaultTracks.youtubeTracks.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.genre.contains(query, ignoreCase = true)
            }
            if (matchingCurated.isNotEmpty()) {
                results.addAll(matchingCurated)
            } else {
                // If user searched a custom track, create a dynamic playable YouTube track entry!
                // Any valid query can be played via YouTube search or video ID
                results.add(
                    Track(
                        id = "yt_dyn_${System.currentTimeMillis()}",
                        title = query.replaceFirstChar { it.uppercase() },
                        artist = "YouTube Music Audio",
                        album = "YouTube Search",
                        durationMs = 210000,
                        sourceUrl = "https://www.youtube.com/results?search_query=${URLEncoder.encode(query, "UTF-8")}",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
                        isOnline = true,
                        genre = "YouTube Stream",
                        bitRate = "YouTube HQ",
                        isYouTube = true,
                        youtubeVideoId = null // Will trigger search-play or fallback video
                    )
                )
                results.addAll(DefaultTracks.youtubeTracks)
            }
        }

        results
    }

    private fun parseYouTubeHtml(html: String): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            // Locate ytInitialData JSON
            val pattern = Pattern.compile("var ytInitialData = (\\{.*?\\});</script>")
            val matcher = pattern.matcher(html)
            if (matcher.find()) {
                val jsonString = matcher.group(1) ?: return tracks
                val root = JSONObject(jsonString)

                val contents = root.optJSONObject("contents")
                    ?.optJSONObject("twoColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")
                    ?.optJSONArray("contents")

                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val itemSection = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer")
                        val items = itemSection?.optJSONArray("contents") ?: continue

                        for (j in 0 until items.length()) {
                            val videoRenderer = items.optJSONObject(j)?.optJSONObject("videoRenderer") ?: continue
                            val videoId = videoRenderer.optString("videoId")
                            if (videoId.isBlank()) continue

                            val title = videoRenderer.optJSONObject("title")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                ?: "Unknown Track"

                            val artist = videoRenderer.optJSONObject("ownerText")
                                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                ?: "YouTube Artist"

                            val durationText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "3:30"
                            val durationMs = parseDurationMs(durationText)

                            val artworkUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

                            tracks.add(
                                Track(
                                    id = "yt_$videoId",
                                    title = title,
                                    artist = artist,
                                    album = "YouTube Audio",
                                    durationMs = durationMs,
                                    sourceUrl = "https://www.youtube.com/watch?v=$videoId",
                                    artworkUrl = artworkUrl,
                                    isOnline = true,
                                    genre = "YouTube",
                                    bitRate = "YouTube HQ",
                                    isYouTube = true,
                                    youtubeVideoId = videoId
                                )
                            )

                            if (tracks.size >= 15) break
                        }
                        if (tracks.isNotEmpty()) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse ytInitialData: ${e.message}")
        }
        return tracks
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
