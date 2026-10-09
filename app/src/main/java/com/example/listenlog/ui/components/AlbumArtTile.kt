package com.example.listenlog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.listenlog.ui.theme.AccentOrange
import com.example.listenlog.ui.theme.SpotifyGreen

private val GradientPairs = listOf(
    Pair(Color(0xFF8E2DE2), Color(0xFF4A00E0)),
    Pair(Color(0xFFFF416C), Color(0xFFFF4B2B)),
    Pair(Color(0xFF00B4DB), Color(0xFF0083B0)),
    Pair(Color(0xFFF7971E), Color(0xFFFFD200)),
    Pair(Color(0xFF11998E), Color(0xFF38EF7D)),
    Pair(Color(0xFFFC466B), Color(0xFF3F5EFB)),
    Pair(Color(0xFFEE0979), Color(0xFFFF6A00)),
    Pair(AccentOrange, Color(0xFFE65100))
)

@Composable
fun AlbumArtTile(
    title: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    cornerRadius: Dp = 8.dp
) {
    val hash = (title.hashCode() and 0x7FFFFFFF) % GradientPairs.size
    val (startColor, endColor) = GradientPairs[hash]
    val letter = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "🎵"

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(listOf(startColor, endColor))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.45f).sp
        )
    }
}
