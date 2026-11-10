package com.example.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
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

enum class ChimeFeedback {
    WAKE_DETECTED,
    COMMAND_SUCCESS,
    ERROR
}

data class WakeWordMatchResult(
    val isMatched: Boolean,
    val isWakeWordOnly: Boolean,
    val commandPart: String
)

class VoiceEngine(
    private val context: Context,
    private val onCommandReceived: (VoiceCommand, String) -> Unit
) {
    private val TAG = "VoiceEngine"
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val prefs = context.getSharedPreferences("voicebeats_wake_word_prefs", Context.MODE_PRIVATE)

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyRecognizing = false
    private var isWakeWordArmed = false
    private var wakeTimeoutJob: Job? = null
    private var restartListeningJob: Job? = null

    // Audio Chime Generator for Earbuds & Pocket Feedback
    private var toneGenerator: ToneGenerator? = null

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

    // Continuous Hands-Free Earbuds / Pocket Mode (Enabled by default as requested)
    private val _isHandsFreeModeActive = MutableStateFlow(
        prefs.getBoolean("hands_free_earbuds_mode", true)
    )
    val isHandsFreeModeActive: StateFlow<Boolean> = _isHandsFreeModeActive.asStateFlow()

    // Audio Chime Feedback toggle
    private val _isAudioChimeEnabled = MutableStateFlow(
        prefs.getBoolean("audio_chime_feedback", true)
    )
    val isAudioChimeEnabled: StateFlow<Boolean> = _isAudioChimeEnabled.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        initSpeechRecognizer()
        initToneGenerator()
    }

    private fun initToneGenerator() {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed: ${e.message}")
        }
    }

    private fun loadPresets(): List<String> {
        val defaultList = listOf("Akki Beat", "Hey Akki", "Hey Buddy", "Suno", "Hi Genie", "Jarvis")
        val savedCustom = prefs.getStringSet("saved_custom_presets", emptySet())?.toList() ?: emptyList()
        val combined = (savedCustom + defaultList).distinct()
        return combined
    }

    /**
     * Plays an audible tone through connected earbuds or speaker
     */
    fun playChime(feedback: ChimeFeedback) {
        if (!_isAudioChimeEnabled.value) return
        try {
            if (toneGenerator == null) initToneGenerator()
            when (feedback) {
                ChimeFeedback.WAKE_DETECTED -> {
                    // Two quick beeps for wake word detection
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                }
                ChimeFeedback.COMMAND_SUCCESS -> {
                    // Positive acknowledgment chime
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 160)
                }
                ChimeFeedback.ERROR -> {
                    // Negative alert chime
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 140)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play chime: ${e.message}")
        }
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

    fun setHandsFreeMode(enabled: Boolean) {
        _isHandsFreeModeActive.value = enabled
        prefs.edit().putBoolean("hands_free_earbuds_mode", enabled).apply()
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun setAudioChimeEnabled(enabled: Boolean) {
        _isAudioChimeEnabled.value = enabled
        prefs.edit().putBoolean("audio_chime_feedback", enabled).apply()
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
            destroyRecognizer()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "onReadyForSpeech (armed=$isWakeWordArmed)")
                        isCurrentlyRecognizing = true
                        _voiceState.value = VoiceState.Listening(wakeWordDetected = isWakeWordArmed, rmsDb = 0f)
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
                        isCurrentlyRecognizing = false
                    }

                    override fun onError(error: Int) {
                        isCurrentlyRecognizing = false
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timed out"
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                            SpeechRecognizer.ERROR_CLIENT -> "Client recognition error"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
                            SpeechRecognizer.ERROR_NETWORK -> "Network issue"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                            else -> "Recognition error #$error"
                        }
                        Log.d(TAG, "SpeechRecognizer error: $errorMsg ($error)")

                        // In Hands-Free Earbuds Mode, keep listening loop alive
                        if (_isHandsFreeModeActive.value) {
                            scheduleRestart(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 600 else 300)
                        } else {
                            _voiceState.value = VoiceState.Error(errorMsg)
                            scope.launch {
                                delay(2000)
                                if (_voiceState.value is VoiceState.Error) {
                                    _voiceState.value = VoiceState.Idle
                                }
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isCurrentlyRecognizing = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognizedText = matches[0]
                            Log.d(TAG, "Voice input received: $recognizedText")
                            processVoiceInput(recognizedText)
                        } else {
                            if (_isHandsFreeModeActive.value) {
                                scheduleRestart(300)
                            } else {
                                _voiceState.value = VoiceState.Idle
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

    private fun scheduleRestart(delayMs: Long) {
        restartListeningJob?.cancel()
        restartListeningJob = scope.launch {
            delay(delayMs)
            if (_isHandsFreeModeActive.value) {
                startListening()
            }
        }
    }

    fun startListening() {
        scope.launch {
            try {
                if (speechRecognizer == null) {
                    setupRecognizer()
                } else if (isCurrentlyRecognizing) {
                    // Already actively listening
                    return@launch
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                _voiceState.value = VoiceState.Listening(wakeWordDetected = isWakeWordArmed, rmsDb = 0f)
                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error starting voice listening: ${e.message}")
                if (_isHandsFreeModeActive.value) {
                    scheduleRestart(1000)
                }
            }
        }
    }

    fun stopListening() {
        restartListeningJob?.cancel()
        wakeTimeoutJob?.cancel()
        isWakeWordArmed = false
        isCurrentlyRecognizing = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping voice listening: ${e.message}")
        }
    }

    /**
     * Checks if text contains the active wake-word or common variants
     */
    private fun checkWakeWordMatch(spokenText: String, activeWakeWord: String): WakeWordMatchResult {
        val normalizedSpoken = spokenText.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:'\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val normalizedWake = activeWakeWord.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:'\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val wakeTokens = normalizedWake.split(" ").filter { it.isNotBlank() }
        val coreToken = wakeTokens.lastOrNull { !it.matches(Regex("hey|hi|hello|ok|suno")) } ?: normalizedWake

        val matchesFull = normalizedSpoken.contains(normalizedWake)
        val matchesCore = coreToken.length >= 3 && (normalizedSpoken.contains(coreToken) || normalizedSpoken.split(" ").contains(coreToken))

        if (!matchesFull && !matchesCore) {
            return WakeWordMatchResult(isMatched = false, isWakeWordOnly = false, commandPart = spokenText)
        }

        // Strip wake-word from spoken text
        var remainder = normalizedSpoken
        if (matchesFull) {
            remainder = remainder.replace(normalizedWake, " ")
        }
        if (matchesCore) {
            remainder = remainder.replace(coreToken, " ")
        }
        remainder = remainder
            .replace(Regex("\\b(hey|hi|hello|ok|suno)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val isOnlyWake = remainder.isBlank() || remainder.length < 2

        return WakeWordMatchResult(
            isMatched = true,
            isWakeWordOnly = isOnlyWake,
            commandPart = remainder
        )
    }

    /**
     * Robust NLP Intent Parsing Engine:
     * Handles custom wake-words, punctuation stripping, Hindi (Hinglish) and English variations.
     */
    fun processVoiceInput(text: String) {
        val raw = text.trim()
        val activeWake = _wakeWord.value
        val wakeMatch = checkWakeWordMatch(raw, activeWake)

        // 1. User spoke ONLY the wake-word (e.g. "Hey Buddy", "Akki Beat", "Suno", or custom saved word)
        if (wakeMatch.isMatched && wakeMatch.isWakeWordOnly) {
            Log.d(TAG, "Wake-word triggered: '$raw'. Arming command listener...")
            isWakeWordArmed = true
            playChime(ChimeFeedback.WAKE_DETECTED)

            val message = "✨ Wake-word '$raw' recognized! Listening for command..."
            _voiceState.value = VoiceState.Executed(VoiceCommand.WakeWordOnly, raw, message)
            onCommandReceived(VoiceCommand.WakeWordOnly, raw)

            // Arm a 7-second window for the follow-up command
            wakeTimeoutJob?.cancel()
            wakeTimeoutJob = scope.launch {
                delay(7000)
                if (isWakeWordArmed) {
                    isWakeWordArmed = false
                    Log.d(TAG, "Command window timed out, returning to passive wake-word listening")
                }
            }

            // Immediately continue listening for the follow-up command
            scheduleRestart(250)
            return
        }

        // 2. Either the wake-word was in this utterance with a command, or the engine was already armed
        val isCommandAuthorized = wakeMatch.isMatched || isWakeWordArmed || !_isHandsFreeModeActive.value

        var commandText = if (wakeMatch.isMatched) wakeMatch.commandPart else raw.lowercase(Locale.ROOT)
            .replace(Regex("[.,!?;:'\"]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Clean conversational fillers & Hindi colloquialisms
        commandText = commandText
            .replace(Regex("^\\b(hey|hi|hello|ok|suno)\\b"), " ")
            .replace(Regex("\\b(please|kripya|zara|bhai|bhaiya|can you|could you)\\b"), " ")
            .replace("gaana", "song")
            .replace(Regex("\\s+"), " ")
            .trim()

        // Match Intent
        val parsedCommand: VoiceCommand = when {
            // Next song variations
            commandText.containsAny("next", "skip", "aage", "forward", "agla", "next song", "aage chalo", "skip this", "gaana badlo") -> {
                VoiceCommand.NextTrack
            }

            // Previous song variations
            commandText.containsAny("previous", "prev", "back", "peeche", "peechhle", "pichla", "piche", "peeche jao") -> {
                VoiceCommand.PreviousTrack
            }

            // Volume Up (Hardware System Volume)
            commandText.containsAny(
                "volume up", "volume badhao", "volume badha", "volume tez", "volume increase", "increase volume",
                "awaaz badhao", "awaz badhao", "awaaz tez", "awaz tez", "awaaz jyada", "sound up", "sound badhao",
                "tez karo", "louder", "sound high", "volume high"
            ) -> {
                VoiceCommand.VolumeUp
            }

            // Volume Down (Hardware System Volume)
            commandText.containsAny(
                "volume down", "volume kam", "volume dheere", "volume decrease", "decrease volume",
                "awaaz kam", "awaz kam", "awaaz dheere", "awaz dheere", "awaaz ghatado", "sound down",
                "sound kam", "dheere karo", "softer", "quieter", "sound low", "volume low"
            ) -> {
                VoiceCommand.VolumeDown
            }

            // Mute / Unmute
            commandText.containsAny("unmute", "awaaz kholo", "sound on", "awaz chalu") -> {
                VoiceCommand.Unmute
            }
            commandText.containsAny("mute", "silent", "awaaz band", "sound band", "chup", "shant") -> {
                VoiceCommand.Mute
            }

            // Bass Boost
            commandText.containsAny("bass boost", "boost bass", "more bass", "heavy bass", "bass badhao", "bass") -> {
                VoiceCommand.BoostBass
            }

            // Shuffle
            commandText.containsAny("shuffle", "mix", "random") -> {
                VoiceCommand.Shuffle
            }

            // Play / Resume
            commandText.matches(Regex(".*\\b(play|start|resume|chalao|bajaao|shuru)\\b.*")) &&
                    !commandText.containsAny("pause", "stop", "ruk", "band") -> {
                if (commandText.contains("bollywood")) VoiceCommand.PlayGenre("Bollywood")
                else if (commandText.contains("punjabi")) VoiceCommand.PlayGenre("Punjabi")
                else if (commandText.contains("synthwave")) VoiceCommand.PlayGenre("Synthwave")
                else if (commandText.contains("lo-fi") || commandText.contains("lofi")) VoiceCommand.PlayGenre("Lo-Fi")
                else if (commandText.contains("rock")) VoiceCommand.PlayGenre("Rock")
                else if (commandText.contains("ambient")) VoiceCommand.PlayGenre("Ambient")
                else if (commandText.contains("pop")) VoiceCommand.PlayGenre("Pop")
                else {
                    val searchPart = commandText.replace(Regex("\\b(play|song|start|bajaao|chalao)\\b"), "").trim()
                    if (searchPart.length > 2) {
                        VoiceCommand.SearchPlay(searchPart)
                    } else {
                        VoiceCommand.Play
                    }
                }
            }

            // Pause / Stop
            commandText.containsAny("pause", "stop", "ruko", "rok", "band karo", "ruk jao", "hold") -> {
                VoiceCommand.Pause
            }

            // Genre directly
            commandText.contains("bollywood") -> VoiceCommand.PlayGenre("Bollywood")
            commandText.contains("punjabi") -> VoiceCommand.PlayGenre("Punjabi")
            commandText.contains("synthwave") -> VoiceCommand.PlayGenre("Synthwave")
            commandText.contains("lofi") || commandText.contains("lo-fi") -> VoiceCommand.PlayGenre("Lo-Fi")
            commandText.contains("rock") -> VoiceCommand.PlayGenre("Rock")
            commandText.contains("pop") -> VoiceCommand.PlayGenre("Pop")

            else -> {
                // If wake word matched and user spoke some query
                if (wakeMatch.isMatched && commandText.length > 2) {
                    VoiceCommand.SearchPlay(commandText)
                } else {
                    VoiceCommand.Unknown(raw)
                }
            }
        }

        // If command was parsed and recognized
        if (parsedCommand !is VoiceCommand.Unknown) {
            isWakeWordArmed = false
            wakeTimeoutJob?.cancel()
            playChime(ChimeFeedback.COMMAND_SUCCESS)

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
        } else {
            // If unknown and in hands-free mode without wake word, just ignore ambient chatter
            if (!wakeMatch.isMatched && !isWakeWordArmed && _isHandsFreeModeActive.value) {
                Log.d(TAG, "Ignoring non-wake ambient speech: $raw")
            } else {
                playChime(ChimeFeedback.ERROR)
                _voiceState.value = VoiceState.Executed(parsedCommand, raw, "❓ Command not recognized: '$raw'")
            }
        }

        // Reset and continue listening loop in hands-free mode
        if (_isHandsFreeModeActive.value) {
            scheduleRestart(700)
        } else {
            scope.launch {
                delay(3000)
                if (_voiceState.value is VoiceState.Executed) {
                    _voiceState.value = VoiceState.Idle
                }
            }
        }
    }

    private fun String.containsAny(vararg words: String): Boolean {
        return words.any { this.contains(it) }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    fun onDestroy() {
        restartListeningJob?.cancel()
        wakeTimeoutJob?.cancel()
        destroyRecognizer()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }
}
