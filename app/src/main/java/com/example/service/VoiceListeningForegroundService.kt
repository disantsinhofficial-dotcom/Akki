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
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity
import com.example.AkkiBeatApplication
import com.example.R

class VoiceListeningForegroundService : Service() {
    private var voiceEngine: VoiceEngine? = null
    private lateinit var externalController: ExternalMusicController
    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var overlayDismissed = false
    private val hudHandler = Handler(Looper.getMainLooper())
    private var hudHideRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        externalController = ExternalMusicController(this)
        createNotificationChannel()
        // Keep CPU awake while the user explicitly enables hands-free listening.
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:VoiceListening")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(HANDS_FREE_KEY, false).apply()
                stopVoiceService()
                return START_NOT_STICKY
            }
            ACTION_SET_WAKE_WORD -> {
                val word = intent.getStringExtra(EXTRA_WAKE_WORD)?.trim().orEmpty()
                if (word.isNotBlank()) voiceEngine?.setWakeWord(word)
                return START_STICKY
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
                    if (wakeLock?.isHeld != true) {
                        wakeLock?.acquire()
                    }
                    if (voiceEngine == null) {
                        voiceEngine = VoiceEngine(applicationContext) { command, raw ->
                            handleCommand(command, raw)
                        }.also {
                            it.setHandsFreeModeState(true)
                            it.startListening()
                        }
                    }
                    // Keep the HUD hidden during passive wake-word monitoring.
                    // It appears only after the saved wake-word is recognized.
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to start microphone foreground service", error)
                    stopVoiceService()
                }
            }
        }
        return START_STICKY
    }

    private fun showSystemFloatingHud(textStr: String, autoHideAfterMs: Long? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Display-over-other-apps permission is not granted")
            return
        }
        if (overlayDismissed) return
        try {
            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            val density = resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()

            if (floatingView == null) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0xF018293B.toInt())
                        cornerRadius = dp(28).toFloat()
                        setStroke(dp(2), 0xFF00E5C0.toInt())
                    }
                    elevation = dp(16).toFloat()
                }
                val topRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val mic = TextView(this).apply {
                    text = "🎙"
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(0xFF00E5C0.toInt())
                    setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                val title = TextView(this).apply {
                    text = "Voice Engine: Listening..."
                    setTextColor(0xFF00E5C0.toInt())
                    textSize = 17f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val close = TextView(this).apply {
                    text = "✕"
                    textSize = 24f
                    setTextColor(0xFFB8C2D1.toInt())
                    gravity = Gravity.CENTER
                    setPadding(dp(12), dp(4), dp(4), dp(4))
                    setOnClickListener {
                        overlayDismissed = true
                        hideSystemFloatingHud()
                    }
                    contentDescription = "Dismiss voice popup"
                }
                topRow.addView(mic)
                topRow.addView(title, LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                topRow.addView(close)
                val status = TextView(this).apply {
                    id = android.R.id.text1
                    text = textStr
                    setTextColor(0xFFF4F7FB.toInt())
                    textSize = 14f
                    setPadding(dp(4), dp(12), dp(4), dp(2))
                }
                card.addView(topRow)
                card.addView(status)

                @Suppress("DEPRECATION")
                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                    x = dp(12)
                    y = dp(44)
                    width = resources.displayMetrics.widthPixels - dp(24)
                }
                floatingView = card
                windowManager?.addView(card, params)
            } else {
                floatingView?.findViewById<TextView>(android.R.id.text1)?.text = textStr
                floatingView?.visibility = View.VISIBLE
            }
            hudHideRunnable?.let { hudHandler.removeCallbacks(it) }
            if (autoHideAfterMs != null) {
                val hideTask = Runnable { hideSystemFloatingHud() }
                hudHideRunnable = hideTask
                hudHandler.postDelayed(hideTask, autoHideAfterMs)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing system floating HUD", e)
            floatingView = null
        }
    }

    private fun hideSystemFloatingHud() {
        hudHideRunnable?.let { hudHandler.removeCallbacks(it) }
        hudHideRunnable = null
        try {
            floatingView?.let { view -> windowManager?.removeView(view) }
        } catch (e: Exception) {
            Log.w(TAG, "Error hiding system floating HUD", e)
        } finally {
            floatingView = null
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

        // Every recognized wake-word/command gets a fresh HUD, including after a
        // previous popup was dismissed by the user.
        overlayDismissed = false
        if (command is VoiceCommand.WakeWordOnly) {
            showSystemFloatingHud("✨ Wake-word recognized! Speak your command...", 10_000)
            return
        }

        showSystemFloatingHud("🗣 Heard: \"$raw\"", 10_000)

        if (mode == "akki_beat") {
            val handledAkkiBeat = dispatchAkkiBeatCommand(command)
            showSystemFloatingHud(
                if (handledAkkiBeat) "▶ Akki Beat: $raw" else "⚠️ Akki Beat command not supported: $raw",
                10_000
            )
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
            val isVolumeCommand = command is VoiceCommand.VolumeUp ||
                command is VoiceCommand.VolumeDown || command is VoiceCommand.SetVolumeLevel
            showSystemFloatingHud(
                if (isVolumeCommand) "🔊 Phone media volume updated: $raw" else "🎯 External player: $raw",
                10_000
            )
        } else {
            val isVolumeCommand = command is VoiceCommand.VolumeUp ||
                command is VoiceCommand.VolumeDown || command is VoiceCommand.SetVolumeLevel ||
                command is VoiceCommand.Mute || command is VoiceCommand.Unmute
            val reason = when {
                isVolumeCommand -> "Android did not apply the phone's media volume change"
                externalController.hasNotificationAccess() -> "No active external player"
                else -> "Enable Akki Beat Notification Access in Android Settings"
            }
            showSystemFloatingHud("⚠️ $reason: $raw", 10_000)
        }
    }

    /**
     * Run Akki Beat commands against the Application-owned player instance.
     * This does not depend on MainActivity or its BroadcastReceiver being alive.
     */
    private fun dispatchAkkiBeatCommand(command: VoiceCommand): Boolean {
        val player = (application as AkkiBeatApplication).playerManager
        return when (command) {
            VoiceCommand.Play -> { player.resume(); true }
            VoiceCommand.Pause -> { player.pause(); true }
            VoiceCommand.TogglePlayPause -> { player.togglePlayPause(); true }
            VoiceCommand.NextTrack -> { player.skipToNext(); true }
            VoiceCommand.PreviousTrack -> { player.skipToPrevious(); true }
            VoiceCommand.VolumeUp -> { player.adjustVolumeRelative(+0.10f); true }
            VoiceCommand.VolumeDown -> { player.adjustVolumeRelative(-0.10f); true }
            VoiceCommand.Mute -> { player.toggleMute(); true }
            VoiceCommand.Unmute -> { player.toggleMute(); true }
            is VoiceCommand.SetVolumeLevel -> { player.setVolumePercent(command.percentage); true }
            VoiceCommand.BoostBass -> { player.applyPreset("Bass Boost"); true }
            VoiceCommand.Shuffle -> { player.toggleShuffle(); true }
            is VoiceCommand.PlayGenre -> {
                val matching = player.playerState.value.queue.filter {
                    it.genre.equals(command.genre, ignoreCase = true)
                }
                if (matching.isEmpty()) false else {
                    player.setQueue(matching, startIndex = 0, autoPlay = true)
                    true
                }
            }
            is VoiceCommand.SearchPlay -> {
                val matching = player.playerState.value.queue.filter {
                    it.title.contains(command.query, ignoreCase = true) ||
                        it.artist.contains(command.query, ignoreCase = true)
                }
                if (matching.isEmpty()) false else {
                    player.setQueue(matching, startIndex = 0, autoPlay = true)
                    true
                }
            }
            is VoiceCommand.WakeWordOnly, is VoiceCommand.Unknown -> false
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
        voiceEngine?.onDestroy()
        voiceEngine = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // stopWithTask=false keeps this foreground service alive when the app task is swiped away.
        // Do not start a duplicate service or use an alarm to bypass Android background-FGS limits.
        Log.i(TAG, "App task removed; foreground voice service remains active")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        hideSystemFloatingHud()
        voiceEngine?.onDestroy()
        voiceEngine = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
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
        const val ACTION_SET_WAKE_WORD = "com.example.voice.SET_WAKE_WORD"
        const val EXTRA_WAKE_WORD = "wake_word"
    }
}
