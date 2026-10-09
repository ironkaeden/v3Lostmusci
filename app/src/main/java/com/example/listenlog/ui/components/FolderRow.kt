package com.example.listenlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.TextSecondary

@Composable
fun FolderRow(
    folderName: String,
    songCount: Int,
    isPlayingFolder: Boolean,
    playingTrackIndex: Int = -1,
    totalTracksInFolder: Int = 0,
    onRowClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    val backgroundColor = if (isPlayingFolder) {
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
                .clickable(onClick = onRowClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle play button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isPlayingFolder) AccentOrange else Color(0xFF333333))
            ) {
                Text(
                    text = "▶",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "📁",
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = folderName,
                        color = if (isPlayingFolder) AccentOrange else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isPlayingFolder && playingTrackIndex >= 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${playingTrackIndex + 1}/$totalTracksInFolder) playing",
                            color = AccentOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = "$songCount song${if (songCount == 1) "" else "s"}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

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
                        text = { Text("Play Folder", color = Color.White) },
                        onClick = { expandedMenu = false; onPlayClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle Folder", color = Color.White) },
                        onClick = { expandedMenu = false; onShuffleClick() }
                    )
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
                }
            }
        }

        HorizontalDivider(color = Color(0xFF222222), thickness = 0.5.dp)
    }
}
