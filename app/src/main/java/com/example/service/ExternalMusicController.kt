package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicSessionListenerService : NotificationListenerService() {
    override fun onListenerConnected() { super.onListenerConnected(); activeService = this }
    override fun onListenerDisconnected() { if (activeService === this) activeService = null; super.onListenerDisconnected() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { }
    companion object {
        @Volatile private var activeService: MusicSessionListenerService? = null
        fun activeControllers(): List<MediaController> {
            return try {
                val service = activeService
                if (service == null) emptyList() else {
                    val manager = service.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
                    manager.getActiveSessions(ComponentName(service, MusicSessionListenerService::class.java))
                }
            } catch (_: Exception) { emptyList() }
        }
    }
}

class ExternalMusicController(private val context: Context) {
    private val prefs = context.getSharedPreferences("external_music_target", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isAutoDetect() = prefs.getBoolean("auto_detect", true)
    fun setAutoDetect(enabled: Boolean) { prefs.edit().putBoolean("auto_detect", enabled).apply() }

    // Target mode: "auto", "akki_beat", or "external"
    fun targetMode(): String = prefs.getString("target_mode", "auto") ?: "auto"
    fun setTargetMode(mode: String) { prefs.edit().putString("target_mode", mode).apply() }

    private fun selectedController(): MediaController? {
        val mode = targetMode()
        if (mode == "akki_beat") return null // Controlled locally by Akki Beat player

        val sessions = MusicSessionListenerService.activeControllers()
            .filter { it.packageName != context.packageName }

        if (mode == "auto" || isAutoDetect()) {
            return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        }
        return sessions.firstOrNull()
    }

    fun send(action: String): Boolean {
        if (targetMode() == "akki_beat") return false // Handled locally
        val controller = selectedController() ?: return false
        return try { when (action) {
            "play" -> controller.transportControls.play()
            "pause" -> controller.transportControls.pause()
            "next" -> controller.transportControls.skipToNext()
            "previous" -> controller.transportControls.skipToPrevious()
            "toggle" -> if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause() else controller.transportControls.play()
            else -> return false
        }; true } catch (_: Exception) { false }
    }
    fun adjustVolume(up: Boolean): Boolean = try {
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0); true
    } catch (_: Exception) { false }
    fun setVolumePercent(percent: Int): Boolean = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent.coerceIn(0, 100) / 100f).toInt(), 0)
        true
    } catch (_: Exception) { false }
    fun setMuted(muted: Boolean): Boolean = try {
        if (muted) { val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); if (current > 0) prefs.edit().putInt("last_music_volume", current).apply(); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
        else { val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val restore = prefs.getInt("last_music_volume", (max * 0.5f).toInt().coerceAtLeast(1)); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restore.coerceIn(1, max), 0) }; true
    } catch (_: Exception) { false }
}
