package com.example.listenlog.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.listenlog.data.Track
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun TrackRow(
    track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onPlayClick: () -> Unit,
    onTrackClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onEditInfo: () -> Unit,
    // Inline panel controls for currently playing track
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    onSeek: (Long) -> Unit = {},
    onPrev: () -> Unit = {},
    onNext: () -> Unit = {},
    onSeekBack10: () -> Unit = {},
    onSeekForward10: () -> Unit = {}
) {
    var expandedMenu by remember { mutableStateOf(false) }
    var inlinePanelExpanded by remember { mutableStateOf(false) }

    val backgroundColor = if (isCurrentTrack) {
        AccentOrange.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isCurrentTrack) {
                        inlinePanelExpanded = !inlinePanelExpanded
                    } else {
                        onTrackClick()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle play/pause button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCurrentTrack) AccentOrange else Color(0xFF333333))
            ) {
                Text(
                    text = if (isCurrentTrack && isPlaying) "⏸" else "▶",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            AlbumArtTile(title = track.title, size = 40.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // Track details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (isCurrentTrack) AccentOrange else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} · ${formatDuration(track.durationMs)}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Like heart icon
            IconButton(onClick = onToggleLike) {
                Text(
                    text = if (track.isLiked) "❤️" else "🤍",
                    fontSize = 16.sp
                )
            }

            // Overflow menu button
            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Text(text = "⋮", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false },
                    modifier = Modifier.background(Color(0xFF282828))
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Next", color = Color.White) },
                        onClick = { expandedMenu = false; onPlayNext() }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = Color.White) },
                        onClick = { expandedMenu = false; onAddToQueue() }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = Color.White) },
                        onClick = { expandedMenu = false; onAddToPlaylist() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (track.isLiked) "Unlike" else "Like", color = Color.White) },
                        onClick = { expandedMenu = false; onToggleLike() }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Info", color = Color.White) },
                        onClick = { expandedMenu = false; onEditInfo() }
                    )
                }
            }
        }

        // Inline expand panel when tapping currently playing track
        AnimatedVisibility(visible = isCurrentTrack && inlinePanelExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                val effectiveDuration = if (durationMs > 0) durationMs else track.durationMs
                val sliderPosition = currentPositionMs.coerceIn(0L, effectiveDuration).toFloat()

                Slider(
                    value = sliderPosition,
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..effectiveDuration.toFloat(),
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
                    Text(text = formatDuration(currentPositionMs), color = TextSecondary, fontSize = 12.sp)
                    Text(text = formatDuration(effectiveDuration), color = TextSecondary, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPrev) { Text("⏮", fontSize = 20.sp, color = Color.White) }
                    TextButton(onClick = onSeekBack10) { Text("-10s", fontSize = 14.sp, color = AccentOrange) }
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(AccentOrange)
                    ) {
                        Text(text = if (isPlaying) "⏸" else "▶", color = Color.White, fontSize = 18.sp)
                    }
                    TextButton(onClick = onSeekForward10) { Text("+10s", fontSize = 14.sp, color = AccentOrange) }
                    TextButton(onClick = onNext) { Text("⏭", fontSize = 20.sp, color = Color.White) }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
    }
}
