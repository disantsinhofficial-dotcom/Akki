package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Track
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
import com.example.viewmodel.LibrarySubTab

@Composable
fun LibraryScreen(
    currentSubTab: LibrarySubTab,
    onSelectSubTab: (LibrarySubTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    selectedGenre: String,
    onSelectGenre: (String) -> Unit,
    youtubeGenres: List<String>,
    streamGenres: List<String>,
    youtubeTracks: List<Track>,
    localTracks: List<Track>,
    streamTracks: List<Track>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    isSearchingYouTube: Boolean,
    hasStoragePermission: Boolean,
    onRequestStoragePermission: () -> Unit,
    isScanningLocal: Boolean,
    onScanLocal: () -> Unit,
    onPickedAudioFiles: (List<Uri>) -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Document File Picker for selecting audio files directly from phone storage / folders
    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onPickedAudioFiles(uris)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("library_screen")
    ) {
        // Sub-Tab Switcher: YouTube Music vs Local Storage vs Streams
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            // YouTube Music Tab
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (currentSubTab == LibrarySubTab.YOUTUBE) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(NeonPink.copy(alpha = 0.35f), NeonTurquoise.copy(alpha = 0.25f))
                                )
                            )
                        } else {
                            Modifier.background(MidnightSurface)
                        }
                    )
                    .clickable { onSelectSubTab(LibrarySubTab.YOUTUBE) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_youtube_music"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(NeonPink),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MidnightBlack,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "YouTube Music",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSubTab == LibrarySubTab.YOUTUBE) NeonPink else TextSecondary
                    )
                }
            }

            // Local Storage Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (currentSubTab == LibrarySubTab.LOCAL) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(NeonTurquoise.copy(alpha = 0.35f), NeonViolet.copy(alpha = 0.25f))
                                )
                            )
                        } else {
                            Modifier.background(MidnightSurface)
                        }
                    )
                    .clickable { onSelectSubTab(LibrarySubTab.LOCAL) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_local_storage"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (currentSubTab == LibrarySubTab.LOCAL) NeonTurquoise else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local MP3",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSubTab == LibrarySubTab.LOCAL) NeonTurquoise else TextSecondary
                    )
                }
            }

            // Streams Tab
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (currentSubTab == LibrarySubTab.STREAMS) {
                            Modifier.background(
                                Brush.horizontalGradient(
                                    listOf(NeonViolet.copy(alpha = 0.35f), NeonTurquoise.copy(alpha = 0.25f))
                                )
                            )
                        } else {
                            Modifier.background(MidnightSurface)
                        }
                    )
                    .clickable { onSelectSubTab(LibrarySubTab.STREAMS) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_streams"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (currentSubTab == LibrarySubTab.STREAMS) NeonViolet else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Radio",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSubTab == LibrarySubTab.STREAMS) NeonViolet else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar with instant Search Submit
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = when (currentSubTab) {
                        LibrarySubTab.YOUTUBE -> "Search YouTube songs, Bollywood, Punjabi..."
                        LibrarySubTab.LOCAL -> "Search device songs, artists..."
                        LibrarySubTab.STREAMS -> "Search streams, genres..."
                    },
                    color = TextMuted,
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (currentSubTab == LibrarySubTab.YOUTUBE) NeonPink else NeonTurquoise
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearchingYouTube) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit(searchQuery) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MidnightSurface,
                unfocusedContainerColor = MidnightSurface,
                focusedBorderColor = if (currentSubTab == LibrarySubTab.YOUTUBE) NeonPink else NeonTurquoise,
                unfocusedBorderColor = BorderSlate,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
        )

        // Subtab Content Header & Actions
        when (currentSubTab) {
            LibrarySubTab.YOUTUBE -> {
                Spacer(modifier = Modifier.height(10.dp))
                // YouTube Genre Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(youtubeGenres) { genre ->
                        val isSelected = selectedGenre == genre
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonPink else MidnightSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonPink else BorderSlate,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { onSelectGenre(genre) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("youtube_genre_$genre")
                        ) {
                            Text(
                                text = genre,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MidnightBlack else TextSecondary
                            )
                        }
                    }
                }
            }

            LibrarySubTab.LOCAL -> {
                Spacer(modifier = Modifier.height(10.dp))

                // Storage Permission Banner if not granted
                if (!hasStoragePermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MidnightElevated)
                            .border(1.dp, NeonTurquoise.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = NeonTurquoise,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Storage Permission Needed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Allow storage access to scan all local MP3 songs stored on your device, or directly select files from folders.",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = onRequestStoragePermission,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonTurquoise,
                                        contentColor = MidnightBlack
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("btn_grant_storage_permission")
                                ) {
                                    Text("Grant Permission", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        audioFilePickerLauncher.launch(
                                            arrayOf("audio/*", "application/ogg", "application/x-ogg")
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MidnightSurface,
                                        contentColor = NeonTurquoise
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("btn_select_files_no_perm")
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pick Songs", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Local Storage Toolbar (Re-Scan + Pick Audio Files)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MidnightSurface)
                            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Device Music (${localTracks.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Permission granted",
                                    tint = NeonTurquoise,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "Full storage access enabled",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Pick files from device storage / Downloads / SD Card
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonTurquoise.copy(alpha = 0.15f))
                                    .clickable {
                                        audioFilePickerLauncher.launch(
                                            arrayOf("audio/*", "application/ogg", "application/x-ogg")
                                        )
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("btn_pick_audio_files")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = NeonTurquoise, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Add Files", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NeonTurquoise)
                                }
                            }

                            // Re-Scan MediaStore
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MidnightElevated)
                                    .clickable { onScanLocal() }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("btn_rescan_storage")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isScanningLocal) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = NeonTurquoise, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan", fontSize = 11.sp, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }

            LibrarySubTab.STREAMS -> {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(streamGenres) { genre ->
                        val isSelected = selectedGenre == genre
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonViolet else MidnightSurface)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonViolet else BorderSlate,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable { onSelectGenre(genre) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("stream_genre_$genre")
                        ) {
                            Text(
                                text = genre,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MidnightBlack else TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tracks List
        val displayTracks = when (currentSubTab) {
            LibrarySubTab.YOUTUBE -> youtubeTracks
            LibrarySubTab.LOCAL -> localTracks
            LibrarySubTab.STREAMS -> streamTracks
        }

        if (displayTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (currentSubTab == LibrarySubTab.LOCAL) "No local songs found" else "No tracks found",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (currentSubTab == LibrarySubTab.LOCAL)
                            "Tap 'Add Files' to select audio files from your folders"
                        else "Search songs by title or artist",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    if (currentSubTab == LibrarySubTab.LOCAL) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                audioFilePickerLauncher.launch(
                                    arrayOf("audio/*", "application/ogg", "application/x-ogg")
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonTurquoise,
                                contentColor = MidnightBlack
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Songs From Storage", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tracks_lazy_column"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(displayTracks, key = { it.id }) { track ->
                    val isCurrent = currentPlayingTrack?.id == track.id
                    TrackItemRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isPlaying && isCurrent,
                        onClick = { onTrackSelect(track, displayTracks) }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackItemRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isCurrent) MidnightElevated else MidnightSurface)
            .border(
                width = 1.dp,
                color = if (isCurrent) (if (track.isYouTube) NeonPink else NeonTurquoise).copy(alpha = 0.8f) else BorderSlate,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(10.dp)
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MidnightBlack)
                    .border(
                        1.dp,
                        if (isCurrent) (if (track.isYouTube) NeonPink else NeonTurquoise) else BorderHighlight,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = track.artworkUrl,
                        contentDescription = track.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) NeonTurquoise else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) (if (track.isYouTube) NeonPink else NeonTurquoise) else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (track.isYouTube) "• YouTube" else "• ${track.genre}",
                        fontSize = 10.sp,
                        color = if (track.isYouTube) NeonPink else TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Duration & State Indicator
            Column(horizontalAlignment = Alignment.End) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = if (track.isYouTube) NeonPink else NeonTurquoise,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = track.durationFormatted,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (track.isYouTube) "YouTube HQ" else track.bitRate,
                    fontSize = 9.sp,
                    color = if (track.isYouTube) NeonPink.copy(alpha = 0.8f) else TextMuted
                )
            }
        }
    }
}
