package com.jishi.collection.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun MessageBar(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JiShiColors.SearchSurface, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(message, color = JiShiColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(text, color = JiShiColors.TextTertiary, fontSize = 14.sp)
    }
}

@Composable
fun QuietIconButton(
    symbol: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(36.dp),
    ) {
        Text(
            text = symbol,
            color = JiShiColors.TextTertiary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
fun TopTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text, color = JiShiColors.TextSecondary, fontWeight = FontWeight.Normal)
    }
}

@Composable
fun QuietSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索我的收藏...",
) {
    Column(modifier = modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                color = JiShiColors.TextPrimary,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(39.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            text = placeholder,
                            color = Color.Black.copy(alpha = 0.18f),
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Light,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(JiShiColors.Hairline),
        )
    }
}

@Composable
fun QuietPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingSymbol: String? = null,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = JiShiColors.TextTertiary),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        if (leadingSymbol != null) {
            Text(leadingSymbol, color = JiShiColors.TextTertiary, fontSize = 13.sp)
        }
        Text(text, fontSize = 12.sp)
    }
}

@Composable
fun SyncStatusButton(
    text: String,
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = text.startsWith("已更新")
    val secondaryText = if (done) text.removePrefix("已更新").trim().takeIf { it.isNotBlank() } else null
    val primaryText = if (done) "已更新" else text
    val transition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sync_rotation_value",
    )

    Row(
        modifier = modifier
            .height(30.dp)
            .border(1.dp, JiShiColors.Hairline, RoundedCornerShape(999.dp))
            .clickable(
                enabled = !isSyncing,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RefreshIcon(
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { rotationZ = if (isSyncing) rotation else 0f },
            color = if (done) JiShiColors.TextPrimary else JiShiColors.TextTertiary,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = primaryText,
            color = if (done) JiShiColors.TextPrimary else JiShiColors.TextTertiary,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal,
        )
        if (secondaryText != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = secondaryText,
                color = JiShiColors.TextTertiary,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
fun SearchIcon(modifier: Modifier = Modifier, color: Color = JiShiColors.TextTertiary) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(
            color = color,
            radius = size.minDimension * 0.33f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.43f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.66f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun RefreshIcon(modifier: Modifier = Modifier, color: Color = JiShiColors.TextPrimary) {
    Icon(
        imageVector = LucideRefreshCw,
        contentDescription = null,
        modifier = modifier,
        tint = color,
    )
}

private val LucideRefreshCw: ImageVector = ImageVector.Builder(
    name = "LucideRefreshCw",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 12f)
        curveTo(3f, 7.03f, 7.03f, 3f, 12f, 3f)
        curveTo(14.63f, 3f, 16.99f, 4.13f, 18.64f, 5.93f)
        lineTo(21f, 8f)
        moveTo(21f, 3f)
        verticalLineTo(8f)
        horizontalLineTo(16f)
        moveTo(21f, 12f)
        curveTo(21f, 16.97f, 16.97f, 21f, 12f, 21f)
        curveTo(9.37f, 21f, 7.01f, 19.87f, 5.36f, 18.07f)
        lineTo(3f, 16f)
        moveTo(8f, 16f)
        horizontalLineTo(3f)
        verticalLineTo(21f)
    }
}.build()

@Composable
fun SectionLabel(
    text: String,
    action: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = JiShiColors.TextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.84.sp,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}
