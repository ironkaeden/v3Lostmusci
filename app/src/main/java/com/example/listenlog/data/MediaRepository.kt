package com.example.listenlog.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import android.os.Bundle
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

fun Track.toMediaUri(): Uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(path)
    .setUri(toMediaUri())
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setExtras(Bundle().apply {
                putLong("dur", durationMs)
            })
            .build()
    )
    .build()

object MediaRepository {

    private fun clean(s: String?, fallback: String): String {
        return if (s.isNullOrBlank() || s.equals("<unknown>", ignoreCase = true)) fallback else s.trim()
    }

    fun scanMediaStore(context: Context): List<Track> {
        val out = mutableListOf<Track>()
        val selection = "${Media.IS_RINGTONE}=0 AND ${Media.IS_NOTIFICATION}=0 AND ${Media.IS_ALARM}=0 AND ${Media.DURATION}>=10000"
        val projection = arrayOf(
            Media._ID,
            Media.DATA,
            Media.TITLE,
            Media.ARTIST,
            Media.ALBUM,
            Media.DURATION
        )

        context.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${Media.DATA} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(Media._ID)
            val pathCol = cursor.getColumnIndexOrThrow(Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(Media.DURATION)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: continue
                val file = File(path)
                if (!file.exists()) continue

                val id = cursor.getLong(idCol)
                val rawTitle = cursor.getString(titleCol)
                val rawArtist = cursor.getString(artistCol)
                val rawAlbum = cursor.getString(albumCol)
                val duration = cursor.getLong(durCol)

                val title = clean(rawTitle, file.nameWithoutExtension)
                val artist = clean(rawArtist, "Unknown Artist")
                val album = clean(rawAlbum, "Unknown Album")

                out.add(
                    Track(
                        id = id,
                        path = path,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration
                    )
                )
            }
        }
        return out
    }

    fun getCommonRoot(tracks: List<Track>): String {
        if (tracks.isEmpty()) return Environment.getExternalStorageDirectory().absolutePath
        var prefix = tracks.first().path.substringBeforeLast('/')
        for (track in tracks) {
            val dir = track.path.substringBeforeLast('/')
            while (prefix.isNotEmpty() && !(dir == prefix || dir.startsWith("$prefix/"))) {
                prefix = prefix.substringBeforeLast('/', "")
            }
        }
        return if (prefix.isBlank() || prefix == "/storage/emulated") {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            prefix
        }
    }

    fun exportMixToFolder(context: Context, playlistName: String, tracks: List<Track>): Int {
        val resolver = context.contentResolver
        val safeName = playlistName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        var copiedCount = 0

        tracks.forEachIndexed { index, track ->
            val sourceFile = File(track.path)
            val targetFileName = "%02d %s".format(index + 1, sourceFile.name)

            val contentValues = ContentValues().apply {
                put(Media.DISPLAY_NAME, targetFileName)
                put(Media.MIME_TYPE, resolver.getType(track.toMediaUri()) ?: "audio/mpeg")
                put(Media.RELATIVE_PATH, "Music/ListenLog Mixes/$safeName")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(Media.IS_PENDING, 1)
                }
            }

            val destUri = resolver.insert(Media.EXTERNAL_CONTENT_URI, contentValues) ?: return@forEachIndexed

            try {
                resolver.openInputStream(track.toMediaUri())?.use { input ->
                    resolver.openOutputStream(destUri)?.use { output ->
                        input.copyTo(output)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(Media.IS_PENDING, 0)
                    resolver.update(destUri, contentValues, null, null)
                }
                copiedCount++
            } catch (e: Exception) {
                resolver.delete(destUri, null, null)
            }
        }
        return copiedCount
    }

    fun exportHistoryCsv(context: Context, plays: List<Play>): String? {
        val resolver = context.contentResolver
        val fileName = "listenlog_history_${System.currentTimeMillis()}.csv"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                out.bufferedWriter().use { writer ->
                    writer.write("ID,Path,Title,Artist,Album,DurationSeconds,ListenedSeconds,Timestamp,DateTime,Status\n")
                    for (p in plays) {
                        val dt = sdf.format(Date(p.startedAt))
                        val status = if (p.counted) "Play" else "Skip"
                        val safeTitle = p.title.replace("\"", "\"\"")
                        val safeArtist = p.artist.replace("\"", "\"\"")
                        val safeAlbum = p.album.replace("\"", "\"\"")
                        val line = "${p.id},\"${p.path}\",\"$safeTitle\",\"$safeArtist\",\"$safeAlbum\",${p.durationMs / 1000},${p.listenedMs / 1000},${p.startedAt},\"$dt\",$status\n"
                        writer.write(line)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            fileName
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
