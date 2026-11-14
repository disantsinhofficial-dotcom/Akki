package com.example.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlin.math.roundToInt

class MusicSessionListenerService : NotificationListenerService() {
    override fun onListenerConnected() { super.onListenerConnected(); activeService = this }
    override fun onListenerDisconnected() { if (activeService === this) activeService = null; super.onListenerDisconnected() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { }

    companion object {
        @Volatile private var activeService: MusicSessionListenerService? = null

        fun activeControllers(context: Context): List<MediaController> {
            return try {
                val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
                manager.getActiveSessions(ComponentName(context, MusicSessionListenerService::class.java))
            } catch (error: SecurityException) {
                android.util.Log.w("MusicSessionListener", "Notification-listener access is not enabled", error)
                emptyList()
            } catch (error: Exception) {
                android.util.Log.w("MusicSessionListener", "Unable to query active media sessions", error)
                emptyList()
            }
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

    /**
     * Resolve the external media session Android currently exposes.
     * A paused session is still a valid target (for example, Play after Pause).
     */
    private fun selectedController(): MediaController? {
        if (targetMode() == "akki_beat") return null

        val sessions = MusicSessionListenerService.activeControllers(context)
            .filter { it.packageName != context.packageName }

        val selectedPackage = prefs.getString("selected_external_package", null)
        val eligible = if (!selectedPackage.isNullOrBlank()) {
            sessions.filter { it.packageName == selectedPackage }
        } else sessions

        fun priority(controller: MediaController): Int = when (controller.playbackState?.state) {
            PlaybackState.STATE_PLAYING -> 0
            PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> 1
            PlaybackState.STATE_PAUSED -> 2
            PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING,
            PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS -> 3
            else -> 4
        }

        return eligible.minByOrNull(::priority)
    }

    fun hasNotificationAccess(): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        ).orEmpty()
        return enabled.split(':').any { it.contains(context.packageName) }
    }

    fun setSelectedExternalPackage(packageName: String?) {
        prefs.edit().apply {
            if (packageName.isNullOrBlank()) remove("selected_external_package")
            else putString("selected_external_package", packageName)
        }.apply()
    }

    fun getSelectedExternalPackage(): String? = prefs.getString("selected_external_package", null)

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
    // System media volume is independent of whether a media session is currently playing.
    fun adjustVolume(up: Boolean): Boolean = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val step = (max * 0.10f).roundToInt().coerceAtLeast(1)
        val target = (current + if (up) step else -step).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        true
    } catch (error: Exception) {
        android.util.Log.w("ExternalMusicController", "Unable to adjust media volume", error)
        false
    }

    fun setVolumePercent(percent: Int): Boolean = try {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val target = (max * percent.coerceIn(0, 100) / 100f).roundToInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        true
    } catch (error: Exception) {
        android.util.Log.w("ExternalMusicController", "Unable to set media volume", error)
        false
    }

    fun setMuted(muted: Boolean): Boolean = try {
        if (muted) { val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); if (current > 0) prefs.edit().putInt("last_music_volume", current).apply(); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
        else { val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val restore = prefs.getInt("last_music_volume", (max * 0.5f).toInt().coerceAtLeast(1)); audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restore.coerceIn(1, max), 0) }; true
    } catch (_: Exception) { false }
}
