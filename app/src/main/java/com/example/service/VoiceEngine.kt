package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

sealed class VoiceCommand {
    object Play : VoiceCommand()
    object Pause : VoiceCommand()
    object TogglePlayPause : VoiceCommand()
    object NextTrack : VoiceCommand()
    object PreviousTrack : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    object Mute : VoiceCommand()
    object Unmute : VoiceCommand()
    object BoostBass : VoiceCommand()
    object Shuffle : VoiceCommand()
    data class PlayGenre(val genre: String) : VoiceCommand()
    data class SearchPlay(val query: String) : VoiceCommand()
    data class Unknown(val rawText: String) : VoiceCommand()
}

sealed class VoiceState {
    object Idle : VoiceState()
    data class Listening(val wakeWordDetected: Boolean = false, val rmsDb: Float = 0f) : VoiceState()
    data class Processing(val transcript: String) : VoiceState()
    data class Executed(val command: VoiceCommand, val rawQuery: String, val message: String) : VoiceState()
    data class Error(val message: String) : VoiceState()
}

class VoiceEngine(
    private val context: Context,
    private val onCommandReceived: (VoiceCommand, String) -> Unit
) {
    private val tag = "VoiceEngine"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningContinuous = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    private val _wakeWord = MutableStateFlow("Akki Beat") // Default custom wake word
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    // Configurable wake word presets
    val wakeWordPresets = listOf("Akki Beat", "Hey Akki", "Suno", "Hi Genie", "Hey Buddy")

    init {
        initSpeechRecognizer()
    }

    fun setWakeWord(word: String) {
        if (word.isNotBlank()) {
            _wakeWord.value = word.trim()
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            _isAvailable.value = true
            setupRecognizer()
        } else {
            _isAvailable.value = false
            Log.w(tag, "Speech recognition is not supported on this device.")
        }
    }

    private fun setupRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(tag, "onReadyForSpeech")
                        _voiceState.value = VoiceState.Listening(wakeWordDetected = false, rmsDb = 0f)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(tag, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val current = _voiceState.value
                        if (current is VoiceState.Listening) {
                            _voiceState.value = current.copy(rmsDb = (rmsdB + 2f).coerceAtLeast(0f))
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(tag, "onEndOfSpeech")
                    }

                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue (offline fallback active)"
                            else -> "Recognition error #$error"
                        }
                        Log.w(tag, "SpeechRecognizer error: $errorMsg ($error)")
                        _voiceState.value = VoiceState.Error(errorMsg)

                        if (isListeningContinuous) {
                            scope.launch {
                                delay(1200)
                                if (isListeningContinuous) startListening()
                            }
                        } else {
                            scope.launch {
                                delay(2000)
                                _voiceState.value = VoiceState.Idle
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.d(tag, "Voice results: $recognizedText")
                            processVoiceInput(recognizedText)
                        } else {
                            _voiceState.value = VoiceState.Idle
                        }

                        if (isListeningContinuous) {
                            scope.launch {
                                delay(800)
                                if (isListeningContinuous) startListening()
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!partial.isNullOrEmpty()) {
                            val text = partial[0]
                            _voiceState.value = VoiceState.Processing(text)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize SpeechRecognizer: ${e.message}")
        }
    }

    fun startListening() {
        try {
            if (speechRecognizer == null) {
                setupRecognizer()
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Offline fallback hint
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            _voiceState.value = VoiceState.Listening(wakeWordDetected = false, rmsDb = 0f)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(tag, "Error starting voice listening: ${e.message}")
            _voiceState.value = VoiceState.Error("Mic failed to start: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(tag, "Error stopping voice listening: ${e.message}")
        }
    }

    fun toggleContinuousMode(enabled: Boolean) {
        isListeningContinuous = enabled
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    /**
     * NLP Intent Parsing Engine:
     * Parses natural language variations in English & Hindi (Hinglish).
     * Handles wake words if present, e.g., "Hey Buddy, skip this track" or directly "Skip track".
     */
    fun processVoiceInput(text: String) {
        val lower = text.lowercase(Locale.ROOT).trim()
        val currentWakeWord = _wakeWord.value.lowercase(Locale.ROOT)

        // Check if wake word is prefixed
        var cleanedText = lower
        var wakeWordMatched = false

        if (lower.contains(currentWakeWord)) {
            wakeWordMatched = true
            cleanedText = lower.replace(currentWakeWord, "").trim()
        }

        // Clean up common fillers: "please", "can you", "kripya", "zara", "bhai"
        cleanedText = cleanedText
            .replace("please", "")
            .replace("can you", "")
            .replace("could you", "")
            .replace("kripya", "")
            .replace("zara", "")
            .replace("bhai", "")
            .replace("gaana", "song")
            .trim()

        val parsedCommand: VoiceCommand = when {
            // Next song variations: "next", "next song", "aage chalo", "skip this track", "agla song"
            cleanedText.containsAny("next", "skip", "aage", "forward", "agla", "next song") -> {
                VoiceCommand.NextTrack
            }

            // Previous song variations: "previous", "previous song", "peeche jao", "peechhle gaane", "back", "pichla"
            cleanedText.containsAny("previous", "prev", "back", "peeche", "peechhle", "pichla", "piche") -> {
                VoiceCommand.PreviousTrack
            }

            // Volume Up (+10%): "volume up", "awaaz badhao", "louder", "volume high", "sound up", "tez karo"
            cleanedText.containsAny("volume up", "louder", "awaaz badhao", "awaz badhao", "sound up", "tez", "increase volume", "volume increase") -> {
                VoiceCommand.VolumeUp
            }

            // Volume Down (-10%): "volume down", "awaaz kam karo", "quieter", "sound down", "softer", "kam karo"
            cleanedText.containsAny("volume down", "softer", "quieter", "awaaz kam", "awaz kam", "sound down", "dheere", "decrease volume", "volume decrease") -> {
                VoiceCommand.VolumeDown
            }

            // Mute / Unmute
            cleanedText.containsAny("unmute", "awaaz kholo") -> {
                VoiceCommand.Unmute
            }
            cleanedText.containsAny("mute", "silent", "shant", "chup") -> {
                VoiceCommand.Mute
            }

            // Bass Boost: "bass boost", "more bass", "bass badhao"
            cleanedText.containsAny("bass boost", "bass", "more bass", "heavy bass", "bass badhao") -> {
                VoiceCommand.BoostBass
            }

            // Shuffle
            cleanedText.containsAny("shuffle", "mix", "random") -> {
                VoiceCommand.Shuffle
            }

            // Play / Resume: "play", "start", "resume", "chalao", "shuru", "bajaao"
            cleanedText.matches(Regex(".*\\b(play|start|resume|chalao|bajaao|shuru)\\b.*")) &&
                    !cleanedText.containsAny("pause", "stop", "ruk", "band") -> {
                // Check if playing a specific genre or title
                if (cleanedText.contains("synthwave")) VoiceCommand.PlayGenre("Synthwave")
                else if (cleanedText.contains("lo-fi") || cleanedText.contains("lofi")) VoiceCommand.PlayGenre("Lo-Fi")
                else if (cleanedText.contains("rock")) VoiceCommand.PlayGenre("Rock")
                else if (cleanedText.contains("ambient")) VoiceCommand.PlayGenre("Ambient")
                else if (cleanedText.contains("electronic")) VoiceCommand.PlayGenre("Electronic")
                else if (cleanedText.contains("pop")) VoiceCommand.PlayGenre("Pop")
                else {
                    val searchPart = cleanedText.replace(Regex("\\b(play|song|start|bajaao|chalao)\\b"), "").trim()
                    if (searchPart.length > 2) {
                        VoiceCommand.SearchPlay(searchPart)
                    } else {
                        VoiceCommand.Play
                    }
                }
            }

            // Pause / Stop: "pause", "stop", "ruko", "chup", "rok do", "band karo"
            cleanedText.containsAny("pause", "stop", "ruko", "rok", "band karo", "ruk jao", "hold") -> {
                VoiceCommand.Pause
            }

            // Genre searches without "play" keyword
            cleanedText.contains("synthwave") -> VoiceCommand.PlayGenre("Synthwave")
            cleanedText.contains("lofi") || cleanedText.contains("lo-fi") -> VoiceCommand.PlayGenre("Lo-Fi")
            cleanedText.contains("rock") -> VoiceCommand.PlayGenre("Rock")
            cleanedText.contains("ambient") -> VoiceCommand.PlayGenre("Ambient")
            cleanedText.contains("electronic") -> VoiceCommand.PlayGenre("Electronic")

            else -> VoiceCommand.Unknown(text)
        }

        val message = when (parsedCommand) {
            is VoiceCommand.NextTrack -> "⏭ Skipping to next track"
            is VoiceCommand.PreviousTrack -> "⏮ Going to previous track"
            is VoiceCommand.Play -> "▶ Resuming playback"
            is VoiceCommand.Pause -> "⏸ Pausing playback"
            is VoiceCommand.TogglePlayPause -> "⏯ Toggling playback"
            is VoiceCommand.VolumeUp -> "🔊 Volume +10%"
            is VoiceCommand.VolumeDown -> "🔉 Volume -10%"
            is VoiceCommand.Mute -> "🔇 Muted audio"
            is VoiceCommand.Unmute -> "🔊 Unmuted audio"
            is VoiceCommand.BoostBass -> "⚡ Bass Booster activated"
            is VoiceCommand.Shuffle -> "🔀 Shuffle toggled"
            is VoiceCommand.PlayGenre -> "🎵 Playing ${parsedCommand.genre} tracks"
            is VoiceCommand.SearchPlay -> "🔍 Searching for '${parsedCommand.query}'"
            is VoiceCommand.Unknown -> "❓ Voice command not recognized: '$text'"
        }

        _voiceState.value = VoiceState.Executed(parsedCommand, text, message)
        onCommandReceived(parsedCommand, text)

        scope.launch {
            delay(3000)
            if (_voiceState.value is VoiceState.Executed) {
                _voiceState.value = VoiceState.Idle
            }
        }
    }

    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it) }
    }

    fun onDestroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
