package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DefaultTracks
import com.example.model.Track
import com.example.service.AudioPlayerManager
import com.example.service.HybridSensorManager
import com.example.service.LocalMusicScanner
import com.example.service.ExternalMusicController
import com.example.service.InstalledMusicPlayer
import com.example.service.VoiceCommand
import com.example.service.VoiceEngine
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

    val playerManager = AudioPlayerManager(context)
    val localScanner = LocalMusicScanner(context)
    private val externalMusicController = ExternalMusicController(context)
    private val _installedMusicPlayers = MutableStateFlow<List<InstalledMusicPlayer>>(emptyList())
    val installedMusicPlayers: StateFlow<List<InstalledMusicPlayer>> = _installedMusicPlayers.asStateFlow()
    private val _isAutoDetectMusicPlayer = MutableStateFlow(externalMusicController.isAutoDetect())
    val isAutoDetectMusicPlayer: StateFlow<Boolean> = _isAutoDetectMusicPlayer.asStateFlow()
    private val _selectedMusicPlayerPackage = MutableStateFlow(externalMusicController.selectedPackage())
    val selectedMusicPlayerPackage: StateFlow<String?> = _selectedMusicPlayerPackage.asStateFlow()

    lateinit var voiceEngine: VoiceEngine
    lateinit var hybridSensorManager: HybridSensorManager

    private val _currentTab = MutableStateFlow(AppTab.PLAYER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

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

        refreshMusicPlayers()

        // Initial queue: start with default demo tracks
        playerManager.setQueue(DefaultTracks.youtubeTracks, startIndex = 0, autoPlay = false)

        // Initial scan of local storage
        checkStoragePermissionState()
        scanLocalMusic()
    }

    fun refreshMusicPlayers() { _installedMusicPlayers.value = externalMusicController.installedPlayers(); _selectedMusicPlayerPackage.value = externalMusicController.selectedPackage(); _isAutoDetectMusicPlayer.value = externalMusicController.isAutoDetect() }
    fun setAutoDetectMusicPlayer(enabled: Boolean) { externalMusicController.setAutoDetect(enabled); _isAutoDetectMusicPlayer.value = enabled; showStatusBanner(if (enabled) "Auto Detect enabled" else "Manual player selection enabled") }
    fun selectMusicPlayer(packageName: String) { externalMusicController.selectPackage(packageName); externalMusicController.setAutoDetect(false); _selectedMusicPlayerPackage.value = packageName; _isAutoDetectMusicPlayer.value = false; val label = _installedMusicPlayers.value.firstOrNull { it.packageName == packageName }?.label ?: packageName; showStatusBanner("Selected music player: $label") }
    private fun sendExternalMediaCommand(action: String): Boolean { val sent = externalMusicController.send(action); if (!sent && !_isAutoDetectMusicPlayer.value) showStatusBanner("Selected player is not active. Start playback and enable Notification Access."); return sent }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setWakeWord(word: String) {
        val success = voiceEngine.setWakeWord(word)
        if (success) {
            showStatusBanner("✅ Active Wake-Word saved: \"$word\"")
        }
    }

    fun setHandsFreeMode(enabled: Boolean) {
        voiceEngine.setHandsFreeMode(enabled)
        showStatusBanner(
            if (enabled) "👂 Hands-Free Earbuds Mode Active: say \"${voiceEngine.wakeWord.value}\" anytime!"
            else "Hands-Free Earbuds Mode turned off"
        )
    }

    fun setAudioChimeEnabled(enabled: Boolean) {
        voiceEngine.setAudioChimeEnabled(enabled)
        showStatusBanner(if (enabled) "🔔 Earbuds chime enabled" else "🔕 Earbuds chime disabled")
    }

    fun testChime() {
        voiceEngine.playChime(com.example.service.ChimeFeedback.WAKE_DETECTED)
        showStatusBanner("🔕 App chimes are disabled; system speech-service sounds may still occur.")
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

    private fun handleVoiceCommand(command: VoiceCommand, rawText: String) {
        when (command) {
            is VoiceCommand.Play -> {
                if (!sendExternalMediaCommand("play") && _isAutoDetectMusicPlayer.value) playerManager.resume()
                showStatusBanner("▶ Resumed playback (External player / Local)")
            }
            is VoiceCommand.Pause -> {
                if (!sendExternalMediaCommand("pause") && _isAutoDetectMusicPlayer.value) playerManager.pause()
                showStatusBanner("⏸ Playback paused")
            }
            is VoiceCommand.TogglePlayPause -> {
                if (!sendExternalMediaCommand("toggle") && _isAutoDetectMusicPlayer.value) playerManager.togglePlayPause()
                showStatusBanner("⏯ Toggled play/pause")
            }
            is VoiceCommand.NextTrack -> {
                if (!sendExternalMediaCommand("next") && _isAutoDetectMusicPlayer.value) playerManager.skipToNext()
                showStatusBanner("⏭ Skipped to next track")
            }
            is VoiceCommand.PreviousTrack -> {
                if (!sendExternalMediaCommand("previous") && _isAutoDetectMusicPlayer.value) playerManager.skipToPrevious()
                showStatusBanner("⏮ Previous track")
            }
            is VoiceCommand.VolumeUp -> {
                if (!externalMusicController.adjustVolume(true)) playerManager.adjustVolumeRelative(+0.10f)
                val currentVol = (playerManager.playerState.value.volume * 100).toInt()
                showStatusBanner("🔊 Volume increased: $currentVol%")
            }
            is VoiceCommand.VolumeDown -> {
                if (!externalMusicController.adjustVolume(false)) playerManager.adjustVolumeRelative(-0.10f)
                val currentVol = (playerManager.playerState.value.volume * 100).toInt()
                showStatusBanner("🔉 Volume decreased: $currentVol%")
            }
            is VoiceCommand.Mute -> {
                if (!externalMusicController.setMuted(true)) playerManager.toggleMute()
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
        playerManager.onDestroy()
        voiceEngine.onDestroy()
        hybridSensorManager.unregisterListener()
    }
}
