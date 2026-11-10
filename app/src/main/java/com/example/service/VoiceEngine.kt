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
    object WakeWordOnly : VoiceCommand()
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
    private val TAG = "VoiceEngine"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val prefs = context.getSharedPreferences("voicebeats_wake_word_prefs", Context.MODE_PRIVATE)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningContinuous = false

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState.asStateFlow()

    // Persistent Wake-Word initialized from SharedPreferences
    private val _wakeWord = MutableStateFlow(
        prefs.getString("saved_wake_word", "Akki Beat") ?: "Akki Beat"
    )
    val wakeWord: StateFlow<String> = _wakeWord.asStateFlow()

    // Dynamic preset list including user's custom wake-words
    private val _wakeWordPresets = MutableStateFlow(loadPresets())
    val wakeWordPresets: StateFlow<List<String>> = _wakeWordPresets.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        initSpeechRecognizer()
    }

    private fun loadPresets(): List<String> {
        val defaultList = listOf("Akki Beat", "Hey Akki", "Suno", "Hi Genie", "Hey Buddy")
        val savedCustom = prefs.getStringSet("saved_custom_presets", emptySet())?.toList() ?: emptyList()
        val combined = (savedCustom + defaultList).distinct()
        return combined
    }

    /**
     * Saves custom wake word permanently in SharedPreferences
     */
    fun setWakeWord(word: String): Boolean {
        val cleanWord = word.trim()
        if (cleanWord.isBlank()) return false

        _wakeWord.value = cleanWord
        prefs.edit().putString("saved_wake_word", cleanWord).apply()

        // Update presets list and persist
        val updated = (listOf(cleanWord) + _wakeWordPresets.value).distinct()
        _wakeWordPresets.value = updated
        prefs.edit().putStringSet("saved_custom_presets", updated.toSet()).apply()

        Log.d(TAG, "Custom wake-word saved successfully: $cleanWord")
        return true
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            _isAvailable.value = true
            setupRecognizer()
        } else {
            _isAvailable.value = false
            Log.w(TAG, "Speech recognition is not supported on this device.")
        }
    }

    private fun setupRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech")
                        _voiceState.value = VoiceState.Listening(wakeWordDetected = false, rmsDb = 0f)
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val current = _voiceState.value
                        if (current is VoiceState.Listening) {
                            _voiceState.value = current.copy(rmsDb = (rmsdB + 2f).coerceAtLeast(0f))
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
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
                        Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
                        _voiceState.value = VoiceState.Error(errorMsg)

                        if (isListeningContinuous) {
                            scope.launch {
                                delay(1200)
                                if (isListeningContinuous) startListening()
                            }
                        } else {
                            scope.launch {
                                delay(2200)
                                _voiceState.value = VoiceState.Idle
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.d(TAG, "Voice results: $recognizedText")
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
            Log.e(TAG, "Failed to initialize SpeechRecognizer: ${e.message}")
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
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            _voiceState.value = VoiceState.Listening(wakeWordDetected = false, rmsDb = 0f)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice listening: ${e.message}")
            _voiceState.value = VoiceState.Error("Mic failed to start: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping voice listening: ${e.message}")
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
     * Robust NLP Intent Parsing Engine:
     * Handles custom wake-words, punctuation stripping, Hindi (Hinglish) and English variations.
     */
    fun processVoiceInput(text: String) {
        val raw = text.trim()
        val normalized = raw.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val activeWake = _wakeWord.value.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 1. Check if user spoke ONLY the wake-word (e.g. "Akki Beat", "Hey Akki", or custom word)
        val isJustWakeWord = normalized == activeWake ||
                normalized == "hey $activeWake" ||
                normalized == "hi $activeWake" ||
                normalized == "ok $activeWake" ||
                normalized == "hello $activeWake" ||
                normalized == "suno $activeWake"

        if (isJustWakeWord) {
            Log.d(TAG, "Wake-word only detected: $raw")
            _voiceState.value = VoiceState.Executed(
                VoiceCommand.WakeWordOnly,
                raw,
                "✨ Wake-word '$raw' recognized! Listening for command..."
            )
            onCommandReceived(VoiceCommand.WakeWordOnly, raw)
            // Re-trigger listening for the command
            scope.launch {
                delay(400)
                startListening()
            }
            return
        }

        // 2. Strip Wake-Word prefix from the command text
        var cleanedText = normalized
        if (cleanedText.contains(activeWake)) {
            cleanedText = cleanedText.replace(activeWake, " ").trim()
        }

        // Also clean up common wake prefixes and conversational fillers
        cleanedText = cleanedText
            .replace(Regex("^\\b(hey|hi|hello|ok|suno)\\b"), " ")
            .replace(Regex("\\b(please|kripya|zara|bhai|bhaiya|can you|could you)\\b"), " ")
            .replace("gaana", "song")
            .replace(Regex("\\s+"), " ")
            .trim()

        // 3. Match Intent
        val parsedCommand: VoiceCommand = when {
            // Next song variations
            cleanedText.containsAny("next", "skip", "aage", "forward", "agla", "next song", "aage chalo", "skip this") -> {
                VoiceCommand.NextTrack
            }

            // Previous song variations
            cleanedText.containsAny("previous", "prev", "back", "peeche", "peechhle", "pichla", "piche", "peeche jao") -> {
                VoiceCommand.PreviousTrack
            }

            // Volume Up (Hardware System Volume)
            cleanedText.containsAny(
                "volume up", "volume badhao", "volume badha", "volume tez", "volume increase", "increase volume",
                "awaaz badhao", "awaz badhao", "awaaz tez", "awaz tez", "awaaz jyada", "sound up", "sound badhao",
                "tez karo", "louder", "sound high", "volume high"
            ) -> {
                VoiceCommand.VolumeUp
            }

            // Volume Down (Hardware System Volume)
            cleanedText.containsAny(
                "volume down", "volume kam", "volume dheere", "volume decrease", "decrease volume",
                "awaaz kam", "awaz kam", "awaaz dheere", "awaz dheere", "awaaz ghatado", "sound down",
                "sound kam", "dheere karo", "softer", "quieter", "sound low", "volume low"
            ) -> {
                VoiceCommand.VolumeDown
            }

            // Mute / Unmute
            cleanedText.containsAny("unmute", "awaaz kholo", "sound on", "awaz chalu") -> {
                VoiceCommand.Unmute
            }
            cleanedText.containsAny("mute", "silent", "awaaz band", "sound band", "chup", "shant") -> {
                VoiceCommand.Mute
            }

            // Bass Boost
            cleanedText.containsAny("bass boost", "boost bass", "more bass", "heavy bass", "bass badhao", "bass") -> {
                VoiceCommand.BoostBass
            }

            // Shuffle
            cleanedText.containsAny("shuffle", "mix", "random") -> {
                VoiceCommand.Shuffle
            }

            // Play / Resume
            cleanedText.matches(Regex(".*\\b(play|start|resume|chalao|bajaao|shuru)\\b.*")) &&
                    !cleanedText.containsAny("pause", "stop", "ruk", "band") -> {
                if (cleanedText.contains("bollywood")) VoiceCommand.PlayGenre("Bollywood")
                else if (cleanedText.contains("punjabi")) VoiceCommand.PlayGenre("Punjabi")
                else if (cleanedText.contains("synthwave")) VoiceCommand.PlayGenre("Synthwave")
                else if (cleanedText.contains("lo-fi") || cleanedText.contains("lofi")) VoiceCommand.PlayGenre("Lo-Fi")
                else if (cleanedText.contains("rock")) VoiceCommand.PlayGenre("Rock")
                else if (cleanedText.contains("ambient")) VoiceCommand.PlayGenre("Ambient")
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

            // Pause / Stop
            cleanedText.containsAny("pause", "stop", "ruko", "rok", "band karo", "ruk jao", "hold") -> {
                VoiceCommand.Pause
            }

            // Genre directly
            cleanedText.contains("bollywood") -> VoiceCommand.PlayGenre("Bollywood")
            cleanedText.contains("punjabi") -> VoiceCommand.PlayGenre("Punjabi")
            cleanedText.contains("synthwave") -> VoiceCommand.PlayGenre("Synthwave")
            cleanedText.contains("lofi") || cleanedText.contains("lo-fi") -> VoiceCommand.PlayGenre("Lo-Fi")
            cleanedText.contains("rock") -> VoiceCommand.PlayGenre("Rock")
            cleanedText.contains("pop") -> VoiceCommand.PlayGenre("Pop")

            else -> VoiceCommand.Unknown(raw)
        }

        val message = when (parsedCommand) {
            is VoiceCommand.NextTrack -> "⏭ Skipping to next track"
            is VoiceCommand.PreviousTrack -> "⏮ Going to previous track"
            is VoiceCommand.Play -> "▶ Resuming playback"
            is VoiceCommand.Pause -> "⏸ Pausing playback"
            is VoiceCommand.TogglePlayPause -> "⏯ Toggling playback"
            is VoiceCommand.VolumeUp -> "🔊 System Volume Raised (+1 Step)"
            is VoiceCommand.VolumeDown -> "🔉 System Volume Lowered (-1 Step)"
            is VoiceCommand.Mute -> "🔇 Muted device audio"
            is VoiceCommand.Unmute -> "🔊 Unmuted device audio"
            is VoiceCommand.BoostBass -> "⚡ Bass Booster activated"
            is VoiceCommand.Shuffle -> "🔀 Shuffle toggled"
            is VoiceCommand.WakeWordOnly -> "✨ Wake-word recognized!"
            is VoiceCommand.PlayGenre -> "🎵 Playing ${parsedCommand.genre} music"
            is VoiceCommand.SearchPlay -> "🔍 Searching YouTube for '${parsedCommand.query}'"
            is VoiceCommand.Unknown -> "❓ Command not recognized: '$raw'"
        }

        _voiceState.value = VoiceState.Executed(parsedCommand, raw, message)
        onCommandReceived(parsedCommand, raw)

        scope.launch {
            delay(3200)
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
