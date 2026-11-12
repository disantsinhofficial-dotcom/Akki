package com.example

import android.Manifest
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.VoiceState
import com.example.ui.components.DraggableVoiceBubble
import com.example.ui.components.HeaderBar
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.VoiceTranscriptOverlay
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.VoiceSettingsScreen
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoiceBeatsTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.VoiceBeatsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceBeatsViewModel by viewModels()
    private var mediaSession: MediaSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupMediaSession()

        setContent {
            VoiceBeatsTheme {
                VoiceBeatsApp(viewModel = viewModel)
            }
        }
    }

    private fun setupMediaSession() {
        try {
            mediaSession = MediaSession(this, "VoiceBeatsMediaSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setPlaybackState(
                    PlaybackState.Builder()
                        .setActions(
                            PlaybackState.ACTION_PLAY or
                                    PlaybackState.ACTION_PAUSE or
                                    PlaybackState.ACTION_PLAY_PAUSE or
                                    PlaybackState.ACTION_SKIP_TO_NEXT or
                                    PlaybackState.ACTION_SKIP_TO_PREVIOUS
                        )
                        .setState(PlaybackState.STATE_PLAYING, 0, 1.0f)
                        .build()
                )
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() {
                        viewModel.playerManager.resume()
                    }

                    override fun onPause() {
                        viewModel.playerManager.pause()
                    }

                    override fun onSkipToNext() {
                        viewModel.playerManager.skipToNext()
                    }

                    override fun onSkipToPrevious() {
                        viewModel.playerManager.skipToPrevious()
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                    viewModel.playerManager.togglePlayPause()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    viewModel.playerManager.skipToNext()
                                    return true
                                }
                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    viewModel.playerManager.skipToPrevious()
                                    return true
                                }
                            }
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }
        } catch (e: Exception) {
            // MediaSession fallback
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.hybridSensorManager.registerListener()
        viewModel.refreshMusicPlayers()
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            if (viewModel.voiceEngine.isHandsFreeModeActive.value) {
                viewModel.voiceEngine.startListening()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.hybridSensorManager.unregisterListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
    }
}

@Composable
fun VoiceBeatsApp(viewModel: VoiceBeatsViewModel) {
    val context = LocalContext.current
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val playerState by viewModel.playerManager.playerState.collectAsStateWithLifecycle()
    val visualizerBands by viewModel.playerManager.visualizerBands.collectAsStateWithLifecycle()
    val bands by viewModel.playerManager.bands.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceEngine.voiceState.collectAsStateWithLifecycle()
    val wakeWord by viewModel.voiceEngine.wakeWord.collectAsStateWithLifecycle()
    val wakeWordPresets by viewModel.voiceEngine.wakeWordPresets.collectAsStateWithLifecycle()
    val isHandsFreeMode by viewModel.voiceEngine.isHandsFreeModeActive.collectAsStateWithLifecycle()
    val isAudioChimeEnabled by viewModel.voiceEngine.isAudioChimeEnabled.collectAsStateWithLifecycle()
    val isHybridMode by viewModel.hybridSensorManager.isHybridModeActive.collectAsStateWithLifecycle()
    val shakeSensitivity by viewModel.hybridSensorManager.shakeSensitivity.collectAsStateWithLifecycle()
    val installedMusicPlayers by viewModel.installedMusicPlayers.collectAsStateWithLifecycle()
    val isAutoDetectMusicPlayer by viewModel.isAutoDetectMusicPlayer.collectAsStateWithLifecycle()
    val selectedMusicPlayerPackage by viewModel.selectedMusicPlayerPackage.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
    val filteredLocalTracks by viewModel.filteredLocalTracks.collectAsStateWithLifecycle()
    val hasStoragePermission by viewModel.hasStoragePermission.collectAsStateWithLifecycle()
    val isScanningLocal by viewModel.isScanningLocal.collectAsStateWithLifecycle()

    val bubbleX by viewModel.bubbleOffsetX.collectAsStateWithLifecycle()
    val bubbleY by viewModel.bubbleOffsetY.collectAsStateWithLifecycle()
    val isBubbleVisible by viewModel.isBubbleVisible.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()

    // Request permissions on launch
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            viewModel.showStatusBanner("🎙 Microphone access enabled: Hands-Free Earbuds listening ready")
            if (viewModel.voiceEngine.isHandsFreeModeActive.value) {
                viewModel.voiceEngine.startListening()
            }
        }
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (storageGranted) {
            viewModel.scanLocalMusic()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    // Back handling: If on secondary tab, back returns to Player
    if (currentTab != AppTab.PLAYER) {
        BackHandler {
            viewModel.selectTab(AppTab.PLAYER)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack),
        topBar = {
            HeaderBar(
                voiceState = voiceState,
                wakeWord = wakeWord,
                isHandsFreeModeActive = isHandsFreeMode,
                isHybridModeActive = isHybridMode,
                onMicClick = {
                    if (voiceState is VoiceState.Listening) {
                        viewModel.voiceEngine.stopListening()
                    } else {
                        viewModel.voiceEngine.startListening()
                    }
                },
                onWakeWordClick = {
                    viewModel.selectTab(AppTab.VOICE_SETTINGS)
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MidnightBlack)
                    .navigationBarsPadding()
            ) {
                // Persistent mini-player when browsing Library, Equalizer, or Settings
                if (currentTab != AppTab.PLAYER && playerState.currentTrack != null) {
                    MiniPlayerBar(
                        currentTrack = playerState.currentTrack,
                        isPlaying = playerState.isPlaying,
                        onBarClick = { viewModel.selectTab(AppTab.PLAYER) },
                        onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                        onSkipNext = { viewModel.playerManager.skipToNext() }
                    )
                }

                NavigationBar(
                    containerColor = MidnightSurface,
                    contentColor = TextPrimary,
                    modifier = Modifier
                        .border(1.dp, BorderSlate)
                        .testTag("bottom_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == AppTab.PLAYER,
                        onClick = { viewModel.selectTab(AppTab.PLAYER) },
                        icon = {
                            Icon(Icons.Default.MusicNote, contentDescription = "Now Playing")
                        },
                        label = { Text("Player", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBlack,
                            selectedTextColor = NeonTurquoise,
                            indicatorColor = NeonTurquoise,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_player")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.LIBRARY,
                        onClick = { viewModel.selectTab(AppTab.LIBRARY) },
                        icon = {
                            Icon(Icons.Default.QueueMusic, contentDescription = "Library")
                        },
                        label = { Text("Library", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBlack,
                            selectedTextColor = NeonTurquoise,
                            indicatorColor = NeonTurquoise,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_library")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.EQUALIZER,
                        onClick = { viewModel.selectTab(AppTab.EQUALIZER) },
                        icon = {
                            Icon(Icons.Default.Equalizer, contentDescription = "Equalizer")
                        },
                        label = { Text("Equalizer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBlack,
                            selectedTextColor = NeonViolet,
                            indicatorColor = NeonViolet,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_equalizer")
                    )

                    NavigationBarItem(
                        selected = currentTab == AppTab.VOICE_SETTINGS,
                        onClick = { viewModel.selectTab(AppTab.VOICE_SETTINGS) },
                        icon = {
                            Icon(Icons.Default.SettingsVoice, contentDescription = "Voice Engine")
                        },
                        label = { Text("Voice AI", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MidnightBlack,
                            selectedTextColor = NeonPink,
                            indicatorColor = NeonPink,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_item_voice_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Screen Content
            when (currentTab) {
                AppTab.PLAYER -> {
                    PlayerScreen(
                            playerState = playerState,
                            visualizerBands = visualizerBands,
                            isListening = voiceState is VoiceState.Listening,
                            onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                            onSkipNext = { viewModel.playerManager.skipToNext() },
                            onSkipPrevious = { viewModel.playerManager.skipToPrevious() },
                            onSeekTo = { viewModel.playerManager.seekTo(it) },
                            onVolumeChange = { viewModel.playerManager.setVolume(it) },
                            onToggleMute = { viewModel.playerManager.toggleMute() },
                            onToggleShuffle = { viewModel.playerManager.toggleShuffle() },
                            onCycleRepeat = { viewModel.playerManager.cycleRepeatMode() },
                            onOpenEqualizer = { viewModel.selectTab(AppTab.EQUALIZER) },
                            onTriggerVoice = { viewModel.voiceEngine.startListening() }
                        )
                }

                AppTab.LIBRARY -> {
                    LibraryScreen(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        selectedGenre = selectedGenre,
                        onSelectGenre = { viewModel.setSelectedGenre(it) },
                        genres = viewModel.genres,
                        tracks = filteredLocalTracks,
                        currentPlayingTrack = playerState.currentTrack,
                        isPlaying = playerState.isPlaying,
                        hasStoragePermission = hasStoragePermission,
                        onRequestStoragePermission = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms)
                        },
                        isScanningLocal = isScanningLocal,
                        onScanLocal = { viewModel.scanLocalMusic() },
                        onPickedAudioFiles = { uris -> viewModel.addPickedAudioUris(uris) },
                        onTrackSelect = { track, list -> viewModel.playTrackFromList(track, list) }
                    )
                }

                AppTab.EQUALIZER -> {
                    EqualizerScreen(
                        bands = bands,
                        activePreset = playerState.activePreset,
                        availablePresets = viewModel.playerManager.availablePresets,
                        bassBoostStrength = playerState.bassBoostStrength,
                        virtualizerStrength = playerState.virtualizerStrength,
                        onBandGainChange = { band, gain -> viewModel.playerManager.setBandGain(band, gain) },
                        onPresetSelect = { viewModel.playerManager.applyPreset(it) },
                        onBassBoostChange = { viewModel.playerManager.setBassBoostStrength(it) },
                        onVirtualizerChange = { viewModel.playerManager.setVirtualizerStrength(it) }
                    )
                }

                AppTab.VOICE_SETTINGS -> {
                    VoiceSettingsScreen(
                        currentWakeWord = wakeWord,
                        wakeWordPresets = wakeWordPresets,
                        onSelectWakeWord = { viewModel.setWakeWord(it) },
                        isHandsFreeModeActive = isHandsFreeMode,
                        onToggleHandsFreeMode = { viewModel.setHandsFreeMode(it) },
                        isAudioChimeEnabled = isAudioChimeEnabled,
                        onToggleAudioChime = { viewModel.setAudioChimeEnabled(it) },
                        onTestChime = { viewModel.testChime() },
                        isHybridModeActive = isHybridMode,
                        onToggleHybridMode = { viewModel.hybridSensorManager.setHybridMode(it) },
                        shakeSensitivity = shakeSensitivity,
                        onSensitivityChange = { viewModel.hybridSensorManager.setSensitivity(it) },
                        isBubbleVisible = isBubbleVisible,
                        onToggleBubble = { viewModel.toggleBubbleVisibility() },
                        onSimulateVoiceCommand = { viewModel.voiceEngine.processVoiceInput(it) },
                        onStartVoiceListening = { viewModel.voiceEngine.startListening() },
                        installedMusicPlayers = installedMusicPlayers,
                        isAutoDetectMusicPlayer = isAutoDetectMusicPlayer,
                        selectedMusicPlayerPackage = selectedMusicPlayerPackage,
                        onRefreshMusicPlayers = { viewModel.refreshMusicPlayers() },
                        onSetAutoDetectMusicPlayer = { viewModel.setAutoDetectMusicPlayer(it) },
                        onSelectMusicPlayer = { viewModel.selectMusicPlayer(it) },
                        onOpenNotificationAccess = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    )
                }
            }

            // Status feedback banner
            AnimatedVisibility(
                visible = statusBanner != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                statusBanner?.let { msg ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MidnightElevated)
                            .border(1.dp, NeonTurquoise.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonTurquoise,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = msg,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // HUD Voice Transcript Overlay when active
            VoiceTranscriptOverlay(
                voiceState = voiceState,
                onDismiss = { viewModel.voiceEngine.stopListening() },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Persistent Draggable Floating Voice Bubble Widget
            if (isBubbleVisible) {
                DraggableVoiceBubble(
                    voiceState = voiceState,
                    offsetX = bubbleX,
                    offsetY = bubbleY,
                    onDrag = { dx, dy -> viewModel.updateBubblePosition(dx, dy) },
                    onClick = {
                        if (voiceState is VoiceState.Listening) {
                            viewModel.voiceEngine.stopListening()
                        } else {
                            viewModel.voiceEngine.startListening()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 90.dp, end = 20.dp)
                )
            }
        }
    }
}
