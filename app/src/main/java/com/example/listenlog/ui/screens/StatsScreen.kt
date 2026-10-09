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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.data.Bucket
import com.example.listenlog.data.MediaRepository
import com.example.listenlog.data.Play
import com.example.listenlog.data.PlayDao
import com.example.listenlog.data.Top
import com.example.listenlog.data.Track
import com.example.listenlog.ui.components.ArtistTopSongsDialog
import com.example.listenlog.ui.components.formatDuration
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen
import com.example.listenlog.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

enum class StatsRange(val label: String, val days: Int) {
    TODAY("Today", 1),
    SEVEN_DAYS("7d", 7),
    THIRTY_DAYS("30d", 30),
    NINETY_DAYS("90d", 90),
    YEAR("Year", 365),
    ALL("All", 0)
}

enum class StatsSubTab(val title: String) {
    OVERVIEW("Overview"),
    ARTISTS("Artists"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    HISTORY("History")
}

@Composable
fun StatsScreen(
    dao: PlayDao,
    tracks: List<Track>,
    onPlayTrack: (Track) -> Unit,
    onPlayTrackList: (tracks: List<Track>, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var range by remember { mutableStateOf(StatsRange.THIRTY_DAYS) }
    var subTab by remember { mutableStateOf(StatsSubTab.OVERVIEW) }
    var selectedArtistForSongs by remember { mutableStateOf<String?>(null) }

    val trackMap = remember(tracks) { tracks.associateBy { it.path } }

    val currentTimestamp = remember { System.currentTimeMillis() }
    val sinceTimestamp = remember(range, currentTimestamp) {
        val now = System.currentTimeMillis()
        when (range) {
            StatsRange.TODAY -> LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            StatsRange.SEVEN_DAYS -> now - 7 * 86_400_000L
            StatsRange.THIRTY_DAYS -> now - 30 * 86_400_000L
            StatsRange.NINETY_DAYS -> now - 90 * 86_400_000L
            StatsRange.YEAR -> now - 365 * 86_400_000L
            StatsRange.ALL -> 0L
        }
    }

    val prevPeriodSinceTimestamp = remember(range, sinceTimestamp) {
        if (sinceTimestamp == 0L) 0L
        else {
            val duration = currentTimestamp - sinceTimestamp
            sinceTimestamp - duration
        }
    }

    val plays by dao.plays(sinceTimestamp).collectAsState(0)
    val skips by dao.skips(sinceTimestamp).collectAsState(0)
    val totalMs by dao.totalMs(sinceTimestamp).collectAsState(0L)
    val uniqueArtists by dao.uniqueArtists(sinceTimestamp).collectAsState(0)
    val uniqueTracks by dao.uniqueTracks(sinceTimestamp).collectAsState(0)
    val uniqueAlbums by dao.uniqueAlbums(sinceTimestamp).collectAsState(0)

    val prevPlays by dao.plays(prevPeriodSinceTimestamp).collectAsState(0)

    val topArtists by dao.topArtists(sinceTimestamp, 50).collectAsState(emptyList())
    val topTracks by dao.topTracks(sinceTimestamp, 50).collectAsState(emptyList())
    val topAlbums by dao.topAlbums(sinceTimestamp, 50).collectAsState(emptyList())
    val mostSkipped by dao.mostSkipped(sinceTimestamp, 50).collectAsState(emptyList())

    val byHourBuckets by dao.byHour(sinceTimestamp).collectAsState(emptyList())
    val byDayBuckets by dao.byDay(sinceTimestamp).collectAsState(emptyList())
    val byWeekdayBuckets by dao.byWeekday(sinceTimestamp).collectAsState(emptyList())

    val recentHistory by dao.recentHistory(200).collectAsState(emptyList())

    // Streak calculation
    val (currentStreak, longestStreak) = remember(byDayBuckets) {
        val daySet = byDayBuckets.map { it.label }.toSet()
        if (daySet.isEmpty()) 0 to 0
        else {
            var cur = 0
            var d = LocalDate.now()
            if (!daySet.contains(d.toString())) {
                d = d.minusDays(1)
            }
            while (daySet.contains(d.toString())) {
                cur++
                d = d.minusDays(1)
            }

            var maxRun = 0
            var tempRun = 0
            val sortedDays = daySet.map { LocalDate.parse(it) }.sorted()
            var prevDate: LocalDate? = null
            for (date in sortedDays) {
                tempRun = if (prevDate != null && date == prevDate.plusDays(1)) tempRun + 1 else 1
                maxRun = maxOf(maxRun, tempRun)
                prevDate = date
            }
            cur to maxRun
        }
    }

    val activeDays = byDayBuckets.size.coerceAtLeast(1)
    val avgPlaysPerActiveDay = plays / activeDays
    val avgTimePerActiveDayMs = totalMs / activeDays
    val bestDayPlays = byDayBuckets.maxOfOrNull { it.plays } ?: 0

    val topArtistHighlight = topArtists.firstOrNull()?.name ?: "-"
    val topSongHighlight = topTracks.firstOrNull()?.name ?: "-"
    val topAlbumHighlight = topAlbums.firstOrNull()?.name ?: "-"
    val peakHourLabel = byHourBuckets.maxByOrNull { it.plays }?.let { "${it.label}:00" } ?: "-"

    val playsDiff = plays - (prevPlays - plays)
    val playsDiffText = if (sinceTimestamp > 0L) {
        if (playsDiff >= 0) "+$playsDiff plays vs before" else "$playsDiff plays vs before"
    } else ""

    val artistTopSongsList = selectedArtistForSongs?.let { artist ->
        dao.artistTopSongs(artist).collectAsState(emptyList()).value
    } ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        // RANGE CHIPS (Today, 7d, 30d, 90d, Year, All)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatsRange.values().forEach { r ->
                val isSelected = range == r
                FilterChip(
                    selected = isSelected,
                    onClick = { range = r },
                    label = { Text(r.label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange,
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF222222),
                        labelColor = TextSecondary
                    )
                )
            }
        }

        // SUB-TABS (Overview, Artists, Tracks, Albums, History)
        TabRow(
            selectedTabIndex = subTab.ordinal,
            containerColor = Color(0xFF1E1E1E),
            contentColor = AccentOrange
        ) {
            StatsSubTab.values().forEach { tab ->
                Tab(
                    selected = subTab == tab,
                    onClick = { subTab = tab },
                    text = { Text(tab.title, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        // SUB-TAB CONTENT
        when (subTab) {
            StatsSubTab.OVERVIEW -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Export CSV Button
                    item {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val allPlays = dao.allPlaysForCsv()
                                    val fileName = withContext(Dispatchers.IO) {
                                        MediaRepository.exportHistoryCsv(context, allPlays)
                                    }
                                    if (fileName != null) {
                                        Toast.makeText(context, "Exported history to Downloads/$fileName", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to export CSV", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📥 Export history (CSV)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Stat Cards Grid
                    item {
                        Text("Overview Cards", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Listening Time", formatDuration(totalMs), AccentOrange, Modifier.weight(1f))
                                StatCard("Plays", "$plays", SpotifyGreen, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val totalAttempts = (plays + skips).coerceAtLeast(1)
                                val skipPct = (skips * 100) / totalAttempts
                                StatCard("Skips", "$skips ($skipPct%)", Color(0xFFFF5252), Modifier.weight(1f))
                                StatCard("Period Compare", playsDiffText.ifEmpty { "$plays plays" }, Color(0xFF64B5F6), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Avg Plays / Day", "$avgPlaysPerActiveDay", Color(0xFFFFB74D), Modifier.weight(1f))
                                StatCard("Avg Time / Day", formatDuration(avgTimePerActiveDayMs), Color(0xFFBA68C8), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Daily Streak", "$currentStreak days (best $longestStreak)", SpotifyGreen, Modifier.weight(1f))
                                StatCard("Best Day Plays", "$bestDayPlays plays", AccentOrange, Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatCard("Unique Artists", "$uniqueArtists", Color.White, Modifier.weight(1f))
                                StatCard("Unique Songs", "$uniqueTracks", Color.White, Modifier.weight(1f))
                            }
                        }
                    }

                    // Highlights Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
                                .padding(16.dp)
                        ) {
                            Text("Period Highlights", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            HighlightRow("Top Artist", topArtistHighlight)
                            HighlightRow("Top Song", topSongHighlight)
                            HighlightRow("Top Album", topAlbumHighlight)
                            HighlightRow("Peak Hour", peakHourLabel)
                        }
                    }

                    // Bar Chart: Plays by Hour of Day
                    item {
                        BarChartSection(
                            title = "Plays by Hour of Day (0 - 23)",
                            buckets = (0..23).map { hour ->
                                val label = "%02d".format(hour)
                                val count = byHourBuckets.find { it.label == label }?.plays ?: 0
                                Bucket(label, count)
                            }
                        )
                    }

                    // Bar Chart: Plays by Weekday (Sun - Sat)
                    item {
                        val daysOfWeek = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        BarChartSection(
                            title = "Plays by Weekday",
                            buckets = (0..6).map { dayIdx ->
                                val count = byWeekdayBuckets.find { it.label == "$dayIdx" }?.plays ?: 0
                                Bucket(daysOfWeek[dayIdx], count)
                            }
                        )
                    }

                    // Bar Chart: Plays by Day (Last 30 Days)
                    item {
                        BarChartSection(
                            title = "Plays over Recent Active Days",
                            buckets = byDayBuckets.take(30).reversed()
                        )
                    }
                }
            }

            StatsSubTab.ARTISTS -> {
                val maxPlays = topArtists.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(topArtists) { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
                                .clickable { selectedArtistForSongs = item.name }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("#${index + 1}", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${item.plays} plays · ${formatDuration(item.ms)}", color = TextSecondary, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                // Proportion bar
                                val fraction = item.plays.toFloat() / maxPlays.toFloat()
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = AccentOrange,
                                    trackColor = Color.DarkGray
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("➔", color = TextSecondary)
                        }
                    }
                }
            }

            StatsSubTab.TRACKS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text("Top 50 Most Played Songs", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    itemsIndexed(topTracks) { index, item ->
                        RankedTopRow(
                            rank = index + 1,
                            title = item.name,
                            subtitle = "${item.plays} plays · ${formatDuration(item.ms)}",
                            onClick = {
                                item.path?.let { p ->
                                    trackMap[p]?.let { t -> onPlayTrack(t) }
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Most Skipped Songs", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    itemsIndexed(mostSkipped) { index, item ->
                        RankedTopRow(
                            rank = index + 1,
                            title = item.name,
                            subtitle = "${item.plays} skips",
                            rankColor = Color(0xFFFF5252),
                            onClick = {
                                item.path?.let { p ->
                                    trackMap[p]?.let { t -> onPlayTrack(t) }
                                }
                            }
                        )
                    }
                }
            }

            StatsSubTab.ALBUMS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(topAlbums) { index, item ->
                        RankedTopRow(
                            rank = index + 1,
                            title = item.name,
                            subtitle = "${item.plays} plays · ${formatDuration(item.ms)}",
                            onClick = {
                                val albumTracks = tracks.filter { it.album == item.name }
                                if (albumTracks.isNotEmpty()) {
                                    onPlayTrackList(albumTracks, 0)
                                }
                            }
                        )
                    }
                }
            }

            StatsSubTab.HISTORY -> {
                val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recentHistory) { play ->
                        val track = trackMap[play.path]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E1E))
                                .clickable {
                                    if (track != null) onPlayTrack(track)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(play.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${play.artist} · ${dateFormat.format(Date(play.startedAt))}", color = TextSecondary, fontSize = 12.sp)
                            }

                            if (!play.counted) {
                                Text("skipped", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Text(formatDuration(play.listenedMs), color = SpotifyGreen, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Artist top songs dialog
    selectedArtistForSongs?.let { artistName ->
        ArtistTopSongsDialog(
            artistName = artistName,
            topSongs = artistTopSongsList,
            onDismiss = { selectedArtistForSongs = null },
            onSongClick = { top ->
                top.path?.let { p ->
                    trackMap[p]?.let { t -> onPlayTrack(t) }
                }
            }
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HighlightRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RankedTopRow(
    rank: Int,
    title: String,
    subtitle: String,
    rankColor: Color = AccentOrange,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#$rank", color = rankColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
        Text("▶", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun BarChartSection(title: String, buckets: List<Bucket>) {
    val maxBucketPlays = buckets.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { bucket ->
                val fraction = bucket.plays.toFloat() / maxBucketPlays.toFloat()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight(fraction.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (bucket.plays > 0) AccentOrange else Color.DarkGray)
                    )
                }
            }
        }
    }
}
