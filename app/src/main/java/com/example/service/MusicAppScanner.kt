package com.example.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap

data class MusicPlayerApp(
    val packageName: String,
    val appName: String,
    val bitmap: Bitmap? = null
)

class MusicAppScanner(private val context: Context) {
    fun getInstalledMusicApps(): List<MusicPlayerApp> {
        val pm = context.packageManager
        val apps = mutableMapOf<String, MusicPlayerApp>()

        // 1. Query ACTION_MAIN with CATEGORY_APP_MUSIC
        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        pm.queryIntentActivities(musicIntent, 0).forEach { resolve ->
            try {
                val pkg = resolve.activityInfo.packageName
                val name = resolve.loadLabel(pm).toString()
                val bmp = try {
                    resolve.loadIcon(pm)?.toBitmap(96, 96)
                } catch (_: Exception) {
                    null
                }
                apps[pkg] = MusicPlayerApp(pkg, name, bmp)
            } catch (_: Exception) {}
        }

        // 2. Query ACTION_VIEW with audio data
        val viewIntent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/audio/media/1"), "audio/*")
        pm.queryIntentActivities(viewIntent, 0).forEach { resolve ->
            try {
                val pkg = resolve.activityInfo.packageName
                val name = resolve.loadLabel(pm).toString()
                val bmp = try {
                    resolve.loadIcon(pm)?.toBitmap(96, 96)
                } catch (_: Exception) {
                    null
                }
                if (!apps.containsKey(pkg)) {
                    apps[pkg] = MusicPlayerApp(pkg, name, bmp)
                }
            } catch (_: Exception) {}
        }

        // 3. Known popular music/media player packages
        val knownPackages = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "org.videolan.vlc",
            "com.amazon.mp3",
            "com.apple.android.music",
            "com.jio.media.jiobeats",
            "com.soundcloud.android",
            "com.gaana",
            "com.saavn.android",
            "deezer.android.app",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro"
        )

        knownPackages.forEach { pkg ->
            if (!apps.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val name = pm.getApplicationLabel(appInfo).toString()
                    val bmp = try {
                        pm.getApplicationIcon(appInfo)?.toBitmap(96, 96)
                    } catch (_: Exception) {
                        null
                    }
                    apps[pkg] = MusicPlayerApp(pkg, name, bmp)
                } catch (_: Exception) {}
            }
        }

        return apps.values.sortedBy { it.appName }
    }
}
