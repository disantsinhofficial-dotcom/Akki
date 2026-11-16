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
import kotlinx.coroutines.cancel
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.vosk.android.RecognitionListener as VoskRecognitionListener
import java.io.IOException

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
    private var voskController: VoskVoiceController? = null
    private val _recognitionMode = MutableStateFlow(prefs.getString("voice_recognition_mode", "google") ?: "google")
    val recognitionMode: StateFlow<String> = _recognitionMode.asStateFlow()
    @Volatile private var isDestroyed = false
    private var isCurrentlyRecognizing = false
    private var isStartPending = false
    private var isWakeWordArmed = false
    private var wakeTimeoutJob: Job? = null
    private var restartListeningJob: Job? = null
    // SpeechRecognizer can occasionally stop delivering callbacks after the app is backgrounded.
    // A session watchdog recreates it instead of leaving the microphone silently inactive.
    private var recognitionWatchdogJob: Job? = null
    private var recognitionSessionStartedAt: Long = 0L

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

    fun setRecognitionMode(mode: String, restartIfActive: Boolean = true) {
        val selected = if (mode.equals("vosk", ignoreCase = true)) "vosk" else "google"
        if (_recognitionMode.value == selected) return
        val wasListening = _isHandsFreeModeActive.value
        stopListening()
        _recognitionMode.value = selected
        prefs.edit().putString("voice_recognition_mode", selected).apply()
        if (selected == "vosk") {
            destroyRecognizer()
        } else {
            voskController?.destroy()
            voskController = null
            if (speechRecognizer == null) setupRecognizer()
        }
        if (restartIfActive && wasListening) startListening()
    }

    fun setHandsFreeModeState(enabled: Boolean) {
        _isHandsFreeModeActive.value = enabled
        prefs.edit().putBoolean("hands_free_earbuds_mode", enabled).apply()
    }

    fun setHandsFreeMode(enabled: Boolean) {
        setHandsFreeModeState(enabled)
        if (enabled) startListening() else stopListening()
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
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            if (isDestroyed) return@post
            try {
                destroyRecognizer()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "onReadyForSpeech (armed=$isWakeWordArmed)")
                            isStartPending = false
                            isCurrentlyRecognizing = true
                            recognitionSessionStartedAt = android.os.SystemClock.elapsedRealtime()
                            scheduleRecognitionWatchdog()
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
                            Log.d(TAG, "onEndOfSpeech; waiting for final result")
                        }

                        override fun onError(error: Int) {
                            isStartPending = false
                            isCurrentlyRecognizing = false
                            recognitionWatchdogJob?.cancel()
                            Log.d(TAG, "SpeechRecognizer error code: $error (armed=$isWakeWordArmed)")

                            if (_isHandsFreeModeActive.value) {
                                val needsRecreation = error == SpeechRecognizer.ERROR_CLIENT ||
                                    error == SpeechRecognizer.ERROR_SERVER
                                if (needsRecreation) {
                                    Log.w(TAG, "Speech service became unstable; recreating recognizer")
                                    destroyRecognizer()
                                    setupRecognizer()
                                }
                                val delayMs = when {
                                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2200L
                                    needsRecreation -> 2500L
                                    else -> 1500L
                                }
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
                            isStartPending = false
                            isCurrentlyRecognizing = false
                            recognitionWatchdogJob?.cancel()
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                val recognizedText = matches[0]
                                Log.d(TAG, "Voice input received: '$recognizedText' (armed=$isWakeWordArmed)")
                                try {
                                    processVoiceInput(recognizedText)
                                } catch (error: Exception) {
                                    Log.e(TAG, "Voice command processing failed; recovering recognition session", error)
                                    _voiceState.value = VoiceState.Error(
                                        error.message?.let { "Voice command error: $it" } ?: "Voice command could not be processed"
                                    )
                                    if (_isHandsFreeModeActive.value) {
                                        scheduleRestart(1200)
                                    } else {
                                        scope.launch {
                                            delay(2500)
                                            if (_voiceState.value is VoiceState.Error) _voiceState.value = VoiceState.Idle
                                        }
                                    }
                                }
                            } else {
                                if (_isHandsFreeModeActive.value) {
                                    scheduleRestart(1200)
                                } else {
                                    _voiceState.value = VoiceState.Idle
                                }
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            // Partial callbacks must not extend the lifetime of a recognition session.
                            // A provider can emit partials but never deliver a final result; keep the
                            // original session deadline so the watchdog can recover that stuck session.
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
    }

    private fun scheduleRecognitionWatchdog() {
        recognitionWatchdogJob?.cancel()
        recognitionWatchdogJob = scope.launch {
            delay(18_000)
            if (_isHandsFreeModeActive.value && (isCurrentlyRecognizing || isStartPending)) {
                val elapsed = android.os.SystemClock.elapsedRealtime() - recognitionSessionStartedAt
                if (elapsed >= 17_000) {
                    Log.w(TAG, "Recognition session timed out (pending=$isStartPending, active=$isCurrentlyRecognizing); recreating SpeechRecognizer")
                    isCurrentlyRecognizing = false
                    isStartPending = false
                    try { speechRecognizer?.cancel() } catch (_: Exception) {}
                    destroyRecognizer()
                    setupRecognizer()
                    scheduleRestart(900)
                }
            }
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
        if (isDestroyed) return
        if (_recognitionMode.value == "vosk") {
            if (voskController == null) {
                voskController = VoskVoiceController(context,
                    onPartial = { partial ->
                        if (partial.isNotBlank() && _isHandsFreeModeActive.value) {
                            _voiceState.value = VoiceState.Processing(partial)
                        }
                    },
                    onRecognized = { text -> if (text.isNotBlank()) processVoiceInput(text) },
                    onFailure = { error ->
                        Log.e(TAG, "Vosk recognition error", error)
                        _voiceState.value = VoiceState.Error("Vosk: ${error.message ?: "recognition failed"}")
                        if (_isHandsFreeModeActive.value) scheduleRestart(1500)
                    }
                )
            }
            _voiceState.value = VoiceState.Listening(wakeWordDetected = false, rmsDb = 0f)
            voskController?.start()
            return
        }
        scope.launch {
            if (isDestroyed) return@launch
            try {
                if (speechRecognizer == null) {
                    // setupRecognizer posts creation to the main looper. Never return silently:
                    // wait for creation, then retry the start on the next main-loop turn.
                    setupRecognizer()
                    delay(350)
                    if (speechRecognizer == null) {
                        Log.w(TAG, "SpeechRecognizer is still initializing; retrying")
                        scheduleRestart(500)
                        return@launch
                    }
                }
                if (isCurrentlyRecognizing || isStartPending) {
                    Log.d(TAG, "Ignoring duplicate startListening request")
                    return@launch
                }

                // Do not alter system/notification streams here. The user's
                // Quick Settings Silent mode and sound preferences must remain untouched.

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Prefer the installed Android speech provider; forcing offline often disables\n                    // recognition for languages without a downloaded offline model.\n                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }

                val recognizer = speechRecognizer ?: return@launch
                isStartPending = true
                recognitionSessionStartedAt = android.os.SystemClock.elapsedRealtime()
                // Arm recovery before startListening: some speech providers never deliver
                // onReadyForSpeech, leaving isStartPending true forever.
                scheduleRecognitionWatchdog()
                // Do not cancel before every start: cancel can interrupt a valid recognizer
                // session and cause extra recognizer-service transitions/beeps.
                recognizer.startListening(intent)
            } catch (e: Exception) {
                isStartPending = false
                isCurrentlyRecognizing = false
                Log.w(TAG, "Error starting voice listening: ${e.message}")
                if (_isHandsFreeModeActive.value) {
                    scheduleRestart(1500)
                }
            }
        }
    }

    fun stopListening() {
        restartListeningJob?.cancel()
        recognitionWatchdogJob?.cancel()
        wakeTimeoutJob?.cancel()
        isWakeWordArmed = false
        isCurrentlyRecognizing = false
        isStartPending = false
        try {
            voskController?.stop()
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
        val spokenTokens = normalizedSpoken.split(" ").filter { it.isNotBlank() }

        // Match whole words/phrases only; substring matches caused accidental wake-ups
        // (for example, "buddy" inside a longer unrelated word).
        val matchesFull = normalizedSpoken == normalizedWake ||
            normalizedSpoken.startsWith("$normalizedWake ") ||
            normalizedSpoken.endsWith(" $normalizedWake") ||
            normalizedSpoken.contains(" $normalizedWake ")
        val matchesCore = coreToken.length >= 3 && spokenTokens.contains(coreToken)

        if (!matchesFull && !matchesCore) {
            return WakeWordMatchResult(isMatched = false, isWakeWordOnly = false, commandPart = spokenText)
        }

        // Remove only the first matched wake-word occurrence, preserving later words
        // that may be part of a song title or search query.
        var remainder = normalizedSpoken
        if (matchesFull) {
            val escapedWake = Regex.escape(normalizedWake)
            remainder = remainder.replaceFirst(Regex("(^|\\s)$escapedWake(?=\\s|$)"), " ").trim()
        } else if (matchesCore) {
            val escapedCore = Regex.escape(coreToken)
            remainder = remainder.replaceFirst(Regex("(^|\\s)$escapedCore(?=\\s|$)"), " ").trim()
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
    private fun parseSpokenVolumeNumber(text: String): Int? {
        val normalized = text.lowercase(Locale.ROOT).trim()
            .replace(Regex("\\b(set|put|make|volume|vol|awaaz|awaz|sound|to|at|percent|percentage|per cent)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        normalized.toIntOrNull()?.let { return it.coerceIn(0, 100) }
        val words = mapOf(
            "zero" to 0, "oh" to 0, "one" to 1, "two" to 2, "three" to 3,
            "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
            "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
            "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16,
            "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
            "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
            "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
        )
        words[normalized]?.let { return it }
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.size == 2 && tokens[0] in words && tokens[1] in words) {
            val tens = words[tokens[0]] ?: return null
            val ones = words[tokens[1]] ?: return null
            if (tens in 20..90 && ones in 1..9) return (tens + ones).coerceAtMost(100)
        }
        if (tokens.size == 2 && tokens[0] in words && tokens[1] == "hundred") {
            val hundreds = words[tokens[0]] ?: return null
            if (hundreds in 1..9) return 100
        }
        return null
    }

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
                    scheduleRestart(700)
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
                scheduleRestart(600)
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
        val volMatch = Regex("(?:volume|vol|awaaz|awaz|sound)\\D*(\\d{1,3})").find(commandText)
        val spokenNumber = parseSpokenVolumeNumber(commandText)
        val parsedCommand: VoiceCommand = when {
            volMatch != null -> {
                val pct = volMatch.groupValues[1].toInt().coerceIn(0, 100)
                VoiceCommand.SetVolumeLevel(pct)
            }
            spokenNumber != null -> VoiceCommand.SetVolumeLevel(spokenNumber)
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
        if (isDestroyed) return
        isDestroyed = true
        restartListeningJob?.cancel()
        recognitionWatchdogJob?.cancel()
        wakeTimeoutJob?.cancel()
        destroyRecognizer()
        voskController?.destroy()
        voskController = null
        scope.cancel()
    }
}


/**
 * Vosk-backed local recognizer. The English model ships as an Android asset
 * dependency and is unpacked into app-private storage on first use.
 */
private class VoskVoiceController(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onRecognized: (String) -> Unit,
    private val onFailure: (Exception) -> Unit
) {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var loading = false
    private var shouldListen = false
    private var destroyed = false
    // Vosk emits onResult at each end-of-utterance and may emit the same text
    // again as onFinalResult when a stream is stopped. Guard the shared command
    // executor against that duplicate without suppressing a later repeated command.
    private var lastDeliveredText: String = ""
    private var lastDeliveredAtMs: Long = 0L

    fun start() {
        if (destroyed) return
        shouldListen = true
        if (model != null) { startRecorder(); return }
        if (loading) return
        loading = true
        StorageService.unpack(context, "model-en-us", "vosk-model-en",
            { loaded ->
                loading = false
                if (destroyed) { loaded.close(); return@unpack }
                model = loaded
                if (shouldListen) startRecorder()
            },
            { error -> loading = false; if (!destroyed) onFailure(error) }
        )
    }

    private fun startRecorder() {
        if (!shouldListen || destroyed || speechService != null) return
        try {
            val activeRecognizer = Recognizer(model ?: return, 16000.0f)
            val activeService = SpeechService(activeRecognizer, 16000.0f)
            recognizer = activeRecognizer
            speechService = activeService
            activeService.startListening(object : VoskRecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = parseText(hypothesis, "partial")
                    if (shouldListen && !destroyed && text.isNotBlank()) onPartial(text)
                }
                override fun onResult(hypothesis: String?) {
                    // SpeechService calls this once when Vosk detects the end of an
                    // utterance. This is the normal command-delivery callback.
                    deliverRecognized(parseText(hypothesis, "text"))
                }
                override fun onFinalResult(hypothesis: String?) {
                    // Final results are normally emitted only when the stream ends.
                    // Deliver only if this text was not already sent by onResult.
                    deliverRecognized(parseText(hypothesis, "text"))
                }
                override fun onError(exception: Exception?) {
                    releaseRecorder()
                    if (shouldListen && !destroyed) {
                        onFailure(exception ?: IOException("Vosk microphone error"))
                    }
                }
                override fun onTimeout() {
                    // A timed-out SpeechService thread has stopped recording. Recreate
                    // it (and its AudioRecord) rather than leaving hands-free listening idle.
                    if (shouldListen && !destroyed) {
                        releaseRecorder()
                        startRecorder()
                    }
                }
            })
        } catch (error: Exception) {
            releaseRecorder()
            onFailure(error)
        }
    }

    private fun deliverRecognized(text: String) {
        if (text.isBlank() || !shouldListen || destroyed) return
        val normalized = text.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        val now = android.os.SystemClock.elapsedRealtime()
        if (normalized == lastDeliveredText && now - lastDeliveredAtMs < 1800L) {
            Log.d("VoiceEngine", "Ignoring duplicate Vosk final result: '$text'")
            return
        }
        lastDeliveredText = normalized
        lastDeliveredAtMs = now
        Log.d("VoiceEngine", "Vosk final transcript: '$text'")
        onRecognized(text)
    }

    private fun parseText(json: String?, key: String): String {
        if (json.isNullOrBlank()) return ""
        return try { JSONObject(json).optString(key, "").trim() } catch (_: Exception) { "" }
    }

    private fun releaseRecorder() {
        try { speechService?.cancel() } catch (_: Exception) {}
        try { speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
        try { recognizer?.close() } catch (_: Exception) {}
        recognizer = null
    }

    fun stop() { shouldListen = false; releaseRecorder() }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        shouldListen = false
        releaseRecorder()
        try { model?.close() } catch (_: Exception) {}
        model = null
    }
}
