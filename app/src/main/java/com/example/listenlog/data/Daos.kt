package com.example.listenlog.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayDao {
    @Insert
    suspend fun insert(p: Play)

    @Query("SELECT COUNT(*) FROM Play WHERE counted = 1 AND startedAt >= :s")
    fun plays(s: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM Play WHERE counted = 0 AND startedAt >= :s")
    fun skips(s: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM Play WHERE startedAt >= :s")
    fun totalMs(s: Long): Flow<Long>

    @Query("SELECT COUNT(DISTINCT artist) FROM Play WHERE counted = 1 AND startedAt >= :s")
    fun uniqueArtists(s: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT path) FROM Play WHERE counted = 1 AND startedAt >= :s")
    fun uniqueTracks(s: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT album) FROM Play WHERE counted = 1 AND startedAt >= :s")
    fun uniqueAlbums(s: Long): Flow<Int>

    @Query("""
        SELECT artist AS name, COUNT(*) AS plays, SUM(listenedMs) AS ms, MIN(path) AS path
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY artist
        ORDER BY plays DESC, ms DESC
        LIMIT :limit
    """)
    fun topArtists(s: Long, limit: Int = 50): Flow<List<Top>>

    @Query("""
        SELECT title || ' · ' || artist AS name, COUNT(*) AS plays, SUM(listenedMs) AS ms, path
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY path
        ORDER BY plays DESC, ms DESC
        LIMIT :limit
    """)
    fun topTracks(s: Long, limit: Int = 50): Flow<List<Top>>

    @Query("""
        SELECT album AS name, COUNT(*) AS plays, SUM(listenedMs) AS ms, MIN(path) AS path
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY album
        ORDER BY plays DESC, ms DESC
        LIMIT :limit
    """)
    fun topAlbums(s: Long, limit: Int = 50): Flow<List<Top>>

    @Query("""
        SELECT title || ' · ' || artist AS name, COUNT(*) AS plays, SUM(listenedMs) AS ms, path
        FROM Play
        WHERE counted = 0 AND startedAt >= :s
        GROUP BY path
        ORDER BY plays DESC
        LIMIT :limit
    """)
    fun mostSkipped(s: Long, limit: Int = 50): Flow<List<Top>>

    @Query("""
        SELECT strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS label, COUNT(*) AS plays
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY label
        ORDER BY label ASC
    """)
    fun byHour(s: Long): Flow<List<Bucket>>

    @Query("""
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS label, COUNT(*) AS plays
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY label
        ORDER BY label DESC
    """)
    fun byDay(s: Long): Flow<List<Bucket>>

    @Query("""
        SELECT strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') AS label, COUNT(*) AS plays
        FROM Play
        WHERE counted = 1 AND startedAt >= :s
        GROUP BY label
        ORDER BY label ASC
    """)
    fun byWeekday(s: Long): Flow<List<Bucket>>

    @Query("SELECT * FROM Play ORDER BY startedAt DESC LIMIT :limit")
    fun recentHistory(limit: Int = 200): Flow<List<Play>>

    @Query("SELECT * FROM Play ORDER BY startedAt ASC")
    suspend fun allPlaysForCsv(): List<Play>

    @Query("""
        SELECT title AS name, COUNT(*) AS plays, SUM(listenedMs) AS ms, path
        FROM Play
        WHERE counted = 1 AND artist = :artist
        GROUP BY path
        ORDER BY plays DESC
    """)
    fun artistTopSongs(artist: String): Flow<List<Top>>

    @Query("UPDATE Play SET title = :t, artist = :a, album = :al WHERE path = :p")
    suspend fun retagPlays(p: String, t: String, a: String, al: String)
}

@Dao
interface TagEditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveTag(t: TagEdit)

    @Query("SELECT * FROM TagEdit")
    fun tags(): Flow<List<TagEdit>>

    @Query("DELETE FROM TagEdit WHERE path = :p")
    suspend fun deleteTag(p: String)
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun addPlaylist(p: Playlist): Long

    @Query("UPDATE Playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM Playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM Playlist ORDER BY name COLLATE NOCASE ASC")
    fun playlists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItems(items: List<PlaylistItem>)

    @Query("DELETE FROM PlaylistItem WHERE playlistId = :playlistId AND path = :path")
    suspend fun removeItem(playlistId: Long, path: String)

    @Query("DELETE FROM PlaylistItem WHERE playlistId = :playlistId")
    suspend fun deleteItemsForPlaylist(playlistId: Long)

    @Query("SELECT * FROM PlaylistItem WHERE playlistId = :playlistId ORDER BY position ASC, id ASC")
    fun getItems(playlistId: Long): Flow<List<PlaylistItem>>

    @Query("UPDATE PlaylistItem SET position = :position WHERE id = :id")
    suspend fun updateItemPosition(id: Long, position: Int)
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: Favorite)

    @Query("DELETE FROM Favorite WHERE path = :path")
    suspend fun deleteFavorite(path: String)

    @Query("SELECT * FROM Favorite")
    fun favorites(): Flow<List<Favorite>>

    @Query("SELECT EXISTS(SELECT 1 FROM Favorite WHERE path = :path)")
    fun isFavorite(path: String): Flow<Boolean>
}
