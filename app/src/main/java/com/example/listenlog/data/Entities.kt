package com.example.listenlog.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Play")
data class Play(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val listenedMs: Long,
    val startedAt: Long,
    val counted: Boolean // true = completed play (>=30s & >= min(dur/2, 4m)), false = skip (>=3s)
)

@Entity(tableName = "TagEdit")
data class TagEdit(
    @PrimaryKey val path: String,
    val title: String,
    val artist: String,
    val album: String
)

@Entity(tableName = "Playlist")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "PlaylistItem")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val path: String,
    val position: Int = 0
)

@Entity(tableName = "Favorite")
data class Favorite(
    @PrimaryKey val path: String,
    val addedAt: Long = System.currentTimeMillis()
)

// Data Transfer Objects for Stats & UI
data class Top(
    val name: String,
    val plays: Int,
    val ms: Long,
    val path: String? = null
)

data class Bucket(
    val label: String,
    val plays: Int
)

data class Track(
    val id: Long,
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val isLiked: Boolean = false
)

data class FolderItem(
    val path: String,
    val name: String,
    val songCount: Int,
    val isPlayingFolder: Boolean = false,
    val playingTrackIndex: Int = -1,
    val totalTracksInFolder: Int = 0
)
