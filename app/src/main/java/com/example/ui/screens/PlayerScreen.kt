package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.PlayerState
import com.example.service.RepeatMode
import com.example.ui.components.PulsingVisualizerRing
import com.example.ui.theme.BorderHighlight
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun PlayerScreen(
    playerState: PlayerState,
    visualizerBands: List<Float>,
    isListening: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onTriggerVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playerState.currentTrack
    val scrollState = rememberScrollState()

    var isUserDraggingSlider by remember { androidx.compose.runtime.mutableStateOf(false) }
    var userSliderPosition by remember { mutableFloatStateOf(0f) }

    val currentMs = if (isUserDraggingSlider) userSliderPosition.toLong() else playerState.currentPositionMs
    val totalMs = playerState.durationMs.coerceAtLeast(1L)
    val sliderProgress = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Visualizer Ring with Album Cover
        Spacer(modifier = Modifier.height(10.dp))
        PulsingVisualizerRing(
            artworkUrl = track?.artworkUrl,
            isPlaying = playerState.isPlaying,
            isListening = isListening,
            visualizerBands = visualizerBands
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Track Information & Tags
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val badgeColor = when {
                track?.isYouTube == true -> NeonPink
                track?.isOnline == true -> NeonTurquoise
                else -> NeonViolet
            }
            val badgeText = when {
                track?.isYouTube == true -> "▶ YOUTUBE AUDIO"
                track?.isOnline == true -> "ONLINE STREAM"
                else -> "LOCAL MP3"
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.2f))
                    .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(MidnightElevated)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = track?.genre ?: "Music",
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track?.bitRate ?: "320 kbps",
                fontSize = 10.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = track?.title ?: "No Track Selected",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = track?.artist ?: "Akki Beat AI Player",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextSecondary
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Scrubber
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = sliderProgress,
                onValueChange = { fraction ->
                    isUserDraggingSlider = true
                    userSliderPosition = fraction * totalMs
                },
                onValueChangeFinished = {
                    isUserDraggingSlider = false
                    onSeekTo(userSliderPosition.toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = NeonTurquoise,
                    activeTrackColor = NeonTurquoise,
                    inactiveTrackColor = BorderSlate
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_progress_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatMs(currentMs),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Text(
                    text = formatMs(totalMs),
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Transport Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle Button
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(44.dp).testTag("btn_shuffle")
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playerState.isShuffle) NeonTurquoise else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Previous Button
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MidnightElevated)
                    .border(1.dp, BorderSlate, CircleShape)
                    .testTag("btn_skip_prev")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Central Play/Pause Button
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .shadow(16.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(NeonTurquoise, NeonCyan)
                        )
                    )
                    .clickable { onTogglePlayPause() }
                    .testTag("btn_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MidnightBlack,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = MidnightBlack,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Next Button
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MidnightElevated)
                    .border(1.dp, BorderSlate, CircleShape)
                    .testTag("btn_skip_next")
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Repeat Button
            IconButton(
                onClick = onCycleRepeat,
                modifier = Modifier.size(44.dp).testTag("btn_repeat")
            ) {
                Icon(
                    imageVector = if (playerState.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (playerState.repeatMode != RepeatMode.OFF) NeonTurquoise else TextMuted,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Volume Control Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(32.dp).testTag("btn_toggle_mute")
            ) {
                Icon(
                    imageVector = if (playerState.isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeDown,
                    contentDescription = "Mute Toggle",
                    tint = if (playerState.isMuted) NeonPink else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Slider(
                value = if (playerState.isMuted) 0f else playerState.volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonTurquoise,
                    activeTrackColor = NeonTurquoise,
                    inactiveTrackColor = BorderHighlight
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("volume_slider")
            )

            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume Up",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Feature Cards Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Equalizer Quick Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .clickable { onOpenEqualizer() }
                    .padding(12.dp)
                    .testTag("card_quick_equalizer")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Equalizer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = playerState.activePreset, fontSize = 10.sp, color = NeonTurquoise)
                    }
                }
            }

            // Voice Command Quick Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightSurface)
                    .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                    .clickable { onTriggerVoice() }
                    .padding(12.dp)
                    .testTag("card_quick_voice")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeonTurquoise.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = NeonTurquoise,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Tap to Speak", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "Natural NLP", fontSize = 10.sp, color = NeonViolet)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
