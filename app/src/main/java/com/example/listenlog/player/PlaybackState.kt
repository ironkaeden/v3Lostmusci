package com.example.listenlog.player

import androidx.media3.common.Player

data class PState(
    val currentPath: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val isPlaying: Boolean = false,
    val shuffleMode: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<String> = emptyList(),
    val queueIndex: Int = -1,
    val isLiked: Boolean = false
)
