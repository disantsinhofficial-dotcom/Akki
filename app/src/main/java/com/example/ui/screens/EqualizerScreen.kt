package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.EqualizerBand
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
fun EqualizerScreen(
    bands: List<EqualizerBand>,
    activePreset: String,
    availablePresets: List<String>,
    bassBoostStrength: Short,
    virtualizerStrength: Short,
    onBandGainChange: (Short, Short) -> Unit,
    onPresetSelect: (String) -> Unit,
    onBassBoostChange: (Short) -> Unit,
    onVirtualizerChange: (Short) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("equalizer_screen")
    ) {
        // Top Header Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(NeonTurquoise, NeonViolet))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = MidnightBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Earbud Sound Optimizer",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Hardware DSP Equalizer & Bass Extension",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Preset Chips Selector
        Text(
            text = "EQUALIZER PRESETS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(availablePresets) { preset ->
                val isSelected = activePreset == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(Brush.horizontalGradient(listOf(NeonTurquoise, NeonCyan)))
                            } else {
                                Modifier.background(MidnightSurface)
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeonTurquoise else BorderSlate,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { onPresetSelect(preset) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("preset_chip_$preset")
                ) {
                    Text(
                        text = preset,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MidnightBlack else TextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5-Band Hardware Equalizer Sliders
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = NeonTurquoise,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "5-Band Frequency Gains",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "±12 dB",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonTurquoise
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Band Sliders
                bands.forEach { band ->
                    val gainDb = band.currentGainMb / 100 // in dB
                    val minDb = band.minGainMb / 100
                    val maxDb = band.maxGainMb / 100

                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000f} kHz"
                    } else {
                        "${band.centerFreqHz} Hz"
                    }

                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = if (gainDb > 0) "+$gainDb dB" else "$gainDb dB",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gainDb > 0) NeonTurquoise else if (gainDb < 0) NeonPink else TextSecondary
                            )
                        }

                        Slider(
                            value = band.currentGainMb.toFloat(),
                            onValueChange = { newVal ->
                                onBandGainChange(band.index, newVal.toInt().toShort())
                            },
                            valueRange = band.minGainMb.toFloat()..band.maxGainMb.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = NeonTurquoise,
                                activeTrackColor = NeonTurquoise,
                                inactiveTrackColor = BorderHighlight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("slider_band_${band.index}")
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bass Booster Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
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
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Bass Booster",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Sub-harmonic low-end boost",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "${(bassBoostStrength / 10)}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonPink
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = bassBoostStrength.toFloat(),
                    onValueChange = { onBassBoostChange(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPink,
                        activeTrackColor = NeonPink,
                        inactiveTrackColor = BorderHighlight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_bass_boost")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3D Virtualizer Surround Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
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
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(NeonViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SurroundSound,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "3D Virtualizer",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Spatial earbud surround stage",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Text(
                        text = "${(virtualizerStrength / 10)}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonViolet
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Slider(
                    value = virtualizerStrength.toFloat(),
                    onValueChange = { onVirtualizerChange(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonViolet,
                        activeTrackColor = NeonViolet,
                        inactiveTrackColor = BorderHighlight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("slider_virtualizer")
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
