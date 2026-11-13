package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import com.example.R

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

    private fun postCommandNotification(title: String, message: String) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val alertChannel = NotificationChannel(
                    ALERT_CHANNEL_ID, "Voice Command Alerts", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Heads-up notifications for voice commands and wake-word"
                    enableVibration(true)
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(alertChannel)
            }

            val openAppIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, ALERT_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(Notification.BigTextStyle().bigText(message))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .build()
            }

            notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting command notification", e)
        }
    }

    private fun handleCommand(command: VoiceCommand, raw: String) {
        val mode = externalController.targetMode()

        if (command is VoiceCommand.WakeWordOnly) {
            postCommandNotification("Akki Beat Voice AI", "✨ Wake-word recognized! Speak command...")
            return
        }

        postCommandNotification("Akki Beat Command", "Heard: \"$raw\"")

        if (mode == "akki_beat") {
            val intent = Intent("com.example.VOICE_COMMAND_ACTION").apply {
                putExtra("command_type", command.javaClass.simpleName)
                putExtra("raw_text", raw)
            }
            applicationContext.sendBroadcast(intent)
            postCommandNotification("Akki Beat Internal", "Executed on Akki Beat player: $raw")
            return
        }

        // Target mode is external or auto (strictly external player control, NO fallback to Akki Beat)
        val handledExternal = when (command) {
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
            else -> false
        }

        if (handledExternal) {
            postCommandNotification("Akki Beat External Control", "Successfully controlled active music player for \"$raw\"")
        } else {
            postCommandNotification("Akki Beat Notice", "No active external player found. Please start music in Spotify/YT Music.")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Background voice control", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous hands-free voice control active"
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
                .setContentTitle("Akki Beat Voice Control")
                .setContentText("Hands-free listening is active in background & lock screen")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSound(null)
                .setVibrate(longArrayOf(0L))
                .addAction(Notification.Action.Builder(null, "Stop", stopService).build())
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Akki Beat Voice Control")
                .setContentText("Hands-free listening is active")
                .setOngoing(true)
                .setContentIntent(openApp)
                .build()
        }
    }

    private fun stopVoiceService() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(HANDS_FREE_KEY, false).apply()
        voiceEngine?.onDestroy()
        voiceEngine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        voiceEngine?.onDestroy()
        voiceEngine = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceListeningService"
        private const val CHANNEL_ID = "akki_background_voice"
        private const val ALERT_CHANNEL_ID = "akki_voice_alerts"
        private const val NOTIFICATION_ID = 7402
        private const val ALERT_NOTIFICATION_ID = 7403
        private const val PREFS = "voicebeats_wake_word_prefs"
        private const val HANDS_FREE_KEY = "hands_free_earbuds_mode"
        const val ACTION_START = "com.example.voice.START_BACKGROUND_LISTENING"
        const val ACTION_STOP = "com.example.voice.STOP_BACKGROUND_LISTENING"
    }
}
