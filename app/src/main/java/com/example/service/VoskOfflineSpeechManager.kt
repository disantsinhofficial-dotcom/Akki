package com.example.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

enum class OfflineSpeechLanguage(val code: String, val label: String, val archiveName: String, val downloadUrl: String) {
    HINDI("hi", "Hindi", "vosk-model-small-hi-0.22", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip"),
    ENGLISH_INDIA("en-in", "English (India)", "vosk-model-small-en-in-0.4", "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip")
}

data class OfflineVoskStatus(
    val language: OfflineSpeechLanguage = OfflineSpeechLanguage.HINDI,
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val progress: Int = 0,
    val isReady: Boolean = false,
    val error: String? = null
)

/**
 * On-device Vosk recognizer. Model archives are downloaded once into app-private storage;
 * only the selected model is loaded into memory.
 */
class VoskOfflineSpeechManager(
    context: Context,
    private val onPartialText: (String) -> Unit,
    private val onRecognizedText: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val modelRoot = File(appContext.filesDir, "offline_vosk")
    private val prefs = appContext.getSharedPreferences("offline_vosk_prefs", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(OfflineVoskStatus())
    val status: StateFlow<OfflineVoskStatus> = _status.asStateFlow()

    private var downloadJob: Job? = null
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null
    private var lastDeliveredText: String = ""

    init {
        val saved = prefs.getString("language", OfflineSpeechLanguage.HINDI.code)
        val language = OfflineSpeechLanguage.values().firstOrNull { it.code == saved }
            ?: OfflineSpeechLanguage.HINDI
        _status.value = OfflineVoskStatus(
            language = language,
            isDownloaded = modelDirectory(language).resolve("am/final.mdl").isFile
        )
        LibVosk.setLogLevel(org.vosk.LogLevel.WARN)
    }

    fun selectLanguage(language: OfflineSpeechLanguage) {
        stopListening()
        releaseModel()
        prefs.edit().putString("language", language.code).apply()
        _status.value = OfflineVoskStatus(
            language = language,
            isDownloaded = modelDirectory(language).resolve("am/final.mdl").isFile
        )
    }

    fun downloadSelectedModel() {
        if (_status.value.isDownloading) return
        val language = _status.value.language
        if (modelDirectory(language).resolve("am/final.mdl").isFile) {
            _status.value = _status.value.copy(isDownloaded = true, progress = 100, error = null)
            return
        }
        downloadJob?.cancel()
        downloadJob = scope.launch {
            _status.value = _status.value.copy(isDownloading = true, progress = 0, error = null)
            try {
                withContext(Dispatchers.IO) { downloadAndExtract(language) { percent ->
                    scope.launch { _status.value = _status.value.copy(progress = percent) }
                } }
                _status.value = _status.value.copy(isDownloading = false, isDownloaded = true, progress = 100)
            } catch (e: Exception) {
                Log.e(TAG, "Vosk model download failed", e)
                _status.value = _status.value.copy(isDownloading = false, isDownloaded = false, error = e.message ?: "Model download failed")
            }
        }
    }

    private fun downloadAndExtract(language: OfflineSpeechLanguage, onProgress: (Int) -> Unit) {
        modelRoot.mkdirs()
        val zipFile = File(modelRoot, "${language.code}.download.zip")
        val tempDir = File(modelRoot, "${language.code}.extracting")
        zipFile.delete()
        tempDir.deleteRecursively()
        val connection = (URL(language.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 30000
            instanceFollowRedirects = true
            connect()
        }
        try {
            val total = connection.contentLengthLong
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(zipFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) onProgress(((downloaded * 70L / total).toInt()).coerceIn(0, 70))
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }
        tempDir.mkdirs()
        try {
            ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
                val buffer = ByteArray(64 * 1024)
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = File(tempDir, entry.name).canonicalFile
                    require(out.path.startsWith(tempDir.canonicalPath + File.separator)) { "Unsafe model archive entry" }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { output ->
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val extracted = tempDir.walkTopDown().firstOrNull { it.isDirectory && File(it, "am/final.mdl").isFile }
                ?: error("Downloaded model archive is incomplete")
            val target = modelDirectory(language)
            target.deleteRecursively()
            if (extracted == tempDir) {
                check(tempDir.renameTo(target)) { "Could not install speech model" }
            } else {
                check(extracted.renameTo(target)) { "Could not install speech model" }
            }
            check(File(target, "conf/model.conf").isFile || File(target, "am/final.mdl").isFile) {
                "Speech model validation failed"
            }
            onProgress(100)
        } finally {
            zipFile.delete()
            tempDir.deleteRecursively()
        }
    }

    fun startListening(): Boolean {
        val current = _status.value
        if (!current.isDownloaded) {
            onError("Pehle ${current.language.label} model download karein.")
            return false
        }
        if (speechService != null) return true
        return try {
            if (model == null) {
                model = Model(modelDirectory(current.language).absolutePath)
                recognizer = Recognizer(model, 16000.0f)
            }
            val activeRecognizer = recognizer ?: error("Offline recognizer was not initialized")
            lastDeliveredText = ""
            speechService = SpeechService(activeRecognizer, 16000.0f).also { service ->
                service.startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        val text = parseText(hypothesis)
                        if (text.isNotBlank()) onPartialText(text)
                    }
                    override fun onResult(hypothesis: String?) {
                        val text = parseText(hypothesis)
                        deliverRecognizedText(text)
                    }
                    override fun onFinalResult(hypothesis: String?) {
                        deliverRecognizedText(parseText(hypothesis))
                    }
                    override fun onError(exception: Exception?) {
                        this@VoskOfflineSpeechManager.onError(exception?.message ?: "Offline voice recognition error")
                    }
                    override fun onTimeout() {}
                })
            }
            _status.value = _status.value.copy(isReady = true, error = null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start Vosk", e)
            onError(e.message ?: "Unable to start offline recognition")
            false
        }
    }

    private fun deliverRecognizedText(text: String) {
        if (text.isNotBlank() && text != lastDeliveredText) {
            lastDeliveredText = text
            onRecognizedText(text)
        }
    }

    private fun parseText(json: String?): String = try {
        if (json.isNullOrBlank()) "" else JSONObject(json).optString("text").ifBlank { JSONObject(json).optString("partial") }
    } catch (_: Exception) { "" }

    fun stopListening() {
        try { speechService?.stop() } catch (_: Exception) {}
        try { speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
        _status.value = _status.value.copy(isReady = false)
    }

    private fun releaseModel() {
        stopListening()
        try { recognizer?.close() } catch (_: Exception) {}
        try { model?.close() } catch (_: Exception) {}
        recognizer = null
        model = null
    }

    private fun modelDirectory(language: OfflineSpeechLanguage) = File(modelRoot, language.archiveName)

    fun onDestroy() {
        downloadJob?.cancel()
        releaseModel()
        scope.cancel()
    }

    companion object { private const val TAG = "VoskOfflineSpeech" }
}
