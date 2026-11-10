package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VoiceState
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HeaderBar(
    voiceState: VoiceState,
    wakeWord: String,
    isHandsFreeModeActive: Boolean = true,
    isHybridModeActive: Boolean,
    onMicClick: () -> Unit,
    onWakeWordClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening

    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val badgeColor by animateColorAsState(
        targetValue = when (voiceState) {
            is VoiceState.Listening -> NeonTurquoise
            is VoiceState.Processing -> NeonPink
            is VoiceState.Executed -> NeonViolet
            is VoiceState.Error -> NeonPink
            is VoiceState.Idle -> TextMuted
        },
        label = "badge_color"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MidnightSurface)
            .border(width = 1.dp, color = BorderSlate, shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("header_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Branding
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(listOf(NeonTurquoise, NeonViolet))
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AKKI BEAT",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonTurquoise.copy(alpha = 0.2f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "AI",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTurquoise
                        )
                    }
                }
                Text(
                    text = when {
                        isHandsFreeModeActive -> "👂 Earbuds Mode: \"$wakeWord\""
                        isHybridModeActive -> "⚡ Hybrid Sensor Mode"
                        else -> "🎙 Wake-Word Ready"
                    },
                    fontSize = 11.sp,
                    color = if (isHandsFreeModeActive) NeonTurquoise else TextSecondary
                )
            }

            // Quick Status Chips
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Wake word chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MidnightElevated)
                        .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                        .clickable { onWakeWordClick() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "\"$wakeWord\"",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonTurquoise
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mic State Button
                Box(
                    modifier = Modifier
                        .scale(if (isListening) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = if (isListening) 0.25f else 0.15f))
                        .border(1.5.dp, badgeColor, CircleShape)
                        .clickable { onMicClick() }
                        .padding(8.dp)
                        .testTag("header_mic_button")
                ) {
                    Icon(
                        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Voice Control",
                        tint = badgeColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
