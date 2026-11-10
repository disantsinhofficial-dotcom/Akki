package com.example.service

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.DefaultTracks
import com.example.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalMusicScanner(private val context: Context) {

    private val tag = "LocalMusicScanner"

    companion object {
        fun hasStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    /**
     * Scans all device audio files using MediaStore.
     */
    suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        if (!hasStoragePermission(context)) {
            Log.w(tag, "Storage permission not granted. Returning demo tracks.")
            return@withContext DefaultTracks.bundledLocalDemo
        }

        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATA
            )

            // Select all music files
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"
            val queryUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            context.contentResolver.query(
                queryUri,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)?.takeIf { it.isNotBlank() } ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Device Audio"
                    val album = cursor.getString(albumColumn) ?: "Local Album"
                    val duration = cursor.getLong(durationColumn)
                    val albumId = cursor.getLong(albumIdColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    if (duration > 3000) { // filter out ringtones < 3 sec
                        tracks.add(
                            Track(
                                id = "local_$id",
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                sourceUrl = contentUri,
                                artworkUrl = artworkUri,
                                isOnline = false,
                                genre = "Local Device",
                                bitRate = "Local Storage"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to scan local media store: ${e.message}")
        }

        // If local storage query returned empty (e.g. fresh emulator or no MP3s in library yet),
        // fallback to bundled local demo tracks
        if (tracks.isEmpty()) {
            tracks.addAll(DefaultTracks.bundledLocalDemo)
        }

        tracks
    }

    /**
     * Extracts metadata from picked Document URIs (SAF)
     */
    fun parsePickedAudioUri(uri: Uri): Track? {
        try {
            // Take persistable permission if available
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            var displayName = "Audio File"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    displayName = cursor.getString(nameIndex) ?: "Audio File"
                }
            }

            var title = displayName.substringBeforeLast(".")
            var artist = "Local Storage"
            var album = "Custom Files"
            var durationMs = 180000L

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                    if (it.isNotBlank()) title = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                    if (it.isNotBlank()) artist = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                    if (it.isNotBlank()) album = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    if (it > 0) durationMs = it
                }
            } catch (e: Exception) {
                Log.w(tag, "Metadata extraction failed: ${e.message}")
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }

            return Track(
                id = "picked_${System.currentTimeMillis()}_${(0..999).random()}",
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                sourceUrl = uri.toString(),
                artworkUrl = null,
                isOnline = false,
                genre = "Local Storage",
                bitRate = "Device MP3"
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing picked audio: ${e.message}")
            return null
        }
    }
}
