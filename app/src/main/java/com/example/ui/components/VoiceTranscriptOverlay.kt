package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VoiceState
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceTranscriptOverlay(
    voiceState: VoiceState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = voiceState !is VoiceState.Idle

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("voice_transcript_overlay")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MidnightElevated.copy(alpha = 0.95f))
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(NeonTurquoise, NeonViolet, NeonPink)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeonTurquoise.copy(alpha = 0.2f))
                                .border(1.dp, NeonTurquoise, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (voiceState) {
                                    is VoiceState.Listening -> Icons.Default.Mic
                                    is VoiceState.Processing -> Icons.Default.GraphicEq
                                    else -> Icons.Default.Mic
                                },
                                contentDescription = "Voice State Icon",
                                tint = NeonTurquoise,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (voiceState) {
                                is VoiceState.Listening -> "Voice Engine: Listening..."
                                is VoiceState.Processing -> "Parsing NLP Intent..."
                                is VoiceState.Executed -> "Voice Command Executed"
                                is VoiceState.Error -> "Voice Alert"
                                is VoiceState.Idle -> ""
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTurquoise
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (voiceState) {
                    is VoiceState.Listening -> {
                        Text(
                            text = "Speak now... e.g. \"Next song\", \"Volume up\", \"Awaaz badhao\", \"Play lo-fi\"",
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    is VoiceState.Processing -> {
                        Text(
                            text = "\"${voiceState.transcript}\"",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                    is VoiceState.Executed -> {
                        Column {
                            Text(
                                text = "\"${voiceState.rawQuery}\"",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonTurquoise.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = voiceState.message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeonTurquoise
                                )
                            }
                        }
                    }
                    is VoiceState.Error -> {
                        Text(
                            text = voiceState.message,
                            fontSize = 13.sp,
                            color = NeonPink
                        )
                    }
                    is VoiceState.Idle -> {}
                }
            }
        }
    }
}
