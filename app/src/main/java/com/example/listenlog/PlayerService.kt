package com.example.listenlog

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.listenlog.data.AppDatabase
import com.example.listenlog.data.Play
import kotlinx.coroutines.*

class PlayerService : MediaSessionService() {

    private var session: MediaSession? = null
    private var tracker: Tracker? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        tracker = Tracker(applicationContext, player).also {
            player.addListener(it)
        }

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        tracker?.finish(blocking = true)
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

class Tracker(
    private val context: Context,
    private val player: Player
) : Player.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentItem: MediaItem? = null
    private var startedAt: Long = 0L
    private var listenedMs: Long = 0L
    private var sinceTime: Long = 0L

    private fun resumeTimer() {
        if (sinceTime == 0L) {
            sinceTime = SystemClock.elapsedRealtime()
        }
    }

    private fun pauseTimer() {
        if (sinceTime != 0L) {
            listenedMs += (SystemClock.elapsedRealtime() - sinceTime)
            sinceTime = 0L
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        finish(blocking = false)
        currentItem = mediaItem
        startedAt = System.currentTimeMillis()
        if (player.isPlaying) {
            resumeTimer()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            if (currentItem == null) {
                currentItem = player.currentMediaItem
                startedAt = System.currentTimeMillis()
            }
            resumeTimer()
        } else {
            pauseTimer()
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_ENDED) {
            finish(blocking = false)
        }
    }

    fun finish(blocking: Boolean) {
        pauseTimer()
        val mediaItem = currentItem ?: return
        currentItem = null
        val durationPlayed = listenedMs
        listenedMs = 0L

        // Ignore listens under 3 seconds
        if (durationPlayed < 3_000L) return

        val extras = mediaItem.mediaMetadata.extras
        val duration = extras?.getLong("dur") ?: 0L

        // Rule: counted as play if listened >= 30s AND listened >= min(duration/2, 4 minutes)
        val minHalfOrFourMin = minOf(duration / 2, 240_000L)
        val counted = durationPlayed >= 30_000L && durationPlayed >= minHalfOrFourMin

        val playRecord = Play(
            path = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown Title",
            artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown Artist",
            album = mediaItem.mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
            durationMs = duration,
            listenedMs = durationPlayed,
            startedAt = startedAt,
            counted = counted
        )

        val dao = AppDatabase.getInstance(context).playDao()
        if (blocking) {
            runBlocking {
                dao.insert(playRecord)
            }
        } else {
            scope.launch {
                dao.insert(playRecord)
            }
        }
    }
}
