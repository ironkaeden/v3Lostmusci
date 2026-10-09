package com.example.listenlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.data.Track
import com.example.listenlog.player.PState
import com.example.listenlog.ui.components.AlbumArtTile
import com.example.listenlog.ui.components.TrackRow
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary

@Composable
fun SearchScreen(
    tracks: List<Track>,
    playbackState: PState,
    onPlayTracks: (tracks: List<Track>, startIndex: Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onEditInfo: (Track) -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekBack10: () -> Unit,
    onSeekForward10: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) ||
                        it.artist.lowercase().contains(q) ||
                        it.album.lowercase().contains(q)
            }
        }
    }

    val matchedArtists = remember(filteredTracks) {
        filteredTracks.groupBy { it.artist }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val matchedAlbums = remember(filteredTracks) {
        filteredTracks.groupBy { it.album to it.artist }.toSortedMap(compareBy { it.first })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search text input field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search songs, artists, albums...", color = TextSecondary) },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("✕", color = TextSecondary, fontSize = 16.sp)
                    }
                } else {
                    Text("🔍", fontSize = 16.sp)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = AccentOrange,
                unfocusedBorderColor = Color(0xFF333333)
            )
        )

        if (searchQuery.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Type a song, artist, or album name to search",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for \"$searchQuery\"",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // ARTISTS SECTION
                if (matchedArtists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Artists",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    items(matchedArtists.keys.toList()) { artistName ->
                        val artistTracks = matchedArtists.getValue(artistName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                                .clickable { onPlayTracks(artistTracks, 0) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtTile(title = artistName, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(artistName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${artistTracks.size} songs", color = TextSecondary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onPlayTracks(artistTracks, 0) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                            ) {
                                Text("▶ Play All", color = Color.White, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { onAddToPlaylist(artistTracks) }) {
                                Text("＋", color = SpotifyGreen, fontSize = 18.sp)
                            }
                        }
                    }
                }

                // ALBUMS SECTION
                if (matchedAlbums.isNotEmpty()) {
                    item {
                        Text(
                            text = "Albums",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }

                    items(matchedAlbums.keys.toList()) { (albumName, artistName) ->
                        val albumTracks = matchedAlbums.getValue(albumName to artistName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                                .clickable { onPlayTracks(albumTracks, 0) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AlbumArtTile(title = albumName, size = 44.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(albumName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(artistName, color = TextSecondary, fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onPlayTracks(albumTracks, 0) },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                            ) {
                                Text("▶ Play", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // SONGS SECTION
                item {
                    Text(
                        text = "Songs",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                itemsIndexed(filteredTracks) { index, track ->
                    TrackRow(
                        track = track,
                        isPlaying = playbackState.isPlaying,
                        isCurrentTrack = playbackState.currentPath == track.path,
                        onPlayClick = { onPlayTracks(filteredTracks, index) },
                        onTrackClick = { onPlayTracks(filteredTracks, index) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(listOf(track)) },
                        onToggleLike = { onToggleLike(track) },
                        onEditInfo = { onEditInfo(track) },
                        currentPositionMs = playbackState.positionMs,
                        durationMs = playbackState.durationMs,
                        onSeek = onSeek,
                        onPrev = onPrev,
                        onNext = onNext,
                        onSeekBack10 = onSeekBack10,
                        onSeekForward10 = onSeekForward10
                    )
                }
            }
        }
    }
}
