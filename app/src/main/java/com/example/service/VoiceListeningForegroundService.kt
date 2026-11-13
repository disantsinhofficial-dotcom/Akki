package com.example.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.R

class VoiceListeningForegroundService : Service() {
    private var voiceEngine: VoiceEngine? = null
    private lateinit var externalController: ExternalMusicController
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

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
                    showSystemFloatingHud("🎙 Voice Engine: Listening... (Say wake-word)")
                    try {
                        startService(Intent(this, VoiceOverlayService::class.java))
                    } catch (_: Exception) {}
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start microphone foreground service", error)
                    stopVoiceService()
                }
            }
        }
        return START_STICKY
    }

    private fun showSystemFloatingHud(textStr: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }
        try {
            val serviceContext = this@VoiceListeningForegroundService
            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            if (floatingView == null) {
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                    y = 70
                }

                val card = LinearLayout(serviceContext).apply {
                    orientation = LinearLayout.VERTICAL
                    setBackgroundColor(0xF0111522.toInt())
                    setPadding(36, 26, 36, 26)
                    elevation = 24f
                }

                val titleView = TextView(serviceContext).apply {
                    text = "🎙 Akki Beat Voice HUD"
                    setTextColor(0xFF00E5FF.toInt())
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }

                val statusView = TextView(serviceContext).apply {
                    id = android.R.id.text1
                    text = textStr
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 14f
                    setPadding(0, 6, 0, 0)
                }

                card.addView(titleView)
                card.addView(statusView)

                floatingView = card
                windowManager?.addView(floatingView, layoutParams)
            } else {
                val statusView = floatingView?.findViewById<TextView>(android.R.id.text1)
                statusView?.text = textStr
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing system floating HUD", e)
        }
    }

    private fun hideSystemFloatingHud() {
        try {
            if (floatingView != null && windowManager != null) {
                windowManager?.removeView(floatingView)
                floatingView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding system floating HUD", e)
        }
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
            showSystemFloatingHud("✨ Wake-word recognized! Speak command...")
            postCommandNotification("Akki Beat Voice AI", "✨ Wake-word recognized! Speak command...")
            return
        }

        showSystemFloatingHud("🗣 Heard: \"$raw\"")
        postCommandNotification("Akki Beat Command", "Heard: \"$raw\"")

        if (mode == "akki_beat") {
            val intent = Intent("com.example.VOICE_COMMAND_ACTION").apply {
                putExtra("command_type", command.javaClass.simpleName)
                putExtra("raw_text", raw)
            }
            applicationContext.sendBroadcast(intent)
            showSystemFloatingHud("▶ Executed on Akki Beat: $raw")
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
            showSystemFloatingHud("🎯 Controlled external player: \"$raw\"")
            postCommandNotification("Akki Beat External Control", "Successfully controlled active music player for \"$raw\"")
        } else {
            showSystemFloatingHud("⚠️ No active external player found.")
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
        hideSystemFloatingHud()
        try {
            stopService(Intent(this, VoiceOverlayService::class.java))
        } catch (_: Exception) {}
        voiceEngine?.onDestroy()
        voiceEngine = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartIntent = Intent(applicationContext, VoiceListeningForegroundService::class.java).apply {
            action = ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(restartIntent)
            } else {
                applicationContext.startService(restartIntent)
            }
        } catch (_: Exception) {}

        // Schedule via AlarmManager as bulletproof fallback against aggressive task killers / recents swipe
        try {
            val pendingIntent = PendingIntent.getService(
                applicationContext, 9999, restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 300,
                pendingIntent
            )
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        hideSystemFloatingHud()
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
