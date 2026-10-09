package com.example.listenlog.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.data.MediaRepository
import com.example.listenlog.data.Playlist
import com.example.listenlog.data.PlaylistItem
import com.example.listenlog.data.Track
import com.example.listenlog.player.PState
import com.example.listenlog.ui.components.AlbumArtTile
import com.example.listenlog.ui.components.ConfirmDeleteDialog
import com.example.listenlog.ui.components.TrackRow
import com.example.listenlog.ui.components.formatDuration
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SelectedPlaylistView {
    object None : SelectedPlaylistView()
    object LikedSongs : SelectedPlaylistView()
    object RecentlyPlayed : SelectedPlaylistView()
    object MostPlayed : SelectedPlaylistView()
    data class Custom(val playlist: Playlist) : SelectedPlaylistView()
}

@Composable
fun PlaylistsScreen(
    tracks: List<Track>,
    playlists: List<Playlist>,
    playlistItemsProvider: @Composable (playlistId: Long) -> List<PlaylistItem>,
    recentlyPlayedTracks: List<Track>,
    mostPlayedTracks: List<Track>,
    playbackState: PState,
    onCreatePlaylist: (name: String) -> Unit,
    onRenamePlaylist: (id: Long, newName: String) -> Unit,
    onDeletePlaylist: (id: Long) -> Unit,
    onRemoveItemFromPlaylist: (playlistId: Long, path: String) -> Unit,
    onMovePlaylistItemUp: (playlistId: Long, items: List<PlaylistItem>, index: Int) -> Unit,
    onMovePlaylistItemDown: (playlistId: Long, items: List<PlaylistItem>, index: Int) -> Unit,
    onPlayTracks: (tracks: List<Track>, startIndex: Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddFolderToQueue: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onEditInfo: (Track) -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekBack10: () -> Unit,
    onSeekForward10: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedView by remember { mutableStateOf<SelectedPlaylistView>(SelectedPlaylistView.None) }
    var isCreateDialogOpen by remember { mutableStateOf(false) }
    var isRenameDialogOpen by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var renameInput by remember { mutableStateOf("") }

    val trackMap = remember(tracks) { tracks.associateBy { it.path } }
    val likedTracks = remember(tracks) { tracks.filter { it.isLiked } }

    val currentCustomPlaylist = (selectedView as? SelectedPlaylistView.Custom)?.playlist
    val customPlaylistItems = if (currentCustomPlaylist != null) {
        playlistItemsProvider(currentCustomPlaylist.id)
    } else emptyList()

    val currentPlaylistTracks = remember(selectedView, customPlaylistItems, trackMap, likedTracks, recentlyPlayedTracks, mostPlayedTracks) {
        when (selectedView) {
            is SelectedPlaylistView.None -> emptyList()
            is SelectedPlaylistView.LikedSongs -> likedTracks
            is SelectedPlaylistView.RecentlyPlayed -> recentlyPlayedTracks
            is SelectedPlaylistView.MostPlayed -> mostPlayedTracks
            is SelectedPlaylistView.Custom -> customPlaylistItems.mapNotNull { trackMap[it.path] }
        }
    }

    if (selectedView == SelectedPlaylistView.None) {
        Column(modifier = Modifier.fillMaxSize()) {
            // PINNED HEADER OUTSIDE SCROLLING LIST: "Your Library" header with "＋ New" button
            Surface(
                color = Color(0xFF1E1E1E),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Library",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )

                    Button(
                        onClick = { isCreateDialogOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("＋ New", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Built-in Auto Playlists
                item {
                    Text("Auto Playlists", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                item {
                    PlaylistItemTile(
                        title = "Liked Songs",
                        subtitle = "${likedTracks.size} songs",
                        icon = "❤️",
                        tileColor = Color(0xFFE91E63),
                        onClick = { selectedView = SelectedPlaylistView.LikedSongs }
                    )
                }

                item {
                    PlaylistItemTile(
                        title = "Recently Played",
                        subtitle = "${recentlyPlayedTracks.size} songs",
                        icon = "🕒",
                        tileColor = Color(0xFF3F51B5),
                        onClick = { selectedView = SelectedPlaylistView.RecentlyPlayed }
                    )
                }

                item {
                    PlaylistItemTile(
                        title = "Most Played",
                        subtitle = "${mostPlayedTracks.size} songs",
                        icon = "🔥",
                        tileColor = AccentOrange,
                        onClick = { selectedView = SelectedPlaylistView.MostPlayed }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("User Playlists", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                items(playlists) { playlist ->
                    PlaylistItemTile(
                        title = playlist.name,
                        subtitle = "Playlist",
                        icon = "🎵",
                        tileColor = Color(0xFF282828),
                        onClick = { selectedView = SelectedPlaylistView.Custom(playlist) }
                    )
                }
            }
        }
    } else {
        // PLAYLIST DETAIL VIEW
        val (title, icon, isCustom) = when (val view = selectedView) {
            is SelectedPlaylistView.LikedSongs -> Triple("Liked Songs", "❤️", false)
            is SelectedPlaylistView.RecentlyPlayed -> Triple("Recently Played", "🕒", false)
            is SelectedPlaylistView.MostPlayed -> Triple("Most Played", "🔥", false)
            is SelectedPlaylistView.Custom -> Triple(view.playlist.name, "🎵", true)
            else -> Triple("", "", false)
        }

        val totalDurationMs = currentPlaylistTracks.sumOf { it.durationMs }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedView = SelectedPlaylistView.None }) {
                    Text("←", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isCustom && currentCustomPlaylist != null) {
                    var expandedMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { expandedMenu = true }) {
                            Text("⋮", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.background(Color(0xFF282828))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename Playlist", color = Color.White) },
                                onClick = {
                                    expandedMenu = false
                                    renameInput = currentCustomPlaylist.name
                                    isRenameDialogOpen = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy files to folder", color = SpotifyGreen) },
                                onClick = {
                                    expandedMenu = false
                                    coroutineScope.launch {
                                        val count = withContext(Dispatchers.IO) {
                                            MediaRepository.exportMixToFolder(context, currentCustomPlaylist.name, currentPlaylistTracks)
                                        }
                                        Toast.makeText(context, "Copied $count files to Music/ListenLog Mixes/${currentCustomPlaylist.name}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist", color = Color.Red) },
                                onClick = {
                                    expandedMenu = false
                                    playlistToDelete = currentCustomPlaylist
                                }
                            )
                        }
                    }
                }
            }

            // Playlist Cover & Info header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArtTile(title = title, size = 64.dp)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        "${currentPlaylistTracks.size} songs · ${formatDuration(totalDurationMs)}",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            // Action Buttons: Play, Shuffle, Add to Queue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { if (currentPlaylistTracks.isNotEmpty()) onPlayTracks(currentPlaylistTracks, 0) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶ Play", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { if (currentPlaylistTracks.isNotEmpty()) onPlayTracks(currentPlaylistTracks.shuffled(), 0) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔀 Shuffle", color = Color.White)
                }
                OutlinedButton(
                    onClick = { if (currentPlaylistTracks.isNotEmpty()) onAddFolderToQueue(currentPlaylistTracks) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("＋ Queue", color = SpotifyGreen)
                }
            }

            // Playlist Tracks
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(currentPlaylistTracks) { index, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isCustom && currentCustomPlaylist != null) {
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                IconButton(
                                    onClick = { onMovePlaylistItemUp(currentCustomPlaylist.id, customPlaylistItems, index) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("▲", color = if (index > 0) Color.White else Color.DarkGray, fontSize = 10.sp)
                                }
                                IconButton(
                                    onClick = { onMovePlaylistItemDown(currentCustomPlaylist.id, customPlaylistItems, index) },
                                    enabled = index < currentPlaylistTracks.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("▼", color = if (index < currentPlaylistTracks.size - 1) Color.White else Color.DarkGray, fontSize = 10.sp)
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            TrackRow(
                                track = track,
                                isPlaying = playbackState.isPlaying,
                                isCurrentTrack = playbackState.currentPath == track.path,
                                onPlayClick = { onPlayTracks(currentPlaylistTracks, index) },
                                onTrackClick = { onPlayTracks(currentPlaylistTracks, index) },
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

                        if (isCustom && currentCustomPlaylist != null) {
                            IconButton(
                                onClick = { onRemoveItemFromPlaylist(currentCustomPlaylist.id, track.path) },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text("✕", color = Color.Red, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // CREATE PLAYLIST DIALOG
    if (isCreateDialogOpen) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { isCreateDialogOpen = false },
            containerColor = Color(0xFF222222),
            title = { Text("New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Playlist Name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = SpotifyGreen
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            onCreatePlaylist(nameInput.trim())
                            isCreateDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreateDialogOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // RENAME PLAYLIST DIALOG
    if (isRenameDialogOpen && currentCustomPlaylist != null) {
        AlertDialog(
            onDismissRequest = { isRenameDialogOpen = false },
            containerColor = Color(0xFF222222),
            title = { Text("Rename Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Name", color = TextSecondary) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenamePlaylist(currentCustomPlaylist.id, renameInput.trim())
                            selectedView = SelectedPlaylistView.Custom(currentCustomPlaylist.copy(name = renameInput.trim()))
                            isRenameDialogOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isRenameDialogOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    playlistToDelete?.let { target ->
        ConfirmDeleteDialog(
            playlistName = target.name,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                onDeletePlaylist(target.id)
                playlistToDelete = null
                selectedView = SelectedPlaylistView.None
            }
        )
    }
}

@Composable
private fun PlaylistItemTile(
    title: String,
    subtitle: String,
    icon: String,
    tileColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(tileColor)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Text("➔", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
    }
}
