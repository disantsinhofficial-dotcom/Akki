package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val VoiceBeatsDarkColorScheme = darkColorScheme(
    primary = NeonTurquoise,
    onPrimary = MidnightBlack,
    primaryContainer = MidnightSurfaceVariant,
    onPrimaryContainer = NeonTurquoise,
    secondary = NeonViolet,
    onSecondary = TextPrimary,
    secondaryContainer = MidnightElevated,
    onSecondaryContainer = NeonCyan,
    tertiary = NeonPink,
    onTertiary = TextPrimary,
    background = MidnightBlack,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BorderSlate,
    outlineVariant = BorderHighlight
)

@Composable
fun VoiceBeatsTheme(
    darkTheme: Boolean = true, // Default to futuristic dark aesthetic
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoiceBeatsDarkColorScheme,
        typography = Typography,
        content = content
    )
}
