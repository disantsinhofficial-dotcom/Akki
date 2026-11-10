package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.BorderSlate
import com.example.ui.theme.MidnightBlack
import com.example.ui.theme.MidnightElevated
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonTurquoise
import com.example.ui.theme.NeonViolet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PulsingVisualizerRing(
    artworkUrl: String?,
    isPlaying: Boolean,
    isListening: Boolean,
    visualizerBands: List<Float>,
    modifier: Modifier = Modifier
) {
    // Rotation transition for album disc
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Glow pulse animation
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Box(
        modifier = modifier
            .size(280.dp)
            .testTag("visualizer_ring_container"),
        contentAlignment = Alignment.Center
    ) {
        // Dynamic Radial Visualizer Bars Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2f) * 0.72f
            val barCount = 32

            for (i in 0 until barCount) {
                val angleDeg = (i * (360f / barCount))
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val bandIndex = i % visualizerBands.size
                val rawBand = visualizerBands.getOrElse(bandIndex) { 0.2f }
                val intensity = if (isListening) (rawBand * 1.4f).coerceIn(0.2f, 1f) else rawBand

                val barLength = (size.minDimension * 0.14f) * intensity * (if (isPlaying || isListening) 1f else 0.2f)

                val startX = center.x + (baseRadius * cos(angleRad)).toFloat()
                val startY = center.y + (baseRadius * sin(angleRad)).toFloat()

                val endX = center.x + ((baseRadius + barLength) * cos(angleRad)).toFloat()
                val endY = center.y + ((baseRadius + barLength) * sin(angleRad)).toFloat()

                val color = when {
                    isListening -> if (i % 2 == 0) NeonPink else NeonTurquoise
                    i < barCount / 3 -> NeonTurquoise
                    i < (barCount * 2) / 3 -> NeonViolet
                    else -> NeonPink
                }

                drawLine(
                    color = color.copy(alpha = if (isPlaying || isListening) 0.85f else 0.3f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // Outer Glow Ring
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isListening) NeonPink.copy(alpha = 0.35f * glowPulse)
                            else NeonTurquoise.copy(alpha = 0.25f * glowPulse),
                            Color.Transparent
                        )
                    )
                )
        )

        // Center Artwork / Vinyl Disc
        Box(
            modifier = Modifier
                .size(175.dp)
                .rotate(if (isPlaying) rotationAngle else 0f)
                .shadow(elevation = 16.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(MidnightElevated)
                .border(
                    width = 2.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(NeonTurquoise, NeonViolet, NeonPink, NeonTurquoise)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = "Album Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                // Vinyl Groove fallback aesthetic
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(MidnightBlack, MidnightElevated, MidnightBlack)
                            )
                        )
                        .border(1.dp, BorderSlate.copy(alpha = 0.6f), CircleShape)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, BorderSlate.copy(alpha = 0.4f), CircleShape)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music Disc",
                            tint = NeonTurquoise,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            // Center Spindle Hole
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MidnightBlack)
                    .border(2.dp, NeonTurquoise.copy(alpha = 0.8f), CircleShape)
            )
        }
    }
}
