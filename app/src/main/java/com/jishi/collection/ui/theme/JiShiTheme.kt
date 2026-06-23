package com.jishi.collection.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object JiShiColors {
    val Paper = Color(0xFFF8F7F5)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF1A1A1A)
    val TextSecondary = Color(0xFF6F6F68)
    val TextTertiary = Color(0xFF9B9B93)
    val Hairline = Color(0x12000000)
    val QuietAccent = Color(0xFF1A1A1A)
    val SearchSurface = Color(0xFFF0EEEB)
    val Accent = Color(0xFFEDECEA)
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
