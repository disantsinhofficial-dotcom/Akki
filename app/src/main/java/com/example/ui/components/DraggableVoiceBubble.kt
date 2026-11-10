package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.service.VoiceState
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import kotlin.math.roundToInt

@Composable
fun DraggableVoiceBubble(
    voiceState: VoiceState,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is VoiceState.Listening

    val infiniteTransition = rememberInfiniteTransition(label = "bubble_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.35f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 400 else 1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bubble_scale"
    )

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .testTag("draggable_voice_bubble"),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing wave
        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isListening) NeonPink.copy(alpha = 0.45f) else NeonTurquoise.copy(alpha = 0.25f),
                            NeonViolet.copy(alpha = 0.05f)
                        )
                    )
                )
        )

        // Floating Bubble Core Button
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(MidnightBlack, if (isListening) NeonPink else NeonTurquoise)
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(NeonTurquoise, NeonViolet, NeonPink, NeonTurquoise)
                    ),
                    shape = CircleShape
                )
                .clickable { onClick() }
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Assistant Bubble",
                tint = if (isListening) NeonPink else NeonTurquoise,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
