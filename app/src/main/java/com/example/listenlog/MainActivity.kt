package com.example.listenlog

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.listenlog.data.*
import com.example.listenlog.player.PState
import com.example.listenlog.ui.components.AddToPlaylistDialog
import com.example.listenlog.ui.components.EditTagDialog
import com.example.listenlog.ui.components.MiniPlayer
import com.example.listenlog.ui.screens.*
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.DarkBackground
import com.example.listenlog.ui.theme.ListenLogTheme
import com.example.listenlog.ui.theme.SpotifyGreen
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var mediaController by mutableStateOf<MediaController?>(null)
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ListenLogTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    ListenLogApp(controller = mediaController)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val sessionToken = SessionToken(this, ComponentName(this, PlayerService::class.java))
        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                mediaController = future.get()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onStop()
    }
}

fun playTrackList(controller: MediaController?, tracks: List<Track>, startIndex: Int = 0) {
    if (controller == null || tracks.isEmpty()) return
    val items = tracks.map { it.toMediaItem() }
    val validIndex = startIndex.coerceIn(0, items.size - 1)
    controller.setMediaItems(items, validIndex, 0L)
    controller.prepare()
    controller.play()
}

@Composable
fun ListenLogApp(controller: MediaController?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }

    // PERMISSIONS
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var isGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(isGranted) {
        if (!isGranted) {
            audioPermissionLauncher.launch(audioPermission)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // STATE & DATA
    val tagEdits by db.tagEditDao().tags().collectAsState(initial = emptyList())
    val favorites by db.favoriteDao().favorites().collectAsState(initial = emptyList())
    val playlists by db.playlistDao().playlists().collectAsState(initial = emptyList())

    val favoritedPaths = remember(favorites) { favorites.map { it.path }.toSet() }

    var rawTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var rescanTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(isGranted, rescanTrigger) {
        if (isGranted) {
            rawTracks = withContext(Dispatchers.IO) {
                MediaRepository.scanMediaStore(context)
            }
        }
    }

    // Combine MediaStore raw tracks with TagEdits & Favorites
    val tracks = remember(rawTracks, tagEdits, favoritedPaths) {
        val tagMap = tagEdits.associateBy { it.path }
        rawTracks.map { track ->
            val tag = tagMap[track.path]
            track.copy(
                title = tag?.title ?: track.title,
                artist = tag?.artist ?: track.artist,
                album = tag?.album ?: track.album,
                isLiked = favoritedPaths.contains(track.path)
            )
        }
    }

    val trackMap = remember(tracks) { tracks.associateBy { it.path } }

    // PLAYBACK STATE & SLEEP TIMER
    var pState by remember { mutableStateOf(PState()) }
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepTimerEndTimeMs by remember { mutableLongStateOf(0L) }

    // Update PState from MediaController & polling
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}

        fun updateState() {
            val currentItem = controller.currentMediaItem
            val mediaId = currentItem?.mediaId ?: ""
            val metadata = currentItem?.mediaMetadata
            val queueItems = (0 until controller.mediaItemCount).map { idx ->
                controller.getMediaItemAt(idx).mediaId
            }

            pState = PState(
                currentPath = mediaId,
                title = metadata?.title?.toString() ?: "",
                artist = metadata?.artist?.toString() ?: "",
                album = metadata?.albumTitle?.toString() ?: "",
                isPlaying = controller.isPlaying,
                shuffleMode = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                queue = queueItems,
                queueIndex = controller.currentMediaItemIndex,
                isLiked = favoritedPaths.contains(mediaId)
            )
        }

        updateState()

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateState()
            }
        }
        controller.addListener(listener)

        onDispose {
            controller.removeListener(listener)
        }
    }

    // Polling position every 500ms while playing
    LaunchedEffect(controller, pState.isPlaying) {
        while (controller != null && controller.isPlaying) {
            val currentPos = controller.currentPosition.coerceAtLeast(0L)
            val totalDur = controller.duration.coerceAtLeast(0L)
            pState = pState.copy(positionMs = currentPos, durationMs = totalDur)

            // Check sleep timer
            if (sleepTimerMinutes > 0 && sleepTimerEndTimeMs > 0L) {
                if (System.currentTimeMillis() >= sleepTimerEndTimeMs) {
                    controller.pause()
                    sleepTimerMinutes = 0
                    sleepTimerEndTimeMs = 0L
                    Toast.makeText(context, "Sleep timer expired. Playback paused.", Toast.LENGTH_LONG).show()
                }
            }

            delay(500)
        }
    }

    // NAVIGATION & DIALOGS
    var currentTab by remember { mutableIntStateOf(0) }
    var isNowPlayingOpen by remember { mutableStateOf(false) }
    var editingTrack by remember { mutableStateOf<Track?>(null) }
    var pendingAddTracks by remember { mutableStateOf<List<Track>>(emptyList()) }

    // Auto Playlists calculations
    val recentlyPlayedPlays by db.playDao().recentHistory(50).collectAsState(initial = emptyList())
    val recentlyPlayedTracks = remember(recentlyPlayedPlays, trackMap) {
        recentlyPlayedPlays.mapNotNull { trackMap[it.path] }.distinctBy { it.path }
    }

    val mostPlayedTops by db.playDao().topTracks(0L, 50).collectAsState(initial = emptyList())
    val mostPlayedTracks = remember(mostPlayedTops, trackMap) {
        mostPlayedTops.mapNotNull { top -> top.path?.let { trackMap[it] } }
    }

    val queueTracks = remember(pState.queue, trackMap) {
        pState.queue.mapNotNull { trackMap[it] }
    }

    Scaffold(
        bottomBar = {
            Column {
                // MINI PLAYER FLOATING BAR
                MiniPlayer(
                    state = pState,
                    onExpandClick = { isNowPlayingOpen = true },
                    onPlayPauseClick = {
                        if (controller != null) {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                    },
                    onPrevClick = { controller?.seekToPreviousMediaItem() },
                    onNextClick = { controller?.seekToNextMediaItem() }
                )

                // BOTTOM NAVIGATION
                NavigationBar(containerColor = Color(0xFF181818)) {
                    val tabs = listOf(
                        "Library" to "📁",
                        "Search" to "🔍",
                        "Playlists" to "🎵",
                        "Stats" to "📊"
                    )
                    tabs.forEachIndexed { index, (label, icon) ->
                        NavigationBarItem(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentOrange,
                                selectedTextColor = AccentOrange,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray,
                                indicatorColor = AccentOrange.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (!isGranted) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("ListenLog requires storage access to play audio files.", color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { audioPermissionLauncher.launch(audioPermission) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                        ) {
                            Text("Grant Access")
                        }
                    }
                }
            } else {
                when (currentTab) {
                    0 -> LibraryScreen(
                        tracks = tracks,
                        playbackState = pState,
                        onPlayTracks = { trackList, startIdx -> playTrackList(controller, trackList, startIdx) },
                        onPlayNext = { track ->
                            controller?.let { c ->
                                val insertIndex = c.currentMediaItemIndex + 1
                                c.addMediaItem(insertIndex, track.toMediaItem())
                            }
                        },
                        onAddToQueue = { track ->
                            controller?.addMediaItem(track.toMediaItem())
                        },
                        onAddFolderToQueue = { folderTracks ->
                            folderTracks.forEach { controller?.addMediaItem(it.toMediaItem()) }
                        },
                        onPlayFolderNext = { folderTracks ->
                            controller?.let { c ->
                                val insertIndex = c.currentMediaItemIndex + 1
                                folderTracks.reversed().forEach { t ->
                                    c.addMediaItem(insertIndex, t.toMediaItem())
                                }
                            }
                        },
                        onAddToPlaylist = { selected -> pendingAddTracks = selected },
                        onToggleLike = { track ->
                            coroutineScope.launch {
                                if (track.isLiked) {
                                    db.favoriteDao().deleteFavorite(track.path)
                                } else {
                                    db.favoriteDao().addFavorite(Favorite(track.path))
                                }
                            }
                        },
                        onEditInfo = { editingTrack = it },
                        onRescan = { rescanTrigger++ },
                        onOpenSearch = { currentTab = 1 },
                        onToggleRepeatMode = {
                            controller?.let { c ->
                                val nextMode = when (c.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                c.repeatMode = nextMode
                            }
                        },
                        onSetSleepTimer = { mins ->
                            sleepTimerMinutes = mins
                            sleepTimerEndTimeMs = if (mins > 0) System.currentTimeMillis() + mins * 60_000L else 0L
                            Toast.makeText(context, if (mins > 0) "Sleep timer set for $mins minutes" else "Sleep timer disabled", Toast.LENGTH_SHORT).show()
                        },
                        onSeek = { pos -> controller?.seekTo(pos) },
                        onPrev = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeekBack10 = { controller?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } },
                        onSeekForward10 = { controller?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(it.duration)) } }
                    )

                    1 -> SearchScreen(
                        tracks = tracks,
                        playbackState = pState,
                        onPlayTracks = { trackList, startIdx -> playTrackList(controller, trackList, startIdx) },
                        onPlayNext = { track ->
                            controller?.let { c ->
                                c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem())
                            }
                        },
                        onAddToQueue = { track -> controller?.addMediaItem(track.toMediaItem()) },
                        onAddToPlaylist = { pendingAddTracks = it },
                        onToggleLike = { track ->
                            coroutineScope.launch {
                                if (track.isLiked) db.favoriteDao().deleteFavorite(track.path)
                                else db.favoriteDao().addFavorite(Favorite(track.path))
                            }
                        },
                        onEditInfo = { editingTrack = it },
                        onSeek = { pos -> controller?.seekTo(pos) },
                        onPrev = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeekBack10 = { controller?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } },
                        onSeekForward10 = { controller?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(it.duration)) } }
                    )

                    2 -> PlaylistsScreen(
                        tracks = tracks,
                        playlists = playlists,
                        playlistItemsProvider = { playlistId ->
                            db.playlistDao().getItems(playlistId).collectAsState(initial = emptyList()).value
                        },
                        recentlyPlayedTracks = recentlyPlayedTracks,
                        mostPlayedTracks = mostPlayedTracks,
                        playbackState = pState,
                        onCreatePlaylist = { name ->
                            coroutineScope.launch { db.playlistDao().addPlaylist(Playlist(name = name)) }
                        },
                        onRenamePlaylist = { id, name ->
                            coroutineScope.launch { db.playlistDao().renamePlaylist(id, name) }
                        },
                        onDeletePlaylist = { id ->
                            coroutineScope.launch {
                                db.playlistDao().deleteItemsForPlaylist(id)
                                db.playlistDao().deletePlaylist(id)
                            }
                        },
                        onRemoveItemFromPlaylist = { playlistId, path ->
                            coroutineScope.launch { db.playlistDao().removeItem(playlistId, path) }
                        },
                        onMovePlaylistItemUp = { playlistId, items, index ->
                            if (index > 0) {
                                coroutineScope.launch {
                                    val current = items[index]
                                    val prev = items[index - 1]
                                    db.playlistDao().updateItemPosition(current.id, index - 1)
                                    db.playlistDao().updateItemPosition(prev.id, index)
                                }
                            }
                        },
                        onMovePlaylistItemDown = { playlistId, items, index ->
                            if (index < items.size - 1) {
                                coroutineScope.launch {
                                    val current = items[index]
                                    val next = items[index + 1]
                                    db.playlistDao().updateItemPosition(current.id, index + 1)
                                    db.playlistDao().updateItemPosition(next.id, index)
                                }
                            }
                        },
                        onPlayTracks = { trackList, startIdx -> playTrackList(controller, trackList, startIdx) },
                        onPlayNext = { track -> controller?.let { c -> c.addMediaItem(c.currentMediaItemIndex + 1, track.toMediaItem()) } },
                        onAddToQueue = { track -> controller?.addMediaItem(track.toMediaItem()) },
                        onAddFolderToQueue = { list -> list.forEach { controller?.addMediaItem(it.toMediaItem()) } },
                        onAddToPlaylist = { pendingAddTracks = it },
                        onToggleLike = { track ->
                            coroutineScope.launch {
                                if (track.isLiked) db.favoriteDao().deleteFavorite(track.path)
                                else db.favoriteDao().addFavorite(Favorite(track.path))
                            }
                        },
                        onEditInfo = { editingTrack = it },
                        onSeek = { pos -> controller?.seekTo(pos) },
                        onPrev = { controller?.seekToPreviousMediaItem() },
                        onNext = { controller?.seekToNextMediaItem() },
                        onSeekBack10 = { controller?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } },
                        onSeekForward10 = { controller?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(it.duration)) } }
                    )

                    3 -> StatsScreen(
                        dao = db.playDao(),
                        tracks = tracks,
                        onPlayTrack = { track -> playTrackList(controller, listOf(track), 0) },
                        onPlayTrackList = { list, startIdx -> playTrackList(controller, list, startIdx) }
                    )
                }
            }
        }
    }

    // NOW PLAYING SHEET
    if (isNowPlayingOpen) {
        NowPlayingSheet(
            state = pState,
            queueTracks = queueTracks,
            onDismiss = { isNowPlayingOpen = false },
            onPlayPauseClick = {
                if (controller != null) {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }
            },
            onPrevClick = { controller?.seekToPreviousMediaItem() },
            onNextClick = { controller?.seekToNextMediaItem() },
            onToggleShuffle = { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } },
            onToggleRepeat = {
                controller?.let { c ->
                    val next = when (c.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    c.repeatMode = next
                }
            },
            onSeek = { pos -> controller?.seekTo(pos) },
            onToggleLike = { track ->
                coroutineScope.launch {
                    if (track.isLiked) db.favoriteDao().deleteFavorite(track.path)
                    else db.favoriteDao().addFavorite(Favorite(track.path))
                }
            },
            onAddToPlaylist = { pendingAddTracks = listOf(it) },
            onEditInfo = { editingTrack = it },
            onJumpToQueueIndex = { idx -> controller?.seekToDefaultPosition(idx) },
            onRemoveFromQueue = { idx -> controller?.removeMediaItem(idx) }
        )
    }

    // EDIT TAG DIALOG
    editingTrack?.let { track ->
        EditTagDialog(
            track = track,
            onDismiss = { editingTrack = null },
            onSave = { newTitle, newArtist, newAlbum ->
                coroutineScope.launch {
                    val tagEdit = TagEdit(path = track.path, title = newTitle, artist = newArtist, album = newAlbum)
                    db.tagEditDao().saveTag(tagEdit)
                    // Apply retroactively to existing Play records for that file path
                    db.playDao().retagPlays(track.path, newTitle, newArtist, newAlbum)
                }
                editingTrack = null
            }
        )
    }

    // ADD TO PLAYLIST DIALOG
    if (pendingAddTracks.isNotEmpty()) {
        AddToPlaylistDialog(
            playlists = playlists,
            trackCount = pendingAddTracks.size,
            onDismiss = { pendingAddTracks = emptyList() },
            onCreatePlaylistAndAdd = { newName ->
                coroutineScope.launch {
                    val playlistId = db.playlistDao().addPlaylist(Playlist(name = newName))
                    pendingAddTracks.forEachIndexed { idx, track ->
                        db.playlistDao().addItem(PlaylistItem(playlistId = playlistId, path = track.path, position = idx))
                    }
                    pendingAddTracks = emptyList()
                    Toast.makeText(context, "Created \"$newName\" and added tracks.", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectPlaylist = { selectedPlaylist ->
                coroutineScope.launch {
                    val existingItems = db.playlistDao().getItems(selectedPlaylist.id).collectAsState(initial = emptyList()).value
                    val existingPaths = existingItems.map { it.path }.toSet()
                    val startPos = existingItems.size
                    var addedCount = 0

                    pendingAddTracks.forEach { track ->
                        if (!existingPaths.contains(track.path)) {
                            db.playlistDao().addItem(PlaylistItem(playlistId = selectedPlaylist.id, path = track.path, position = startPos + addedCount))
                            addedCount++
                        }
                    }
                    pendingAddTracks = emptyList()
                    Toast.makeText(context, "Added $addedCount track(s) to \"${selectedPlaylist.name}\".", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
