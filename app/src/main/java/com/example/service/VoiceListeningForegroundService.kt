package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import com.aistudio.voicebeats.kzrqw.R

/**
 * Owns the single background SpeechRecognizer while Hands-Free mode is enabled.
 * Android requires a visible foreground-service notification for microphone use.
 */
class VoiceListeningForegroundService : Service() {
    private var voiceEngine: VoiceEngine? = null
    private lateinit var externalController: ExternalMusicController

    override fun onCreate() {
        super.onCreate()
        externalController = ExternalMusicController(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(HANDS_FREE_KEY, false).apply()
                stopVoiceService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                if (!getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(HANDS_FREE_KEY, false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                try {
                    val notification = buildNotification()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    if (voiceEngine == null) {
                        voiceEngine = VoiceEngine(applicationContext) { command, raw ->
                            handleCommand(command, raw)
                        }.also {
                            it.setHandsFreeModeState(true)
                            it.startListening()
                        }
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start microphone foreground service", error)
                    stopVoiceService()
                }
            }
        }
        return START_STICKY
    }

    private fun handleCommand(command: VoiceCommand, raw: String) {
        when (command) {
            VoiceCommand.Play -> externalController.send("play")
            VoiceCommand.Pause -> externalController.send("pause")
            VoiceCommand.TogglePlayPause -> externalController.send("toggle")
            VoiceCommand.NextTrack -> externalController.send("next")
            VoiceCommand.PreviousTrack -> externalController.send("previous")
            VoiceCommand.VolumeUp -> externalController.adjustVolume(true)
            VoiceCommand.VolumeDown -> externalController.adjustVolume(false)
            VoiceCommand.Mute -> externalController.setMuted(true)
            VoiceCommand.Unmute -> externalController.setMuted(false)
            is VoiceCommand.SetVolumeLevel -> externalController.setVolumePercent(command.percentage)
            // Local-library/equalizer commands require the app UI/player and are intentionally not
            // redirected to a different player's MediaSession.
            else -> Log.d(TAG, "Background voice command parsed: $raw")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Background voice control", NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent status for hands-free voice control"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopService = PendingIntent.getService(
            this, 2, Intent(this, VoiceListeningForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Akki Beat voice control")
                .setContentText("Hands-free listening is active")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .addAction(Notification.Action.Builder(null, "Stop", stopService).build())
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Akki Beat voice control")
                .setContentText("Hands-free listening is active")
                .setOngoing(true)
                .setContentIntent(openApp)
                .setSound(null)
                .build()
        }
    }

    private fun stopVoiceService() {
        voiceEngine?.setHandsFreeModeState(false)
        voiceEngine?.onDestroy()
        voiceEngine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // A foreground service with stopWithTask=false remains eligible to run after task removal.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        voiceEngine?.setHandsFreeModeState(false)
        voiceEngine?.onDestroy()
        voiceEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceListeningService"
        private const val CHANNEL_ID = "akki_background_voice"
        private const val NOTIFICATION_ID = 7402
        private const val PREFS = "voicebeats_wake_word_prefs"
        private const val HANDS_FREE_KEY = "hands_free_earbuds_mode"
        const val ACTION_START = "com.example.voice.START_BACKGROUND_LISTENING"
        const val ACTION_STOP = "com.example.voice.STOP_BACKGROUND_LISTENING"
    }
}
