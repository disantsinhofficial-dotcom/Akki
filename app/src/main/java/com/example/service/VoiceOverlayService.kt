package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class VoiceOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
    }

    private fun createOverlayView() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("VoiceOverlayService", "Overlay permission not granted.")
            return
        }
        try {
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
                x = 0
                y = 60
            }

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(36, 28, 36, 28)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#121826")) // Midnight surface
                    cornerRadius = 32f
                    setStroke(4, Color.parseColor("#00E5FF")) // Neon Turquoise border
                }
                elevation = 30f
            }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val titleText = TextView(this).apply {
                text = "🎙 Akki Beat Voice Engine: Listening..."
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val titleParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            val closeBtn = TextView(this).apply {
                text = "  ✕  "
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setOnClickListener {
                    try {
                        val stopIntent = Intent(applicationContext, VoiceListeningForegroundService::class.java).setAction(VoiceListeningForegroundService.ACTION_STOP)
                        startService(stopIntent)
                        stopSelf()
                    } catch (_: Exception) {}
                }
            }

            topRow.addView(titleText, titleParams)
            topRow.addView(closeBtn)

            val descText = TextView(this).apply {
                text = "✨ Speak command now (e.g. \"Next song\", \"Volume up\", \"Awaaz badhao\"). Say wake-word again to cancel, or auto-off in 12s."
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 12f
                setPadding(0, 10, 0, 0)
            }

            container.addView(topRow)
            container.addView(descText)

            overlayView = container
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e("VoiceOverlayService", "Error creating system overlay view", e)
        }
    }

    override fun onDestroy() {
        try {
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
                overlayView = null
            }
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
