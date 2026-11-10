package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
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
    selectedGenre: String,
    onSelectGenre: (String) -> Unit,
    genres: List<String>,
    onlineTracks: List<Track>,
    localTracks: List<Track>,
    currentPlayingTrack: Track?,
    isPlaying: Boolean,
    isScanningLocal: Boolean,
    onScanLocal: () -> Unit,
    onTrackSelect: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Sub-Tab Switcher: Online Streams vs Local Storage
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MidnightSurface)
                .border(1.dp, BorderSlate, RoundedCornerShape(14.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (currentSubTab == LibrarySubTab.ONLINE) {
                            Modifier.background(Brush.horizontalGradient(listOf(NeonTurquoise.copy(alpha = 0.3f), NeonViolet.copy(alpha = 0.3f))))
                        } else {
                            Modifier.background(MidnightSurface)
                        }
                    )
                    .clickable { onSelectSubTab(LibrarySubTab.ONLINE) }
                    .padding(vertical = 10.dp)
                    .testTag("tab_online_streams"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (currentSubTab == LibrarySubTab.ONLINE) NeonTurquoise else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Online Streams",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSubTab == LibrarySubTab.ONLINE) NeonTurquoise else TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (currentSubTab == LibrarySubTab.LOCAL) {
                            Modifier.background(Brush.horizontalGradient(listOf(NeonViolet.copy(alpha = 0.3f), NeonPink.copy(alpha = 0.3f))))
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
                        tint = if (currentSubTab == LibrarySubTab.LOCAL) NeonPink else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Local Storage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (currentSubTab == LibrarySubTab.LOCAL) NeonPink else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search songs, artists, genres...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = NeonTurquoise)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MidnightSurface,
                unfocusedContainerColor = MidnightSurface,
                focusedBorderColor = NeonTurquoise,
                unfocusedBorderColor = BorderSlate,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("library_search_input")
        )

        // Genre Filter Chips (only for online streams)
        if (currentSubTab == LibrarySubTab.ONLINE) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(genres) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) NeonTurquoise else MidnightSurface)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) NeonTurquoise else BorderSlate,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onSelectGenre(genre) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("genre_chip_$genre")
                    ) {
                        Text(
                            text = genre,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MidnightBlack else TextSecondary
                        )
                    }
                }
            }
        } else {
            // Local storage scanner header
            Spacer(modifier = Modifier.height(10.dp))
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
                    Text(
                        text = "Device Audio Files (${localTracks.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Scans MP3 storage & internal demo songs",
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonPink.copy(alpha = 0.2f))
                        .clickable { onScanLocal() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_rescan_storage")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isScanningLocal) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = NeonPink,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan",
                                tint = NeonPink,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isScanningLocal) "Scanning..." else "Re-Scan",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tracks List
        val displayTracks = if (currentSubTab == LibrarySubTab.ONLINE) onlineTracks else localTracks

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
                        text = "No tracks found",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "Try a different search term or scan device storage",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
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
                color = if (isCurrent) NeonTurquoise.copy(alpha = 0.8f) else BorderSlate,
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
                        if (isCurrent) NeonTurquoise else BorderHighlight,
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
                    color = if (isCurrent) NeonTurquoise else TextPrimary,
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
                        text = "• ${track.genre}",
                        fontSize = 10.sp,
                        color = TextMuted
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
                        tint = NeonTurquoise,
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
                    text = track.bitRate,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }
    }
}
