package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DefaultTracks
import com.example.model.Track
import com.example.service.AudioPlayerManager
import com.example.service.HybridSensorManager
import com.example.service.LocalMusicScanner
import com.example.service.MusicAppScanner
import com.example.service.MusicPlayerApp
import com.example.service.VoiceCommand
import com.example.service.VoiceEngine
import com.example.service.VoiceListeningForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    PLAYER, LIBRARY, EQUALIZER, VOICE_SETTINGS
}

class VoiceBeatsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val playerManager = (context.applicationContext as com.example.AkkiBeatApplication).playerManager
    val localScanner = LocalMusicScanner(context)

    lateinit var voiceEngine: VoiceEngine
    lateinit var hybridSensorManager: HybridSensorManager

    private val _currentTab = MutableStateFlow(AppTab.PLAYER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    val externalController = com.example.service.ExternalMusicController(context)

    private val _isAutoDetectMusicPlayer = MutableStateFlow(externalController.isAutoDetect())
    val isAutoDetectMusicPlayer: StateFlow<Boolean> = _isAutoDetectMusicPlayer.asStateFlow()

    private val _targetMode = MutableStateFlow(externalController.targetMode())
    val targetMode: StateFlow<String> = _targetMode.asStateFlow()

    fun setAutoDetectMusicPlayer(enabled: Boolean) {
        externalController.setAutoDetect(enabled)
        _isAutoDetectMusicPlayer.value = enabled
    }

    fun setTargetMode(mode: String) {
        externalController.setTargetMode(mode)
        _targetMode.value = mode
    }

    // Local Storage & Bundled Tracks
    private val _localTracks = MutableStateFlow<List<Track>>(DefaultTracks.youtubeTracks)
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    private val _hasStoragePermission = MutableStateFlow(LocalMusicScanner.hasStoragePermission(context))
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission.asStateFlow()

    private val _isScanningLocal = MutableStateFlow(false)
    val isScanningLocal: StateFlow<Boolean> = _isScanningLocal.asStateFlow()

    // Floating Bubble Position (drag coordinates)
    private val _bubbleOffsetX = MutableStateFlow(0f)
    val bubbleOffsetX: StateFlow<Float> = _bubbleOffsetX.asStateFlow()

    private val _bubbleOffsetY = MutableStateFlow(0f)
    val bubbleOffsetY: StateFlow<Float> = _bubbleOffsetY.asStateFlow()

    private val _isBubbleVisible = MutableStateFlow(true)
    val isBubbleVisible: StateFlow<Boolean> = _isBubbleVisible.asStateFlow()

    // Feedback Toast / Banner message
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Filtered local tracks
    val filteredLocalTracks: StateFlow<List<Track>> = combine(
        _localTracks,
        _searchQuery,
        _selectedGenre
    ) { tracks, query, genre ->
        tracks.filter { track ->
            val matchesGenre = genre == "All" || track.genre.equals(genre, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
            matchesGenre && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultTracks.youtubeTracks)

    val genres = listOf("All", "Bollywood", "Punjabi", "Pop", "Lo-Fi", "Acoustic", "Electronic")

    init {
        // Initialize Voice Engine
        voiceEngine = VoiceEngine(context) { command, rawText ->
            handleVoiceCommand(command, rawText)
        }

        // Initialize Hybrid Sensor Manager (Shake-to-Listen OFF by default)
        hybridSensorManager = HybridSensorManager(context) {
            showStatusBanner("📳 Shake detected! Listening for command...")
            voiceEngine.startListening()
        }

        // Initial queue: start with default demo tracks
        playerManager.setQueue(DefaultTracks.youtubeTracks, startIndex = 0, autoPlay = false)

        checkStoragePermissionState()
        scanLocalMusic()
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setWakeWord(word: String) {
        val success = voiceEngine.setWakeWord(word)
        if (success) {
            if (voiceEngine.isHandsFreeModeActive.value) {
                val updateIntent = Intent(context, VoiceListeningForegroundService::class.java).apply {
                    action = VoiceListeningForegroundService.ACTION_SET_WAKE_WORD
                    putExtra(VoiceListeningForegroundService.EXTRA_WAKE_WORD, word.trim())
                }
                try { context.startService(updateIntent) } catch (_: Exception) {}
            }
            showStatusBanner("✅ Active Wake-Word saved: \"$word\"")
        }
    }

    fun setHandsFreeMode(enabled: Boolean) {
        // Persist the mode, but let the microphone foreground service own continuous
        // recognition. A ViewModel-owned SpeechRecognizer dies with the app task.
        voiceEngine.setHandsFreeModeState(enabled)
        val serviceIntent = Intent(context, VoiceListeningForegroundService::class.java).apply {
            action = if (enabled) VoiceListeningForegroundService.ACTION_START
                     else VoiceListeningForegroundService.ACTION_STOP
        }
        try {
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(context)) {
                val overlaySettings = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(overlaySettings)
            }
            showStatusBanner(
                if (enabled) "👂 Background voice service starting: say \"${voiceEngine.wakeWord.value}\" anytime!"
                else "Hands-Free Voice Engine stopped"
            )
        } catch (error: Exception) {
            voiceEngine.setHandsFreeModeState(false)
            showStatusBanner("⚠️ Could not start background voice service: ${error.message ?: "permission required"}")
        }
    }

    fun setAudioChimeEnabled(enabled: Boolean) {
        voiceEngine.setAudioChimeEnabled(enabled)
    }

    fun testChime() {
        voiceEngine.playChime(com.example.service.ChimeFeedback.WAKE_DETECTED)
        showStatusBanner("🔔 Earbuds test chime played!")
    }

    fun setSelectedGenre(genre: String) {
        _selectedGenre.value = genre
    }

    fun updateBubblePosition(deltaX: Float, deltaY: Float) {
        _bubbleOffsetX.value += deltaX
        _bubbleOffsetY.value += deltaY
    }

    fun toggleBubbleVisibility() {
        _isBubbleVisible.value = !_isBubbleVisible.value
    }

    fun showStatusBanner(message: String) {
        _statusBanner.value = message
        viewModelScope.launch {
            delay(3500)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }

    fun checkStoragePermissionState() {
        _hasStoragePermission.value = LocalMusicScanner.hasStoragePermission(context)
    }

    fun scanLocalMusic() {
        viewModelScope.launch {
            _isScanningLocal.value = true
            checkStoragePermissionState()
            val scanned = localScanner.scanLocalTracks()
            if (scanned.isNotEmpty()) {
                _localTracks.value = scanned + DefaultTracks.youtubeTracks
            }
            _isScanningLocal.value = false
            if (_hasStoragePermission.value) {
                showStatusBanner("📂 Scanned ${scanned.size} local storage audio files")
            }
        }
    }

    fun addPickedAudioUris(uris: List<Uri>) {
        viewModelScope.launch {
            val added = mutableListOf<Track>()
            uris.forEach { uri ->
                val track = localScanner.parsePickedAudioUri(uri)
                if (track != null) {
                    added.add(track)
                }
            }
            if (added.isNotEmpty()) {
                _localTracks.value = added + _localTracks.value
                showStatusBanner("✅ Added ${added.size} audio files from device!")
                playTrackFromList(added.first(), _localTracks.value)
            }
        }
    }

    fun playTrackFromList(track: Track, fromList: List<Track>) {
        val index = fromList.indexOf(track).coerceAtLeast(0)
        playerManager.setQueue(fromList, startIndex = index, autoPlay = true)
        showStatusBanner("▶ Now Playing: ${track.title}")
    }

    fun dispatchVoiceCommandFromService(command: VoiceCommand, rawText: String) {
        handleVoiceCommand(command, rawText)
    }

    private fun handleVoiceCommand(command: VoiceCommand, rawText: String) {
        val mode = externalController.targetMode()
        if (mode != "akki_beat") {
            val handled = when (command) {
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
            if (handled) {
                showStatusBanner("🎯 Executed on external player: $rawText")
            } else {
                showStatusBanner("⚠️ No active external player found. Start music in your player app.")
            }
            return
        }

        when (command) {
            is VoiceCommand.Play -> {
                playerManager.resume()
                showStatusBanner("▶ Resumed playback (Akki Beat)")
            }
            is VoiceCommand.Pause -> {
                playerManager.pause()
                showStatusBanner("⏸ Playback paused")
            }
            is VoiceCommand.TogglePlayPause -> {
                playerManager.togglePlayPause()
                showStatusBanner("⏯ Toggled play/pause")
            }
            is VoiceCommand.NextTrack -> {
                playerManager.skipToNext()
                showStatusBanner("⏭ Skipped to next track")
            }
            is VoiceCommand.PreviousTrack -> {
                playerManager.skipToPrevious()
                showStatusBanner("⏮ Previous track")
            }
            is VoiceCommand.VolumeUp -> {
                playerManager.adjustVolumeRelative(+0.10f)
                val currentVol = (playerManager.playerState.value.volume * 100).toInt()
                showStatusBanner("🔊 Volume increased: $currentVol%")
            }
            is VoiceCommand.VolumeDown -> {
                playerManager.adjustVolumeRelative(-0.10f)
                val currentVol = (playerManager.playerState.value.volume * 100).toInt()
                showStatusBanner("🔉 Volume decreased: $currentVol%")
            }
            is VoiceCommand.Mute -> {
                playerManager.toggleMute()
                showStatusBanner("🔇 Muted")
            }
            is VoiceCommand.Unmute -> {
                playerManager.toggleMute()
                showStatusBanner("🔊 Unmuted")
            }
            is VoiceCommand.SetVolumeLevel -> {
                playerManager.setVolumePercent(command.percentage)
                showStatusBanner("🔊 Volume set to ${command.percentage}%")
            }
            is VoiceCommand.BoostBass -> {
                playerManager.applyPreset("Bass Boost")
                showStatusBanner("⚡ Bass Boost preset activated!")
            }
            is VoiceCommand.Shuffle -> {
                playerManager.toggleShuffle()
                val isShuffle = playerManager.playerState.value.isShuffle
                showStatusBanner(if (isShuffle) "🔀 Shuffle On" else "➡️ Shuffle Off")
            }
            is VoiceCommand.PlayGenre -> {
                val matching = _localTracks.value.filter { it.genre.equals(command.genre, ignoreCase = true) }
                if (matching.isNotEmpty()) {
                    playerManager.setQueue(matching, startIndex = 0, autoPlay = true)
                    showStatusBanner("🎵 Playing ${command.genre} music")
                } else {
                    showStatusBanner("No tracks found for genre ${command.genre}")
                }
            }
            is VoiceCommand.SearchPlay -> {
                val matching = _localTracks.value.filter {
                    it.title.contains(command.query, ignoreCase = true) ||
                            it.artist.contains(command.query, ignoreCase = true)
                }
                if (matching.isNotEmpty()) {
                    playTrackFromList(matching.first(), matching)
                    showStatusBanner("▶ Playing '${matching.first().title}'")
                } else {
                    showStatusBanner("No local track found matching '${command.query}'")
                }
            }
            is VoiceCommand.WakeWordOnly -> {
                showStatusBanner("✨ Wake-word recognized! Speak command...")
            }
            is VoiceCommand.Unknown -> {
                showStatusBanner("Command not recognized: '$rawText'")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The Application owns the player so the foreground voice service can
        // continue controlling it after MainActivity leaves Recents.
        voiceEngine.onDestroy()
        hybridSensorManager.unregisterListener()
    }
}
