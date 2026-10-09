package com.example.listenlog.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.data.FolderItem
import com.example.listenlog.data.MediaRepository
import com.example.listenlog.data.Track
import com.example.listenlog.player.PState
import com.example.listenlog.ui.components.FolderRow
import com.example.listenlog.ui.components.TrackRow
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class SortMode { FILE_NAME, TITLE, ARTIST }

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    playbackState: PState,
    onPlayTracks: (tracks: List<Track>, startIndex: Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddFolderToQueue: (List<Track>) -> Unit,
    onPlayFolderNext: (List<Track>) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onEditInfo: (Track) -> Unit,
    onRescan: () -> Unit,
    onOpenSearch: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onSetSleepTimer: (minutes: Int) -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSeekBack10: () -> Unit,
    onSeekForward10: () -> Unit
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No audio files found in storage.", color = TextSecondary, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRescan,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("⟳ Rescan Library")
                }
            }
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val commonRoot = remember(tracks) { MediaRepository.getCommonRoot(tracks) }
    var currentPath by remember(commonRoot) { mutableStateOf(commonRoot) }
    var sortMode by remember { mutableStateOf(SortMode.FILE_NAME) }
    var expandedOverflowMenu by remember { mutableStateOf(false) }

    // Intercept system back button to navigate up folders
    val canGoUp = currentPath != "/" && currentPath.length > 1
    BackHandler(enabled = canGoUp && currentPath != commonRoot) {
        currentPath = currentPath.substringBeforeLast('/', "/").ifEmpty { "/" }
    }

    val normalizedPrefix = currentPath.trimEnd('/') + "/"
    val tracksUnderPath = remember(tracks, currentPath) {
        tracks.filter { it.path.startsWith(normalizedPrefix) }
    }

    // Direct files in current folder
    val directTracks = remember(tracksUnderPath, currentPath, sortMode) {
        val files = tracksUnderPath.filter { '/' !in it.path.removePrefix(normalizedPrefix) }
        when (sortMode) {
            SortMode.FILE_NAME -> files.sortedBy { it.path.lowercase() }
            SortMode.TITLE -> files.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> files.sortedBy { it.artist.lowercase() }
        }
    }

    // Direct subfolders in current folder
    val subfolders = remember(tracksUnderPath, currentPath, playbackState.currentPath) {
        tracksUnderPath.filter { '/' in it.path.removePrefix(normalizedPrefix) }
            .groupBy { it.path.removePrefix(normalizedPrefix).substringBefore('/') }
            .map { (folderName, folderTracks) ->
                val fullFolderPath = "$normalizedPrefix$folderName"
                val isPlayingInThisFolder = playbackState.currentPath.startsWith("$fullFolderPath/") ||
                        playbackState.currentPath == fullFolderPath
                val playingIndex = if (isPlayingInThisFolder) {
                    folderTracks.indexOfFirst { it.path == playbackState.currentPath }
                } else -1

                FolderItem(
                    path = fullFolderPath,
                    name = folderName,
                    songCount = folderTracks.size,
                    isPlayingFolder = isPlayingInThisFolder,
                    playingTrackIndex = playingIndex,
                    totalTracksInFolder = folderTracks.size
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // Recursively collect all tracks under a folder path
    fun getFolderTracksRecursive(folderPath: String): List<Track> {
        val prefix = folderPath.trimEnd('/') + "/"
        return tracks.filter { it.path.startsWith(prefix) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOOLBAR
        Surface(
            color = Color(0xFF1A1A1A),
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back / Up button
                IconButton(
                    onClick = {
                        if (canGoUp) {
                            currentPath = currentPath.substringBeforeLast('/', "/").ifEmpty { "/" }
                        }
                    },
                    enabled = canGoUp
                ) {
                    Text("⬆", color = if (canGoUp) Color.White else Color.Gray, fontSize = 18.sp)
                }

                // Home button (goes to deepest common root)
                IconButton(onClick = { currentPath = commonRoot }) {
                    Text("🏠", fontSize = 18.sp)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Shuffle-play current folder (recursive)
                TextButton(
                    onClick = {
                        if (tracksUnderPath.isNotEmpty()) {
                            onPlayTracks(tracksUnderPath.shuffled(), 0)
                        }
                    }
                ) {
                    Text("🔀 Shuffle", color = AccentOrange, fontWeight = FontWeight.Bold)
                }

                // Locate button: Jump to folder & row of currently playing song
                IconButton(
                    onClick = {
                        if (playbackState.currentPath.isNotEmpty()) {
                            val songDir = playbackState.currentPath.substringBeforeLast('/', "")
                            if (songDir.isNotEmpty()) {
                                currentPath = songDir
                                coroutineScope.launch {
                                    val targetIndex = directTracks.indexOfFirst { it.path == playbackState.currentPath }
                                    if (targetIndex >= 0) {
                                        listState.animateScrollToItem(subfolders.size + targetIndex)
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text("🎯", fontSize = 18.sp)
                }

                // Search shortcut
                IconButton(onClick = onOpenSearch) {
                    Text("🔍", fontSize = 18.sp)
                }

                // ⋮ Overflow menu
                Box {
                    IconButton(onClick = { expandedOverflowMenu = true }) {
                        Text("⋮", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = expandedOverflowMenu,
                        onDismissRequest = { expandedOverflowMenu = false },
                        modifier = Modifier.background(Color(0xFF282828))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sort: File Name", color = if (sortMode == SortMode.FILE_NAME) AccentOrange else Color.White) },
                            onClick = { sortMode = SortMode.FILE_NAME; expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Song Title", color = if (sortMode == SortMode.TITLE) AccentOrange else Color.White) },
                            onClick = { sortMode = SortMode.TITLE; expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sort: Artist", color = if (sortMode == SortMode.ARTIST) AccentOrange else Color.White) },
                            onClick = { sortMode = SortMode.ARTIST; expandedOverflowMenu = false }
                        )
                        HorizontalDivider(color = Color(0xFF333333))
                        DropdownMenuItem(
                            text = { Text("Repeat Mode", color = Color.White) },
                            onClick = { onToggleRepeatMode(); expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer: Off", color = Color.White) },
                            onClick = { onSetSleepTimer(0); expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer: 15 min", color = Color.White) },
                            onClick = { onSetSleepTimer(15); expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer: 30 min", color = Color.White) },
                            onClick = { onSetSleepTimer(30); expandedOverflowMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Sleep Timer: 60 min", color = Color.White) },
                            onClick = { onSetSleepTimer(60); expandedOverflowMenu = false }
                        )
                        HorizontalDivider(color = Color(0xFF333333))
                        DropdownMenuItem(
                            text = { Text("⟳ Rescan Library", color = SpotifyGreen) },
                            onClick = { onRescan(); expandedOverflowMenu = false }
                        )
                    }
                }
            }
        }

        // PATH BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF141414))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📍 $currentPath",
                color = AccentOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // FOLDER & TRACK LISTING
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Parent folder item ("..")
            if (currentPath != commonRoot && currentPath != "/") {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentPath = currentPath.substringBeforeLast('/', "/").ifEmpty { "/" }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⬆  ..", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = Color(0xFF222222))
                }
            }

            // Subfolder rows
            itemsIndexed(subfolders) { _, folder ->
                FolderRow(
                    folderName = folder.name,
                    songCount = folder.songCount,
                    isPlayingFolder = folder.isPlayingFolder,
                    playingTrackIndex = folder.playingTrackIndex,
                    totalTracksInFolder = folder.totalTracksInFolder,
                    onRowClick = { currentPath = folder.path },
                    onPlayClick = {
                        val subTracks = getFolderTracksRecursive(folder.path)
                        onPlayTracks(subTracks, 0)
                    },
                    onShuffleClick = {
                        val subTracks = getFolderTracksRecursive(folder.path)
                        onPlayTracks(subTracks.shuffled(), 0)
                    },
                    onPlayNext = {
                        val subTracks = getFolderTracksRecursive(folder.path)
                        onPlayFolderNext(subTracks)
                    },
                    onAddToQueue = {
                        val subTracks = getFolderTracksRecursive(folder.path)
                        onAddFolderToQueue(subTracks)
                    },
                    onAddToPlaylist = {
                        val subTracks = getFolderTracksRecursive(folder.path)
                        onAddToPlaylist(subTracks)
                    }
                )
            }

            // Track rows in current folder
            itemsIndexed(directTracks) { index, track ->
                val isCurrent = playbackState.currentPath == track.path
                TrackRow(
                    track = track,
                    isPlaying = playbackState.isPlaying,
                    isCurrentTrack = isCurrent,
                    onPlayClick = {
                        onPlayTracks(directTracks, index)
                    },
                    onTrackClick = {
                        onPlayTracks(directTracks, index)
                    },
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
