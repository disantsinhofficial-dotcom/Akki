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
    data class SetVolumeLevel(val percentage: Int) : VoiceCommand()
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
    CANCELLED,
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

    // Continuous Passive Wake-Word Listening in background (Disabled by default to prevent unwanted mic beeps)
    private val _isHandsFreeModeActive = MutableStateFlow(
        prefs.getBoolean("hands_free_earbuds_mode", false)
    )
    val isHandsFreeModeActive: StateFlow<Boolean> = _isHandsFreeModeActive.asStateFlow()

    // App-generated chimes are disabled to avoid recurring earbud beeps.
    private val _isAudioChimeEnabled = MutableStateFlow(false)
    val isAudioChimeEnabled: StateFlow<Boolean> = _isAudioChimeEnabled.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    init {
        initSpeechRecognizer()
    }

    private fun loadPresets(): List<String> {
        val defaultList = listOf("Akki Beat", "Hey Akki", "Hey Buddy", "Suno", "Hi Genie", "Jarvis")
        val savedCustom = prefs.getStringSet("saved_custom_presets", emptySet())?.toList() ?: emptyList()
        val combined = (savedCustom + defaultList).distinct()
        return combined
    }

    /** App-generated chimes are intentionally silent. */
    fun playChime(feedback: ChimeFeedback) = Unit

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
        _isAudioChimeEnabled.value = false
        prefs.edit().putBoolean("audio_chime_feedback", false).apply()
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
                        _voiceState.value = if (isWakeWordArmed) {
                            VoiceState.Listening(wakeWordDetected = true, rmsDb = 0f)
                        } else {
                            VoiceState.Idle
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "onBeginningOfSpeech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (isWakeWordArmed) {
                            val current = _voiceState.value
                            if (current is VoiceState.Listening) {
                                _voiceState.value = current.copy(rmsDb = (rmsdB + 2f).coerceAtLeast(0f))
                            }
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "onEndOfSpeech")
                        isCurrentlyRecognizing = false
                    }

                    override fun onError(error: Int) {
                        isCurrentlyRecognizing = false
                        Log.d(TAG, "SpeechRecognizer error code: $error (armed=$isWakeWordArmed)")

                        if (_isHandsFreeModeActive.value) {
                            // If user is actively in command window, keep window open until timeout
                            val delayMs = if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 500L else 250L
                            scheduleRestart(delayMs)
                        } else {
                            _voiceState.value = VoiceState.Error("Recognition error #$error")
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
                            Log.d(TAG, "Voice input received: '$recognizedText' (armed=$isWakeWordArmed)")
                            processVoiceInput(recognizedText)
                        } else {
                            if (_isHandsFreeModeActive.value) {
                                scheduleRestart(250)
                            } else {
                                _voiceState.value = VoiceState.Idle
                            }
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        if (isWakeWordArmed) {
                            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!partial.isNullOrEmpty()) {
                                _voiceState.value = VoiceState.Processing(partial[0])
                            }
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
                    return@launch
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }

                speechRecognizer?.cancel()
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error starting voice listening: ${e.message}")
                if (_isHandsFreeModeActive.value) {
                    scheduleRestart(800)
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
     * - Passive background wake-word listening.
     * - Only activates command listening on speaking saved wake-word.
     * - Toggle behavior: speaking wake-word AGAIN cancels/turns off listening.
     * - 12-second auto-timeout if no command is spoken.
     */
    fun processVoiceInput(text: String) {
        val raw = text.trim()
        val activeWake = _wakeWord.value
        val wakeMatch = checkWakeWordMatch(raw, activeWake)

        // Case 1: User spoke ONLY the wake-word (e.g. "Hey Buddy" / "Akki Beat")
        if (wakeMatch.isMatched && wakeMatch.isWakeWordOnly) {
            if (isWakeWordArmed) {
                // TOGGLE OFF: User spoke wake-word again while listening was active -> Cancel/Turn OFF!
                Log.d(TAG, "Wake-word spoken again while active. Cancelling voice listening...")
                isWakeWordArmed = false
                wakeTimeoutJob?.cancel()
                playChime(ChimeFeedback.CANCELLED)

                _voiceState.value = VoiceState.Executed(
                    VoiceCommand.WakeWordOnly,
                    raw,
                    "🔇 Voice listening turned OFF (Standby mode)"
                )
                scope.launch {
                    delay(1500)
                    _voiceState.value = VoiceState.Idle
                }
                if (_isHandsFreeModeActive.value) {
                    scheduleRestart(350)
                }
                return
            } else {
                // TOGGLE ON: User spoke wake-word from standby -> Start Active Command Listening!
                Log.d(TAG, "Wake-word detected from standby! Starting 12s active command window...")
                isWakeWordArmed = true
                playChime(ChimeFeedback.WAKE_DETECTED)

                _voiceState.value = VoiceState.Listening(wakeWordDetected = true, rmsDb = 0f)
                onCommandReceived(VoiceCommand.WakeWordOnly, raw)

                // 12-second auto-off timeout
                wakeTimeoutJob?.cancel()
                wakeTimeoutJob = scope.launch {
                    delay(12000)
                    if (isWakeWordArmed) {
                        isWakeWordArmed = false
                        playChime(ChimeFeedback.CANCELLED)
                        _voiceState.value = VoiceState.Idle
                        Log.d(TAG, "Active command listening timed out after 12s. Returned to passive standby.")
                    }
                }

                // Continue listening for user's command
                scheduleRestart(200)
                return
            }
        }

        // Case 2: Wake-word was NOT spoken and engine was NOT armed:
        // Ambient noise/casual chat -> IGNORE completely to avoid false commands!
        if (!wakeMatch.isMatched && !isWakeWordArmed && _isHandsFreeModeActive.value) {
            Log.d(TAG, "Ignoring non-wake speech in standby: '$raw'")
            scheduleRestart(250)
            return
        }

        // Case 3: Either wake-word was in this utterance together with command, or engine was already armed
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
        val volMatch = Regex("(?:volume|awaaz|awaz|sound|vol)\\D*(\\d{1,3})").find(commandText)
        val parsedCommand: VoiceCommand = when {
            volMatch != null -> {
                val pct = volMatch.groupValues[1].toInt().coerceIn(0, 100)
                VoiceCommand.SetVolumeLevel(pct)
            }
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

            // Direct Search or Genre
            commandText.contains("bollywood") -> VoiceCommand.PlayGenre("Bollywood")
            commandText.contains("punjabi") -> VoiceCommand.PlayGenre("Punjabi")
            commandText.contains("synthwave") -> VoiceCommand.PlayGenre("Synthwave")
            commandText.contains("lofi") || commandText.contains("lo-fi") -> VoiceCommand.PlayGenre("Lo-Fi")
            commandText.contains("rock") -> VoiceCommand.PlayGenre("Rock")
            commandText.contains("pop") -> VoiceCommand.PlayGenre("Pop")

            else -> {
                if (commandText.length > 2) {
                    VoiceCommand.SearchPlay(commandText)
                } else {
                    VoiceCommand.Unknown(raw)
                }
            }
        }

        // Successful command execution
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
                is VoiceCommand.SetVolumeLevel -> "🔊 Volume set to ${parsedCommand.percentage}%"
                is VoiceCommand.Unknown -> "❓ Command not recognized: '$raw'"
            }

            _voiceState.value = VoiceState.Executed(parsedCommand, raw, message)
            onCommandReceived(parsedCommand, raw)
        } else {
            playChime(ChimeFeedback.ERROR)
            _voiceState.value = VoiceState.Executed(parsedCommand, raw, "❓ Command not recognized: '$raw'")
        }

        // Return to passive standby mode
        if (_isHandsFreeModeActive.value) {
            scope.launch {
                delay(2500)
                if (_voiceState.value is VoiceState.Executed) {
                    _voiceState.value = VoiceState.Idle
                }
            }
            scheduleRestart(800)
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
