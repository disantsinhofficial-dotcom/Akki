package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.util.Log
import com.example.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

enum class RepeatMode {
    OFF, ALL, ONE
}

data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val minGainMb: Short,
    val maxGainMb: Short,
    val currentGainMb: Short
)

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val bassBoostStrength: Short = 450, // 0 to 1000
    val virtualizerStrength: Short = 300, // 0 to 1000
    val equalizerEnabled: Boolean = true,
    val activePreset: String = "Bass Boost"
)

class AudioPlayerManager(private val context: Context) {

    private val tag = "AudioPlayerManager"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var mediaPlayer: MediaPlayer? = null
    val youTubePlayer = YouTubePlayerManager(context)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // 16-band visualizer simulation values for animated glowing ring
    private val _visualizerBands = MutableStateFlow(List(16) { 0.15f })
    val visualizerBands: StateFlow<List<Float>> = _visualizerBands.asStateFlow()

    private val _bands = MutableStateFlow<List<EqualizerBand>>(emptyList())
    val bands: StateFlow<List<EqualizerBand>> = _bands.asStateFlow()

    private var progressJob: Job? = null
    private var visualizerJob: Job? = null

    val availablePresets = listOf(
        "Bass Boost",
        "Rock",
        "Electronic",
        "Pop",
        "Vocal",
        "Acoustic",
        "Flat"
    )

    init {
        // Sync with real system audio volume
        syncVolumeWithSystem()

        // Configure YouTube player callbacks
        youTubePlayer.onPlayStateChanged = { isPlaying ->
            _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
        }
        youTubePlayer.onBufferingChanged = { isBuffering ->
            _playerState.value = _playerState.value.copy(isBuffering = isBuffering)
        }
        youTubePlayer.onTimeUpdated = { currentMs, durationMs ->
            _playerState.value = _playerState.value.copy(
                currentPositionMs = currentMs,
                durationMs = if (durationMs > 0) durationMs else _playerState.value.durationMs
            )
        }
        youTubePlayer.onTrackCompleted = {
            handleTrackCompletion()
        }

        startProgressTracking()
        startVisualizerSimulation()
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.lastIndex)
        _playerState.value = _playerState.value.copy(
            queue = tracks,
            queueIndex = validIndex
        )
        if (autoPlay) {
            playTrack(tracks[validIndex])
        }
    }

    fun playTrack(track: Track) {
        // If track is a YouTube track
        if (track.isYouTube) {
            releasePlayer()
            _playerState.value = _playerState.value.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = track.durationMs
            )
            val videoId = if (!track.youtubeVideoId.isNullOrBlank()) {
                track.youtubeVideoId!!
            } else {
                getVerifiedVideoId(track.title + " " + track.artist)
            }
            youTubePlayer.play(videoId)
            applyVolume(_playerState.value.volume)
            return
        }

        // Local storage or direct MP3 audio stream
        try {
            youTubePlayer.pause()
            releasePlayer()
            _playerState.value = _playerState.value.copy(
                currentTrack = track,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = track.durationMs
            )

            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Handle content:// URIs from MediaStore and SAF File Picker
                val uri = Uri.parse(track.sourceUrl)
                if (uri.scheme == "content" || uri.scheme == "android.resource") {
                    setDataSource(context, uri)
                } else {
                    setDataSource(track.sourceUrl)
                }

                setOnPreparedListener { player ->
                    _playerState.value = _playerState.value.copy(
                        isBuffering = false,
                        isPlaying = true,
                        durationMs = player.duration.toLong().takeIf { it > 0 } ?: track.durationMs
                    )
                    applyVolume(_playerState.value.volume)
                    player.start()
                    initAudioEffects(player.audioSessionId)
                }
                setOnCompletionListener {
                    handleTrackCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra")
                    _playerState.value = _playerState.value.copy(
                        isBuffering = false,
                        isPlaying = false
                    )
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (e: Exception) {
            Log.e(tag, "Error setting up MediaPlayer: ${e.message}")
            _playerState.value = _playerState.value.copy(
                isBuffering = false,
                isPlaying = false
            )
        }
    }

    fun togglePlayPause() {
        val current = _playerState.value.currentTrack
        if (current?.isYouTube == true) {
            if (_playerState.value.isPlaying) {
                youTubePlayer.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
            } else {
                youTubePlayer.resume()
                _playerState.value = _playerState.value.copy(isPlaying = true)
            }
            return
        }

        val mp = mediaPlayer
        if (mp != null) {
            if (mp.isPlaying) {
                mp.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
            } else {
                mp.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
            }
        } else {
            val track = _playerState.value.currentTrack
                ?: _playerState.value.queue.getOrNull(_playerState.value.queueIndex)
            if (track != null) {
                playTrack(track)
            }
        }
    }

    fun pause() {
        if (_playerState.value.currentTrack?.isYouTube == true) {
            youTubePlayer.pause()
            _playerState.value = _playerState.value.copy(isPlaying = false)
            return
        }
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playerState.value = _playerState.value.copy(isPlaying = false)
            }
        }
    }

    fun resume() {
        if (_playerState.value.currentTrack?.isYouTube == true) {
            youTubePlayer.resume()
            _playerState.value = _playerState.value.copy(isPlaying = true)
            return
        }
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _playerState.value = _playerState.value.copy(isPlaying = true)
            }
        } ?: run {
            _playerState.value.currentTrack?.let { playTrack(it) }
        }
    }

    fun skipToNext() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return

        val nextIndex = if (state.isShuffle) {
            Random.nextInt(state.queue.size)
        } else {
            (state.queueIndex + 1) % state.queue.size
        }
        _playerState.value = state.copy(queueIndex = nextIndex)
        playTrack(state.queue[nextIndex])
    }

    fun skipToPrevious() {
        val state = _playerState.value
        if (state.queue.isEmpty()) return

        // If played more than 3 seconds, replay track
        if (state.currentTrack?.isYouTube == true) {
            if (state.currentPositionMs > 3000) {
                seekTo(0)
                return
            }
        } else {
            val mp = mediaPlayer
            if (mp != null && mp.currentPosition > 3000) {
                mp.seekTo(0)
                return
            }
        }

        val prevIndex = if (state.queueIndex > 0) state.queueIndex - 1 else state.queue.lastIndex
        _playerState.value = state.copy(queueIndex = prevIndex)
        playTrack(state.queue[prevIndex])
    }

    fun seekTo(positionMs: Long) {
        if (_playerState.value.currentTrack?.isYouTube == true) {
            youTubePlayer.seekTo(positionMs)
            _playerState.value = _playerState.value.copy(currentPositionMs = positionMs)
            return
        }
        mediaPlayer?.let {
            val safePos = positionMs.coerceIn(0, it.duration.toLong()).toInt()
            it.seekTo(safePos)
            _playerState.value = _playerState.value.copy(currentPositionMs = safePos.toLong())
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIndex = (clamped * maxVol).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        } catch (e: Exception) {
            Log.w(tag, "Failed to set system stream volume: ${e.message}")
        }
        _playerState.value = _playerState.value.copy(volume = clamped, isMuted = clamped == 0f)
        applyVolume(clamped)
    }

    fun adjustVolumeRelative(delta: Float) {
        try {
            val direction = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            syncVolumeWithSystem()
        } catch (e: Exception) {
            Log.w(tag, "Failed to adjust system stream volume: ${e.message}")
            val current = _playerState.value.volume
            val newVol = (current + delta).coerceIn(0f, 1f)
            setVolume(newVol)
        }
    }

    fun syncVolumeWithSystem() {
        try {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val fraction = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
            _playerState.value = _playerState.value.copy(volume = fraction, isMuted = curVol == 0)
            applyVolume(fraction)
        } catch (e: Exception) {
            Log.w(tag, "Failed to sync system volume: ${e.message}")
        }
    }

    fun toggleMute() {
        try {
            val state = _playerState.value
            if (state.isMuted) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                syncVolumeWithSystem()
                if (_playerState.value.volume <= 0.05f) {
                    setVolume(0.5f)
                }
            } else {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                _playerState.value = state.copy(isMuted = true, volume = 0f)
                applyVolume(0f)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to toggle mute on system stream: ${e.message}")
        }
    }

    fun toggleShuffle() {
        _playerState.value = _playerState.value.copy(isShuffle = !_playerState.value.isShuffle)
    }

    fun cycleRepeatMode() {
        val next = when (_playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _playerState.value = _playerState.value.copy(repeatMode = next)
    }

    private fun handleTrackCompletion() {
        val state = _playerState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                state.currentTrack?.let { playTrack(it) }
            }
            RepeatMode.ALL -> {
                skipToNext()
            }
            RepeatMode.OFF -> {
                if (state.queueIndex < state.queue.lastIndex) {
                    skipToNext()
                } else {
                    _playerState.value = state.copy(isPlaying = false, currentPositionMs = 0L)
                }
            }
        }
    }

    private fun applyVolume(vol: Float) {
        try {
            val effective = if (_playerState.value.isMuted) 0f else vol
            if (_playerState.value.currentTrack?.isYouTube == true) {
                youTubePlayer.setVolume(effective)
            } else {
                mediaPlayer?.setVolume(effective, effective)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set volume: ${e.message}")
        }
    }

    // Audio effects setup (Equalizer, BassBoost, Virtualizer)
    private fun initAudioEffects(audioSessionId: Int) {
        try {
            releaseAudioEffects()
            if (audioSessionId != 0) {
                equalizer = Equalizer(0, audioSessionId).apply {
                    enabled = _playerState.value.equalizerEnabled
                }
                bassBoost = BassBoost(0, audioSessionId).apply {
                    enabled = _playerState.value.equalizerEnabled
                    if (strengthSupported) {
                        setStrength(_playerState.value.bassBoostStrength)
                    }
                }
                virtualizer = Virtualizer(0, audioSessionId).apply {
                    enabled = _playerState.value.equalizerEnabled
                    if (strengthSupported) {
                        setStrength(_playerState.value.virtualizerStrength)
                    }
                }
                loadEqualizerBands()
                applyPreset(_playerState.value.activePreset)
            }
        } catch (e: Exception) {
            Log.w(tag, "Audio effects init error (running in fallback mode): ${e.message}")
            fallbackBands()
        }
    }

    private fun loadEqualizerBands() {
        val eq = equalizer ?: run {
            fallbackBands()
            return
        }
        try {
            val numBands = eq.numberOfBands
            val minRange = eq.bandLevelRange[0]
            val maxRange = eq.bandLevelRange[1]
            val bandList = mutableListOf<EqualizerBand>()

            for (i in 0 until numBands) {
                val index = i.toShort()
                val freq = eq.getCenterFreq(index) / 1000 // in Hz
                val currentGain = eq.getBandLevel(index)
                bandList.add(
                    EqualizerBand(
                        index = index,
                        centerFreqHz = freq,
                        minGainMb = minRange,
                        maxGainMb = maxRange,
                        currentGainMb = currentGain
                    )
                )
            }
            _bands.value = bandList
        } catch (e: Exception) {
            fallbackBands()
        }
    }

    private fun fallbackBands() {
        val defaultFreqs = listOf(60, 230, 910, 3600, 14000)
        _bands.value = defaultFreqs.mapIndexed { index, freq ->
            EqualizerBand(
                index = index.toShort(),
                centerFreqHz = freq,
                minGainMb = -1200,
                maxGainMb = 1200,
                currentGainMb = 0
            )
        }
    }

    fun setBandGain(bandIndex: Short, gainMb: Short) {
        try {
            equalizer?.setBandLevel(bandIndex, gainMb)
        } catch (e: Exception) {
            Log.w(tag, "Could not set band gain on hardware: ${e.message}")
        }
        _bands.value = _bands.value.map { band ->
            if (band.index == bandIndex) band.copy(currentGainMb = gainMb) else band
        }
    }

    fun setBassBoostStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _playerState.value = _playerState.value.copy(bassBoostStrength = clamped)
        try {
            if (bassBoost?.strengthSupported == true) {
                bassBoost?.setStrength(clamped)
            }
        } catch (e: Exception) {
            Log.w(tag, "BassBoost set error: ${e.message}")
        }
    }

    fun setVirtualizerStrength(strength: Short) {
        val clamped = strength.coerceIn(0, 1000)
        _playerState.value = _playerState.value.copy(virtualizerStrength = clamped)
        try {
            if (virtualizer?.strengthSupported == true) {
                virtualizer?.setStrength(clamped)
            }
        } catch (e: Exception) {
            Log.w(tag, "Virtualizer set error: ${e.message}")
        }
    }

    fun applyPreset(presetName: String) {
        _playerState.value = _playerState.value.copy(activePreset = presetName)
        val gains: List<Short> = when (presetName) {
            "Bass Boost" -> {
                setBassBoostStrength(850)
                listOf(800.toShort(), 600.toShort(), 0.toShort(), (-200).toShort(), (-300).toShort())
            }
            "Rock" -> {
                setBassBoostStrength(600)
                listOf(500.toShort(), 300.toShort(), (-100).toShort(), 400.toShort(), 600.toShort())
            }
            "Electronic" -> {
                setBassBoostStrength(700)
                listOf(600.toShort(), 400.toShort(), 0.toShort(), 300.toShort(), 500.toShort())
            }
            "Pop" -> {
                setBassBoostStrength(400)
                listOf((-200).toShort(), 300.toShort(), 500.toShort(), 300.toShort(), (-100).toShort())
            }
            "Vocal" -> {
                setBassBoostStrength(200)
                listOf((-300).toShort(), 0.toShort(), 700.toShort(), 500.toShort(), 100.toShort())
            }
            "Acoustic" -> {
                setBassBoostStrength(300)
                listOf(300.toShort(), 200.toShort(), 100.toShort(), 200.toShort(), 300.toShort())
            }
            else -> { // Flat
                setBassBoostStrength(0)
                listOf(0.toShort(), 0.toShort(), 0.toShort(), 0.toShort(), 0.toShort())
            }
        }

        gains.forEachIndexed { i, gain ->
            if (i < _bands.value.size) {
                setBandGain(_bands.value[i].index, gain)
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val currentTrack = _playerState.value.currentTrack
                if (currentTrack != null && !currentTrack.isYouTube) {
                    mediaPlayer?.let { mp ->
                        if (mp.isPlaying) {
                            val current = mp.currentPosition.toLong()
                            val duration = mp.duration.toLong().takeIf { it > 0 }
                                ?: _playerState.value.durationMs
                            _playerState.value = _playerState.value.copy(
                                currentPositionMs = current,
                                durationMs = duration
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun startVisualizerSimulation() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var step = 0.0
            while (isActive) {
                step += 0.25
                val isPlaying = _playerState.value.isPlaying
                val bassFactor = (_playerState.value.bassBoostStrength / 1000f) * 0.4f

                val newBands = List(16) { i ->
                    if (isPlaying) {
                        val base = (sin(step + i * 0.45) * 0.4f + 0.5f).toFloat()
                        val jitter = (Random.nextFloat() * 0.25f)
                        val bassBoostExtra = if (i < 4) bassFactor else 0f
                        (base + jitter + bassBoostExtra).coerceIn(0.12f, 1.0f)
                    } else {
                        0.12f
                    }
                }
                _visualizerBands.value = newBands
                delay(70)
            }
        }
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    private fun releasePlayer() {
        try {
            releaseAudioEffects()
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    fun setVolumePercent(percent: Int) {
        setVolume(percent.coerceIn(0, 100).toFloat() / 100f)
    }

    private fun getVerifiedVideoId(query: String): String {
        val q = query.lowercase(java.util.Locale.ROOT)
        return when {
            q.contains("kesariya") -> "BddP6PYo2gs"
            q.contains("tum hi ho") -> "Umqb9KENgmk"
            q.contains("brown munde") -> "VlvA-EWcjBc"
            q.contains("excuses") -> "kz417ioGyv0"
            q.contains("raataan") -> "gYqv402NbpQ"
            q.contains("punjabi") -> "VlvA-EWcjBc"
            q.contains("lofi") || q.contains("lo-fi") -> "jfKfPfyJRdk"
            q.contains("bollywood") -> "BddP6PYo2gs"
            q.contains("rock") -> "9bZkp7q19f0"
            q.contains("pop") -> "5qap5aO4i9A"
            else -> "BddP6PYo2gs"
        }
    }

    fun onDestroy() {
        progressJob?.cancel()
        visualizerJob?.cancel()
        releasePlayer()
        youTubePlayer.onDestroy()
    }
}
