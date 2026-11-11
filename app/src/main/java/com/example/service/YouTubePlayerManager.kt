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
    private var pendingSearchQuery: String? = null

    var onPlayStateChanged: ((Boolean) -> Unit)? = null
    var onBufferingChanged: ((Boolean) -> Unit)? = null
    var onTimeUpdated: ((currentMs: Long, durationMs: Long) -> Unit)? = null
    var onTrackCompleted: (() -> Unit)? = null

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(ctx: Context): WebView {
        if (webView != null) return webView!!

        val wv = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

            addJavascriptInterface(WebAppInterface(), "AndroidInterface")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "YouTube player HTML page loaded: $url")
                }
            }
            webChromeClient = WebChromeClient()

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { margin: 0; background-color: #000; overflow: hidden; display: flex; justify-content: center; align-items: center; width: 100%; height: 100%; min-width: 200px; min-height: 200px; }
                        #player { width: 100%; height: 100%; min-width: 200px; min-height: 200px; border: none; }
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
                        var isReady = false;

                        function onYouTubeIframeAPIReady() {
                            player = new YT.Player('player', {
                                height: '100%',
                                width: '100%',
                                playerVars: {
                                    'autoplay': 0,
                                    'playsinline': 1,
                                    'controls': 1,
                                    'disablekb': 0,
                                    'fs': 1,
                                    'rel': 0,
                                    'enablejsapi': 1,
                                    'modestbranding': 1,
                                    'origin': window.location.origin
                                },
                                events: {
                                    'onReady': onPlayerReady,
                                    'onStateChange': onPlayerStateChange,
                                    'onError': onPlayerError,
                                    'onAutoplayBlocked': onPlayerAutoplayBlocked
                                }
                            });
                        }
                        function onPlayerReady(event) {
                            isReady = true;
                            AndroidInterface.onPlayerReady();
                        }
                        function onPlayerStateChange(event) {
                            AndroidInterface.onStateChange(event.data);
                        }
                        function onPlayerError(event) {
                            AndroidInterface.onError(event.data);
                        }
                        function onPlayerAutoplayBlocked() {
                            AndroidInterface.onAutoplayBlocked();
                        }
                        function playVideoId(id) {
                            if (isReady && player && player.loadVideoById) {
                                player.loadVideoById({'videoId': id, 'startSeconds': 0});
                                player.playVideo();
                            }
                        }
                        function playSearch(query) {
                            if (isReady && player && player.loadPlaylist) {
                                player.loadPlaylist({
                                    listType: 'search',
                                    list: query,
                                    index: 0
                                });
                                player.playVideo();
                            }
                        }
                        function pauseVideo() { if(player && player.pauseVideo) player.pauseVideo(); }
                        function resumeVideo() { if(player && player.playVideo) player.playVideo(); }
                        function seekToSec(sec) { if(player && player.seekTo) player.seekTo(sec, true); }
                        function setPlayerVolume(vol) { if(player && player.setVolume) player.setVolume(vol); }
                        
                        setInterval(function() {
                            if (isReady && player && player.getCurrentTime && player.getDuration) {
                                try {
                                    var cur = player.getCurrentTime() || 0;
                                    var dur = player.getDuration() || 0;
                                    AndroidInterface.onTimeUpdate(cur, dur);
                                } catch(e) {}
                            }
                        }, 500);
                    </script>
                </body>
                </html>
            """.trimIndent()

            loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
        }
        webView = wv
        return wv
    }

    fun play(videoId: String) {
        _currentVideoId.value = videoId
        if (isPlayerReady) {
            evaluateJs("playVideoId('$videoId');")
        } else {
            pendingVideoId = videoId
        }
    }

    fun playSearch(query: String) {
        val sanitized = query.replace("'", "\\'").replace("\"", "\\\"")
        _currentVideoId.value = "search:$sanitized"
        if (isPlayerReady) {
            evaluateJs("playSearch('$sanitized');")
        } else {
            pendingSearchQuery = sanitized
        }
    }

    fun pause() {
        evaluateJs("pauseVideo();")
    }

    fun resume() {
        evaluateJs("resumeVideo();")
    }

    fun seekTo(positionMs: Long) {
        val seconds = positionMs / 1000f
        evaluateJs("seekToSec($seconds);")
    }

    fun setVolume(volumeFraction: Float) {
        val vol100 = (volumeFraction.coerceIn(0f, 1f) * 100).toInt()
        evaluateJs("setPlayerVolume($vol100);")
    }

    private fun evaluateJs(js: String) {
        mainHandler.post {
            webView?.evaluateJavascript(js, null)
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onPlayerReady() {
            Log.d(TAG, "YouTube Player Ready callback received from Iframe API")
            isPlayerReady = true
            mainHandler.post {
                pendingVideoId?.let { id ->
                    pendingVideoId = null
                    play(id)
                } ?: pendingSearchQuery?.let { q ->
                    pendingSearchQuery = null
                    playSearch(q)
                }
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
        fun onAutoplayBlocked() {
            Log.w(TAG, "YouTube blocked autoplay; user must press the visible player control")
            mainHandler.post { onBufferingChanged?.invoke(false) }
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
