package com.example

import android.app.Application
import com.example.service.AudioPlayerManager

/**
 * Holds the Akki Beat player for the lifetime of the app process.
 * The foreground voice service and UI share the same playback instance.
 */
class AkkiBeatApplication : Application() {
    val playerManager: AudioPlayerManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AudioPlayerManager(applicationContext)
    }
}
