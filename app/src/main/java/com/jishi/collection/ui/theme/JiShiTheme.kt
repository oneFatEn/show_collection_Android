package com.jishi.collection.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JiShiColors {
    val Paper = Color(0xFFF5F4F1)
    val Surface = Color(0xFFFBFAF7)
    val TextPrimary = Color(0xFF20211F)
    val TextSecondary = Color(0xFF777A73)
    val TextTertiary = Color(0xFFA6A69F)
    val Hairline = Color(0xFFE6E3DC)
    val QuietAccent = Color(0xFF2F6B4F)
    val SearchSurface = Color(0xFFF0EFEB)
}

val ScreenHorizontalPadding = 28
val CategoryArtworkSize = 54

private val ColorScheme = lightColorScheme(
    primary = JiShiColors.QuietAccent,
    background = JiShiColors.Paper,
    surface = JiShiColors.Surface,
    onBackground = JiShiColors.TextPrimary,
    onSurface = JiShiColors.TextPrimary,
    outline = JiShiColors.Hairline,
)

@Composable
fun JiShiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

