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
import com.example.service.VoiceCommand
import com.example.service.VoiceEngine
import com.example.service.YouTubeSearchService
import kotlinx.coroutines.Job
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

enum class LibrarySubTab {
    YOUTUBE, LOCAL, STREAMS
}

class VoiceBeatsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val playerManager = AudioPlayerManager(context)
    val localScanner = LocalMusicScanner(context)
    val youtubeSearchService = YouTubeSearchService()

    lateinit var voiceEngine: VoiceEngine
    lateinit var hybridSensorManager: HybridSensorManager

    private val _currentTab = MutableStateFlow(AppTab.PLAYER)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    private val _librarySubTab = MutableStateFlow(LibrarySubTab.YOUTUBE)
    val librarySubTab: StateFlow<LibrarySubTab> = _librarySubTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGenre = MutableStateFlow("All")
    val selectedGenre: StateFlow<String> = _selectedGenre.asStateFlow()

    // YouTube Music Tracks
    private val _youtubeTracks = MutableStateFlow<List<Track>>(DefaultTracks.youtubeTracks)
    val youtubeTracks: StateFlow<List<Track>> = _youtubeTracks.asStateFlow()

    private val _isSearchingYouTube = MutableStateFlow(false)
    val isSearchingYouTube: StateFlow<Boolean> = _isSearchingYouTube.asStateFlow()

    // Radio & Public Stream Tracks
    private val _streamTracks = MutableStateFlow<List<Track>>(DefaultTracks.onlineTracks)
    val streamTracks: StateFlow<List<Track>> = _streamTracks.asStateFlow()

    // Local Storage Tracks
    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
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

    private var searchJob: Job? = null

    // Filtered YouTube tracks
    val filteredYouTubeTracks: StateFlow<List<Track>> = combine(
        _youtubeTracks,
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

    // Filtered stream tracks
    val filteredStreamTracks: StateFlow<List<Track>> = combine(
        _streamTracks,
        _searchQuery,
        _selectedGenre
    ) { tracks, query, genre ->
        tracks.filter { track ->
            val matchesGenre = genre == "All" || track.genre.equals(genre, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true)
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

    val youtubeGenres = listOf("All", "Bollywood", "Punjabi", "Pop", "Lo-Fi", "Acoustic")
    val streamGenres = listOf("All", "Synthwave", "Lo-Fi", "Bass Boost", "Ambient", "Electronic")

    init {
        // Initialize Voice Engine
        voiceEngine = VoiceEngine(context) { command, rawText ->
            handleVoiceCommand(command, rawText)
        }

        // Initialize Hybrid Sensor Manager
        hybridSensorManager = HybridSensorManager(context) {
            showStatusBanner("📳 Shake detected! Listening for command...")
            voiceEngine.startListening()
        }

        // Initial queue: start with YouTube trending tracks
        playerManager.setQueue(DefaultTracks.youtubeTracks, startIndex = 0, autoPlay = false)

        // Initial scan of local storage
        checkStoragePermissionState()
        scanLocalMusic()
    }

    fun selectTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun selectLibrarySubTab(subTab: LibrarySubTab) {
        _librarySubTab.value = subTab
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (_librarySubTab.value == LibrarySubTab.YOUTUBE && query.length >= 3) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                delay(600) // Debounce
                performYouTubeSearch(query)
            }
        }
    }

    fun performYouTubeSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingYouTube.value = true
            val results = youtubeSearchService.search(query)
            if (results.isNotEmpty()) {
                _youtubeTracks.value = results
            }
            _isSearchingYouTube.value = false
        }
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
            _localTracks.value = scanned
            _isScanningLocal.value = false
            if (_hasStoragePermission.value) {
                showStatusBanner("📂 Scanned ${scanned.size} local storage audio files")
            }
        }
    }

    /**
     * Adds songs picked through the Android File Picker (Storage Access Framework)
     */
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
                _localTracks.value = added + _localTracks.value.filter { existing ->
                    added.none { it.sourceUrl == existing.sourceUrl }
                }
                showStatusBanner("✅ Added ${added.size} audio files from device!")
                // Auto play the first selected file
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
                val matching = _youtubeTracks.value.filter { it.genre.equals(command.genre, ignoreCase = true) }
                    .ifEmpty { _streamTracks.value.filter { it.genre.equals(command.genre, ignoreCase = true) } }
                if (matching.isNotEmpty()) {
                    playerManager.setQueue(matching, startIndex = 0, autoPlay = true)
                    showStatusBanner("🎵 Playing ${command.genre} music")
                } else {
                    showStatusBanner("Searching for ${command.genre}...")
                    performYouTubeSearch(command.genre)
                }
            }
            is VoiceCommand.SearchPlay -> {
                viewModelScope.launch {
                    showStatusBanner("🔍 Searching YouTube for '${command.query}'...")
                    val results = youtubeSearchService.search(command.query)
                    if (results.isNotEmpty()) {
                        _youtubeTracks.value = results
                        playTrackFromList(results.first(), results)
                    } else {
                        showStatusBanner("No track found matching '${command.query}'")
                    }
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
