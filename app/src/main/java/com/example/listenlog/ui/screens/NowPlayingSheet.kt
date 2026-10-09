package com.example.listenlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.listenlog.data.Track
import com.example.listenlog.player.PState
import com.example.listenlog.ui.components.AlbumArtTile
import com.example.listenlog.ui.components.formatDuration
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.DarkBackground
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    state: PState,
    queueTracks: List<Track>,
    onDismiss: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onEditInfo: (Track) -> Unit,
    onJumpToQueueIndex: (index: Int) -> Unit,
    onRemoveFromQueue: (index: Int) -> Unit
) {
    val currentTrack = remember(queueTracks, state.currentPath) {
        queueTracks.find { it.path == state.currentPath }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Header with dismiss arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Text("▼", color = Color.White, fontSize = 20.sp)
                }
                Text(
                    text = "PLAYING FROM QUEUE",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                if (currentTrack != null) {
                    IconButton(onClick = { onEditInfo(currentTrack) }) {
                        Text("✎", color = Color.White, fontSize = 18.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Album Cover Art
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtTile(
                    title = state.title.ifEmpty { "ListenLog" },
                    size = 280.dp,
                    cornerRadius = 16.dp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Song Title & Artist + Heart & Add to Playlist buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifEmpty { "No Track" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.artist.ifEmpty { "Unknown Artist" },
                        color = TextSecondary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                currentTrack?.let { track ->
                    IconButton(onClick = { onToggleLike(track) }) {
                        Text(if (track.isLiked) "❤️" else "🤍", fontSize = 22.sp)
                    }
                    IconButton(onClick = { onAddToPlaylist(track) }) {
                        Text("＋", color = SpotifyGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Seek bar with elapsed and remaining times
            val sliderPosition = state.positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L)).toFloat()
            Slider(
                value = sliderPosition,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..(state.durationMs.coerceAtLeast(1L)).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentOrange,
                    activeTrackColor = AccentOrange,
                    inactiveTrackColor = Color.DarkGray
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDuration(state.positionMs), color = TextSecondary, fontSize = 12.sp)
                Text(formatDuration(state.durationMs), color = TextSecondary, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Playback Controls: Shuffle, Prev, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle toggle
                IconButton(onClick = onToggleShuffle) {
                    Text(
                        text = if (state.shuffleMode) "🔀" else "➡️",
                        fontSize = 20.sp,
                        color = if (state.shuffleMode) AccentOrange else Color.Gray
                    )
                }

                // Previous
                IconButton(onClick = onPrevClick) {
                    Text("⏮", color = Color.White, fontSize = 28.sp)
                }

                // Play / Pause Circle
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(AccentOrange)
                ) {
                    Text(
                        text = if (state.isPlaying) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 26.sp
                    )
                }

                // Next
                IconButton(onClick = onNextClick) {
                    Text("⏭", color = Color.White, fontSize = 28.sp)
                }

                // Repeat toggle (OFF -> ALL -> ONE)
                IconButton(onClick = onToggleRepeat) {
                    val repeatSymbol = when (state.repeatMode) {
                        Player.REPEAT_MODE_ONE -> "🔂"
                        Player.REPEAT_MODE_ALL -> "🔁"
                        else -> "➡️"
                    }
                    val repeatColor = if (state.repeatMode != Player.REPEAT_MODE_OFF) AccentOrange else Color.Gray
                    Text(text = repeatSymbol, fontSize = 20.sp, color = repeatColor)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFF222222))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Up Next (${queueTracks.size})",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            // Full editable queue list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {
                itemsIndexed(queueTracks) { idx, track ->
                    val isPlayingItem = state.currentPath == track.path
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPlayingItem) AccentOrange.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { onJumpToQueueIndex(idx) }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = if (isPlayingItem) AccentOrange else TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.width(24.dp)
                        )
                        AlbumArtTile(title = track.title, size = 36.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = if (isPlayingItem) AccentOrange else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveFromQueue(idx) }) {
                            Text("✕", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
