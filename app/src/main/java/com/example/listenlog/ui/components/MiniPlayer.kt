package com.example.listenlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.player.PState
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.DarkSurfaceVariant
import com.example.listenlog.ui.theme.TextSecondary

@Composable
fun MiniPlayer(
    state: PState,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit
) {
    if (state.currentPath.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onExpandClick)
    ) {
        val progress = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = AccentOrange,
            trackColor = Color.DarkGray
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtTile(title = state.title.ifEmpty { "Song" }, size = 42.dp)

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifEmpty { "No song playing" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = state.artist.ifEmpty { "Select a song" },
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⏮",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable(onClick = onPrevClick)
                        .padding(8.dp)
                )
                Text(
                    text = if (state.isPlaying) "⏸" else "▶",
                    color = AccentOrange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onPlayPauseClick)
                        .padding(8.dp)
                )
                Text(
                    text = "⏭",
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .clickable(onClick = onNextClick)
                        .padding(8.dp)
                )
            }
        }
    }
}
