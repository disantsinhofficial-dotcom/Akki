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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.VoiceState
import com.example.service.OfflineSpeechLanguage
import com.example.service.OfflineVoskStatus
import com.example.ui.theme.BorderHighlight
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

@Composable
fun VoiceSettingsScreen(
    currentWakeWord: String,
    wakeWordPresets: List<String>,
    onSelectWakeWord: (String) -> Unit,
    isHandsFreeModeActive: Boolean,
    onToggleHandsFreeMode: (Boolean) -> Unit,
    isAudioChimeEnabled: Boolean,
    onToggleAudioChime: (Boolean) -> Unit,
    onTestChime: () -> Unit,
    isHybridModeActive: Boolean,
    onToggleHybridMode: (Boolean) -> Unit,
    shakeSensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isBubbleVisible: Boolean,
    onToggleBubble: () -> Unit,
    onSimulateVoiceCommand: (String) -> Unit,
    onStartVoiceListening: () -> Unit,
    offlineVoskEnabled: Boolean,
    offlineVoskStatus: OfflineVoskStatus,
    onOfflineVoskEnabledChange: (Boolean) -> Unit,
    onOfflineLanguageChange: (OfflineSpeechLanguage) -> Unit,
    onDownloadOfflineModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var customWakeWordInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("voice_settings_screen")
    ) {
        // Section 1: Hands-Free Earbuds & Pocket Mode (Featured Primary Mode)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MidnightSurface,
                            MidnightElevated
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    color = if (isHandsFreeModeActive) NeonTurquoise else BorderSlate,
                    shape = RoundedCornerShape(18.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NeonTurquoise.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headset,
                                contentDescription = null,
                                tint = NeonTurquoise,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Hands-Free Earbuds Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NeonTurquoise.copy(alpha = 0.25f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isHandsFreeModeActive) "ACTIVE" else "OFF",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isHandsFreeModeActive) NeonTurquoise else TextMuted
                                    )
                                }
                            }
                            Text(
                                text = "Always listening for \"$currentWakeWord\" while phone is in pocket",
                                fontSize = 11.sp,
                                color = if (isHandsFreeModeActive) NeonTurquoise else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isHandsFreeModeActive,
                        onCheckedChange = onToggleHandsFreeMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonTurquoise,
                            checkedTrackColor = NeonTurquoise.copy(alpha = 0.35f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BorderSlate
                        ),
                        modifier = Modifier.testTag("switch_hands_free_mode")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MidnightBlack.copy(alpha = 0.6f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "💡 Standby Mode: Mic background me rehta hai par listening tabhi activate hoti hai jab aap \"$currentWakeWord\" bolte hain. Agar dobara \"$currentWakeWord\" bolenge toh listening turant OFF ho jayegi, ya 12 seconds me bina command ke auto-off ho jayegi!",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))


            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Wake-Word Configuration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeonTurquoise.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsVoice,
                            contentDescription = null,
                            tint = NeonTurquoise,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Custom Wake-Word Engine",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Trigger command: \"$currentWakeWord\"",
                            fontSize = 11.sp,
                            color = NeonTurquoise
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "SELECT WAKE-WORD PRESET",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(wakeWordPresets) { preset ->
                        val isSelected = currentWakeWord.equals(preset, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonTurquoise else MidnightElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonTurquoise else BorderSlate,
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { onSelectWakeWord(preset) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("wake_word_preset_$preset")
                        ) {
                            Text(
                                text = preset,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MidnightBlack else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "CUSTOM WAKE-WORD (TYPE ANY NAME / PHRASE)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Custom wake-word input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customWakeWordInput,
                        onValueChange = { customWakeWordInput = it },
                        placeholder = { Text("e.g. Hey Buddy, Jarvis, Akki, Suno", color = TextMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MidnightElevated,
                            unfocusedContainerColor = MidnightElevated,
                            focusedBorderColor = NeonTurquoise,
                            unfocusedBorderColor = BorderSlate,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_custom_wake_word")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (customWakeWordInput.isNotBlank()) {
                                onSelectWakeWord(customWakeWordInput.trim())
                                customWakeWordInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonTurquoise,
                            contentColor = MidnightBlack
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("btn_save_wake_word")
                    ) {
                        Text("Save & Apply", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Active wake-word: \"$currentWakeWord\" (saved in memory & storage)",
                    fontSize = 10.sp,
                    color = NeonTurquoise
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Smart Battery & Mic Optimization (Hybrid Sensor Mode)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonViolet.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = NeonViolet,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Shake to Listen (Hybrid Sensor)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Battery-saver mode: activates mic only when shaken",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isHybridModeActive,
                        onCheckedChange = onToggleHybridMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonViolet,
                            checkedTrackColor = NeonViolet.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BorderSlate
                        ),
                        modifier = Modifier.testTag("switch_hybrid_mode")
                    )
                }

                if (isHybridModeActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Shake Sensitivity Threshold: ${shakeSensitivity.toInt()} m/s²",
                        fontSize = 11.sp,
                        color = NeonViolet
                    )
                    Slider(
                        value = shakeSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 8f..25f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonViolet,
                            activeTrackColor = NeonViolet,
                            inactiveTrackColor = BorderHighlight
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("slider_sensitivity")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 4: Floating Bubble & Offline Indicator
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
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Floating Mic Bubble",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Draggable widget on all screens",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isBubbleVisible,
                        onCheckedChange = { onToggleBubble() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonPink,
                            checkedTrackColor = NeonPink.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = BorderSlate
                        ),
                        modifier = Modifier.testTag("switch_floating_bubble")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonTurquoise,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Offline NLP Engine: Active (0ms zero latency local processing)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeonTurquoise
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Offline speech recognition: Hindi / Indian English Vosk models
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "OFFLINE VOICE RECOGNITION (VOSK)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = NeonTurquoise
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Internet ke bina voice commands. Selected language model ek baar download hoga; recognition device par chalega.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use Vosk offline engine", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Switch(checked = offlineVoskEnabled, onCheckedChange = onOfflineVoskEnabledChange)
                }
                Text("Language / भाषा", color = TextSecondary, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfflineSpeechLanguage.values().forEach { language ->
                        val selected = offlineVoskStatus.language == language
                        OutlinedButton(
                            onClick = { onOfflineLanguageChange(language) },
                            enabled = !offlineVoskStatus.isDownloading,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selected) NeonTurquoise else TextSecondary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (selected) "✓ ${language.label}" else language.label, fontSize = 11.sp)
                        }
                    }
                }
                Text(
                    text = when {
                        offlineVoskStatus.isDownloading -> "Downloading model… ${offlineVoskStatus.progress}%"
                        offlineVoskStatus.isDownloaded -> "✓ ${offlineVoskStatus.language.label} model downloaded"
                        else -> "${offlineVoskStatus.language.label} model not downloaded"
                    },
                    color = if (offlineVoskStatus.isDownloaded) NeonTurquoise else TextSecondary,
                    fontSize = 11.sp
                )
                if (!offlineVoskStatus.error.isNullOrBlank()) {
                    Text(offlineVoskStatus.error, color = NeonPink, fontSize = 11.sp)
                }
                Button(
                    onClick = onDownloadOfflineModel,
                    enabled = !offlineVoskStatus.isDownloaded && !offlineVoskStatus.isDownloading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonTurquoise,
                        contentColor = MidnightBlack
                    )
                ) {
                    Text(if (offlineVoskStatus.isDownloading) "Downloading…" else "Download selected language model")
                }
                if (offlineVoskEnabled && !offlineVoskStatus.isDownloaded) {
                    Text("Offline mode on hai — listening start karne se pehle model download karein.", color = NeonPink, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 5: NLP Voice Commands Reference & Quick Simulators
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "SUPPORTED NATURAL LANGUAGE COMMANDS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                val sampleCommands = listOf(
                    "Next song" to "Skip track (Aage chalo / Agla)",
                    "Previous song" to "Go back (Peeche jao / Pichla)",
                    "Volume up" to "Hardware +10% louder (Awaaz badhao)",
                    "Volume down" to "Hardware -10% softer (Awaaz kam karo)",
                    "Pause" to "Hold playback (Ruko / Rok do)",
                    "Play" to "Resume music (Chalao / Shuru karo)",
                    "Bass boost" to "Boost equalizer low-end",
                    "Play lo-fi" to "Play Lo-Fi stream directly"
                )

                sampleCommands.forEach { (cmd, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MidnightElevated)
                            .clickable { onSimulateVoiceCommand(cmd) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\"$cmd\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTurquoise
                        )
                        Text(
                            text = desc,
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onStartVoiceListening,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonTurquoise,
                        contentColor = MidnightBlack
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_trigger_voice_settings")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Voice Listening Now", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
