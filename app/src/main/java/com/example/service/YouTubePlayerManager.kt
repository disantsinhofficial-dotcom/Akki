package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class YouTubePlayerManager(private val context: Context) {

    private val TAG = "YouTubePlayerManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var webView: WebView? = null
    private var isPlayerReady = false
    private var pendingVideoId: String? = null

    var onPlayStateChanged: ((Boolean) -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onTimeUpdated: ((currentMs: Long, durationMs: Long) -> Unit)? = null
    var onTrackCompleted: (() -> Unit)? = null

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    init {
        mainHandler.post {
            initWebView()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        try {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                addJavascriptInterface(WebAppInterface(), "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        Log.d(TAG, "YouTube player HTML page loaded")
                    }
                }
                webChromeClient = WebChromeClient()

                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; background-color: #090D16; overflow: hidden; display: flex; justify-content: center; align-items: center; height: 100vh; }
                            iframe { width: 100%; height: 100%; border: none; }
                        </style>
                    </head>
                    <body>
                        <div id="player"></div>
                        <script>
                            var tag = document.createElement('script');
                            tag.src = "https://www.youtube.com/iframe_api";
                            var firstScriptTag = document.getElementsByTagName('script')[0];
                            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);
                            
                            var player;
                            function onYouTubeIframeAPIReady() {
                                player = new YT.Player('player', {
                                    height: '100%',
                                    width: '100%',
                                    videoId: 'BddP6PYo2gs',
                                    playerVars: {
                                        'autoplay': 0,
                                        'playsinline': 1,
                                        'controls': 0,
                                        'disablekb': 1,
                                        'fs': 0,
                                        'rel': 0,
                                        'modestbranding': 1,
                                        'origin': 'https://www.youtube.com'
                                    },
                                    events: {
                                        'onReady': onPlayerReady,
                                        'onStateChange': onPlayerStateChange,
                                        'onError': onPlayerError
                                    }
                                });
                            }
                            function onPlayerReady(event) {
                                AndroidInterface.onPlayerReady();
                            }
                            function onPlayerStateChange(event) {
                                AndroidInterface.onStateChange(event.data);
                            }
                            function onPlayerError(event) {
                                AndroidInterface.onError(event.data);
                            }
                            function playVideo() { if(player && player.playVideo) player.playVideo(); }
                            function pauseVideo() { if(player && player.pauseVideo) player.pauseVideo(); }
                            function seekTo(sec) { if(player && player.seekTo) player.seekTo(sec, true); }
                            function loadVideo(id) { if(player && player.loadVideoById) player.loadVideoById(id); }
                            function setVolume(vol) { if(player && player.setVolume) player.setVolume(vol); }
                            
                            setInterval(function() {
                                if (player && player.getCurrentTime && player.getDuration) {
                                    var current = player.getCurrentTime();
                                    var duration = player.getDuration();
                                    AndroidInterface.onTimeUpdate(current, duration);
                                }
                            }, 400);
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YouTube WebView: ${e.message}")
        }
    }

    fun play(videoId: String) {
        _currentVideoId.value = videoId
        if (isPlayerReady) {
            evaluateJs("loadVideo('$videoId'); playVideo();")
        } else {
            pendingVideoId = videoId
        }
    }

    fun pause() {
        evaluateJs("pauseVideo();")
    }

    fun resume() {
        evaluateJs("playVideo();")
    }

    fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000f
        evaluateJs("seekTo($seconds);")
    }

    fun setVolume(volumeFraction: Float) {
        val vol100 = (volumeFraction.coerceIn(0f, 1f) * 100).toInt()
        evaluateJs("setVolume($vol100);")
    }

    private fun evaluateJs(js: String) {
        mainHandler.post {
            webView?.evaluateJavascript(js, null)
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPlayerReady() {
            Log.d(TAG, "YouTube Player Ready callback received")
            isPlayerReady = true
            pendingVideoId?.let { id ->
                pendingVideoId = null
                play(id)
            }
        }

        @JavascriptInterface
        fun onStateChange(state: Int) {
            // YT.PlayerState: -1 (unstarted), 0 (ended), 1 (playing), 2 (paused), 3 (buffering), 5 (video cued)
            Log.d(TAG, "YouTube Player onStateChange: $state")
            mainHandler.post {
                when (state) {
                    1 -> { // Playing
                        onBufferingChanged?.invoke(false)
                        onPlayStateChanged?.invoke(true)
                    }
                    2 -> { // Paused
                        onBufferingChanged?.invoke(false)
                        onPlayStateChanged?.invoke(false)
                    }
                    3 -> { // Buffering
                        onBufferingChanged?.invoke(true)
                    }
                    0 -> { // Ended
                        onPlayStateChanged?.invoke(false)
                        onTrackCompleted?.invoke()
                    }
                }
            }
        }

        @JavascriptInterface
        fun onTimeUpdate(currentSec: Double, durationSec: Double) {
            mainHandler.post {
                val currentMs = (currentSec * 1000).toLong()
                val durationMs = (durationSec * 1000).toLong()
                if (durationMs > 0) {
                    onTimeUpdated?.invoke(currentMs, durationMs)
                }
            }
        }

        @JavascriptInterface
        fun onError(errorCode: Int) {
            Log.e(TAG, "YouTube Player Error: code=$errorCode")
            mainHandler.post {
                onBufferingChanged?.invoke(false)
            }
        }
    }

    fun onDestroy() {
        mainHandler.post {
            try {
                webView?.destroy()
                webView = null
            } catch (_: Exception) {}
        }
    }
}
