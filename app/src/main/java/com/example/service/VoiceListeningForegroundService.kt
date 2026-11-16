package com.example.service

import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.components.VoiceTranscriptOverlay
import com.example.ui.theme.VoiceBeatsTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.provider.Settings
import android.util.Log
import com.example.MainActivity
import com.example.AkkiBeatApplication

class VoiceListeningForegroundService : LifecycleService() {
    private var voiceEngine: VoiceEngine? = null
    private lateinit var externalController: ExternalMusicController
    private var windowManager: WindowManager? = null
    private var floatingComposeView: ComposeView? = null
    private val floatingVoiceState = mutableStateOf<VoiceState>(VoiceState.Idle)
    // ComposeView in a WindowManager-owned window needs both lifecycle and saved-state
    // owners. The service lifecycle is shared; this registry is intentionally non-restoring.
    private val overlaySavedStateOwner = object : SavedStateRegistryOwner {
        private val controller = SavedStateRegistryController.create(this).apply {
            performAttach()
            performRestore(null)
        }

        override val lifecycle: Lifecycle
            get() = this@VoiceListeningForegroundService.lifecycle

        override val savedStateRegistry
            get() = controller.savedStateRegistry
    }
    private var stateCollectorJob: Job? = null
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
                            observeVoiceState(it)
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

    /**
     * Displays the same Compose HUD used by MainActivity in a system overlay window.
     * The composable and state rendering are shared; only the host window differs.
     */
    private fun showSystemFloatingHud(state: VoiceState, autoHideAfterMs: Long? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            hudHandler.post { showSystemFloatingHud(state, autoHideAfterMs) }
            return
        }
        if (isAppUiInForeground()) {
            hideSystemFloatingHud()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Display-over-other-apps permission is not granted")
            return
        }
        if (overlayDismissed) return
        try {
            if (windowManager == null) {
                windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            }
            floatingVoiceState.value = state
            if (floatingComposeView == null) {
                val composeView = ComposeView(this).apply {
                    setViewTreeLifecycleOwner(this@VoiceListeningForegroundService)
                    setViewTreeSavedStateRegistryOwner(overlaySavedStateOwner)
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    setContent {
                        VoiceBeatsTheme {
                            VoiceTranscriptOverlay(
                                voiceState = floatingVoiceState.value,
                                onDismiss = {
                                    overlayDismissed = true
                                    hideSystemFloatingHud()
                                }
                            )
                        }
                    }
                }
                @Suppress("DEPRECATION")
                val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else WindowManager.LayoutParams.TYPE_PHONE
                val params = WindowManager.LayoutParams(
                    resources.displayMetrics.widthPixels - dp(24),
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
                    y = dp(28)
                }
                floatingComposeView = composeView
                windowManager?.addView(composeView, params)
            }
            hudHideRunnable?.let { hudHandler.removeCallbacks(it) }
            if (autoHideAfterMs != null) {
                val hideTask = Runnable { hideSystemFloatingHud() }
                hudHideRunnable = hideTask
                hudHandler.postDelayed(hideTask, autoHideAfterMs)
            }
        } catch (error: Exception) {
            Log.e(TAG, "Error showing Compose voice overlay", error)
            hideSystemFloatingHud()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun hideSystemFloatingHud() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            hudHandler.post { hideSystemFloatingHud() }
            return
        }
        hudHideRunnable?.let { hudHandler.removeCallbacks(it) }
        hudHideRunnable = null
        try {
            floatingComposeView?.let { view ->
                if (view.isAttachedToWindow) windowManager?.removeViewImmediate(view)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Error hiding Compose voice overlay", error)
        } finally {
            floatingComposeView = null
            floatingVoiceState.value = VoiceState.Idle
        }
    }

    private fun observeVoiceState(engine: VoiceEngine) {
        stateCollectorJob?.cancel()
        stateCollectorJob = lifecycleScope.launch {
            engine.voiceState.collect { state ->
                sharedVoiceState.value = state
                when (state) {
                    is VoiceState.Listening -> {
                        if (state.wakeWordDetected) {
                            overlayDismissed = false
                            showSystemFloatingHud(state, 12_000)
                        }
                    }
                    is VoiceState.Processing -> showSystemFloatingHud(state, 12_000)
                    is VoiceState.Error -> showSystemFloatingHud(state, 12_000)
                    is VoiceState.Executed -> Unit // handleCommand supplies the actual execution result
                    is VoiceState.Idle -> {
                        hideSystemFloatingHud()
                    }
                }
            }
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

    private fun isAppUiInForeground(): Boolean {
        // Process importance is not a reliable UI signal: microphone foreground services
        // can keep the app process foreground-important after its Activity is backgrounded.
        // ProcessLifecycleOwner tracks actual started Activities instead.
        return ProcessLifecycleOwner.get().lifecycle.currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }

    private fun handleCommand(command: VoiceCommand, raw: String) {
        val mode = externalController.targetMode()

        // Keep the same popup visible for the command result after recognition.
        // The in-app popup remains responsible while the app is foreground.
        if (command is VoiceCommand.WakeWordOnly) return

        val isVolumeCommand = command is VoiceCommand.VolumeUp ||
            command is VoiceCommand.VolumeDown ||
            command is VoiceCommand.Mute ||
            command is VoiceCommand.Unmute ||
            command is VoiceCommand.SetVolumeLevel

        if (mode == "akki_beat" && !isAppUiInForeground() && isVolumeCommand) {
            val applied = when (command) {
                VoiceCommand.VolumeUp -> externalController.adjustVolume(true)
                VoiceCommand.VolumeDown -> externalController.adjustVolume(false)
                VoiceCommand.Mute -> externalController.setMuted(true)
                VoiceCommand.Unmute -> externalController.setMuted(false)
                is VoiceCommand.SetVolumeLevel -> externalController.setVolumePercent(command.percentage)
                else -> false
            }
            val message = if (applied) "🔊 Phone media volume updated: $raw"
                else "⚠️ Android did not apply the phone's media volume change"
            showSystemFloatingHud(VoiceState.Executed(command, raw, message), 12_000)
            return
        }

        if (mode == "akki_beat") {
            val handled = dispatchAkkiBeatCommand(command)
            val message = if (handled) "▶ Akki Beat: $raw"
                else "⚠️ Akki Beat command not supported: $raw"
            showSystemFloatingHud(VoiceState.Executed(command, raw, message), 12_000)
            return
        }

        // External/auto mode remains strict: no fallback to Akki Beat.
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

        val message = if (handledExternal) {
            if (isVolumeCommand) "🔊 Phone media volume updated: $raw" else "🎯 External player: $raw"
        } else {
            val reason = when {
                isVolumeCommand -> "Android did not apply the phone's media volume change"
                externalController.hasNotificationAccess() -> "No active external player"
                else -> "Enable Akki Beat Notification Access in Android Settings"
            }
            "⚠️ $reason: $raw"
        }
        showSystemFloatingHud(VoiceState.Executed(command, raw, message), 12_000)
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
            VoiceCommand.Mute -> {
                if (!player.playerState.value.isMuted) player.toggleMute()
                true
            }
            VoiceCommand.Unmute -> {
                if (player.playerState.value.isMuted) player.toggleMute()
                true
            }
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
        sharedVoiceState.value = VoiceState.Idle
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(HANDS_FREE_KEY, false).apply()
        stateCollectorJob?.cancel()
        stateCollectorJob = null
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
        sharedVoiceState.value = VoiceState.Idle
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        hudHideRunnable?.let { hudHandler.removeCallbacks(it) }
        hideSystemFloatingHud()
        voiceEngine?.onDestroy()
        voiceEngine = null
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        // The service owns hands-free recognition; the Activity observes this same
        // state so the exact in-app Compose HUD can render the service's live state.
        val sharedVoiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)

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
