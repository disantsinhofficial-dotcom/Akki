package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.DefaultTracks
import com.example.model.Track
import com.example.service.AudioPlayerManager
import com.example.service.HybridSensorManager
import com.example.service.LocalMusicScanner
import com.example.service.VoiceCommand
import com.example.service.VoiceEngine
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

enum class LibrarySubTab {
    ONLINE, LOCAL
}

class VoiceBeatsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val playerManager = AudioPlayerManager(context)
    val localScanner = LocalMusicScanner(context)

    lateinit var voiceEngine: VoiceEngine
    lateinit var hybridSensorManager: HybridSensorManager

    private val _currentTab = MutableStateFlow(AppTab.PLAYER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _librarySubTab = MutableStateFlow(LibrarySubTab.ONLINE)
    val librarySubTab: StateFlow<LibrarySubTab> = _librarySubTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    private val _onlineTracks = MutableStateFlow<List<Track>>(DefaultTracks.onlineTracks)
    val onlineTracks: StateFlow<List<Track>> = _onlineTracks.asStateFlow()

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

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

    // Filtered online tracks based on search & genre
    val filteredOnlineTracks: StateFlow<List<Track>> = combine(
        _onlineTracks,
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultTracks.onlineTracks)

    // Filtered local tracks
    val filteredLocalTracks: StateFlow<List<Track>> = combine(
        _localTracks,
        _searchQuery
    ) { tracks, query ->
        tracks.filter { track ->
            query.isBlank() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val genres = listOf("All", "Synthwave", "Lo-Fi", "Bass Boost", "Ambient", "Electronic", "Pop")

    init {
        // Initialize Voice Engine
        voiceEngine = VoiceEngine(context) { command, rawText ->
            handleVoiceCommand(command, rawText)
        }

        // Initialize Hybrid Sensor Manager
        hybridSensorManager = HybridSensorManager(context) {
            // On Shake detected: start voice listening!
            showStatusBanner("📳 Shake detected! Listening for command...")
            voiceEngine.startListening()
        }

        // Set initial queue
        playerManager.setQueue(DefaultTracks.onlineTracks, startIndex = 0, autoPlay = false)

        // Initial scan of local storage
        scanLocalMusic()
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectLibrarySubTab(subTab: LibrarySubTab) {
        _librarySubTab.value = subTab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
            kotlinx.coroutines.delay(3500)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }

    fun scanLocalMusic() {
        viewModelScope.launch {
            _isScanningLocal.value = true
            val scanned = localScanner.scanLocalTracks()
            _localTracks.value = scanned
            _isScanningLocal.value = false
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
                playerManager.resume()
                showStatusBanner("▶ Resumed playback")
            }
            is VoiceCommand.Pause -> {
                playerManager.pause()
                showStatusBanner("⏸ Playback paused")
            }
            is VoiceCommand.TogglePlayPause -> {
                playerManager.togglePlayPause()
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
                val matching = _onlineTracks.value.filter { it.genre.equals(command.genre, ignoreCase = true) }
                if (matching.isNotEmpty()) {
                    playerManager.setQueue(matching, startIndex = 0, autoPlay = true)
                    showStatusBanner("🎵 Playing ${command.genre} stream")
                } else {
                    showStatusBanner("Could not find tracks in ${command.genre}")
                }
            }
            is VoiceCommand.SearchPlay -> {
                val found = (_onlineTracks.value + _localTracks.value).firstOrNull {
                    it.title.contains(command.query, ignoreCase = true) ||
                            it.artist.contains(command.query, ignoreCase = true)
                }
                if (found != null) {
                    playerManager.playTrack(found)
                    showStatusBanner("🎵 Playing '${found.title}'")
                } else {
                    showStatusBanner("No track found matching '${command.query}'")
                }
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
