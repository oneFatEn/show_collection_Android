package com.jishi.collection.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jishi.collection.CategorySummary
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun CategoryListItem(
    category: CategorySummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rowBackground by animateColorAsState(
        targetValue = if (highlighted) Color(0x1AD93025) else Color.Transparent,
        label = "category-row-highlight",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .clickable(onClick = onOpen)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoStack(
                urls = category.previews,
                modifier = Modifier.size(88.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    color = JiShiColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "笔记 · ${category.count}",
                    color = JiShiColors.TextTertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(31.dp)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                PencilIcon(
                    modifier = Modifier.size(15.dp),
                    color = JiShiColors.TextTertiary,
                )
            }
        }
        HorizontalDivider(
            color = JiShiColors.Hairline,
            thickness = 1.dp,
            modifier = Modifier.padding(start = 96.dp, end = 24.dp),
        )
    }
}

@Composable
fun PhotoStack(urls: List<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val visibleUrls = urls.filter { it.isNotBlank() }.take(3)
        val slots = photoSlots(visibleUrls.size)
        visibleUrls.forEachIndexed { index, url ->
            StackedThumb(
                url = url,
                slot = slots[index],
                modifier = Modifier
                    .size(width = 58.dp, height = 70.dp)
                    .offset(slots[index].x.dp, slots[index].y.dp),
            )
        }
    }
}

private fun photoSlots(count: Int): List<StackSlot> {
    return when (count) {
        1 -> listOf(
            StackSlot(x = 15, y = 9, rotation = 0f, color = Color(0xFFEDECE8)),
        )
        2 -> listOf(
            StackSlot(x = 12, y = 9, rotation = -10f, color = Color(0xFFEDECE8)),
            StackSlot(x = 18, y = 9, rotation = 10f, color = Color(0xFFEDECE8)),
        )
        else -> listOf(
            StackSlot(x = 17, y = 12, rotation = 3f, color = Color(0xFFEDECE8)),
            StackSlot(x = 12, y = 7, rotation = -10f, color = Color(0xFFEDECE8)),
            StackSlot(x = 16, y = 6, rotation = 10f, color = Color(0xFFEDECE8)),
        )
    }
}

@Composable
private fun StackedThumb(url: String?, slot: StackSlot, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = slot.rotation
            }
            .shadow(
                elevation = 5.dp,
                shape = RoundedCornerShape(7.dp),
                ambientColor = Color(0x21000000),
                spotColor = Color(0x21000000),
            )
            .clip(RoundedCornerShape(7.dp))
            .background(Color.White)
            .border(2.dp, Color.White, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(slot.color),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private data class StackSlot(
    val x: Int,
    val y: Int,
    val rotation: Float,
    val color: Color,
)

@Composable
private fun PencilIcon(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        fun x(value: Float) = value * scaleX
        fun y(value: Float) = value * scaleY
        val stroke = Stroke(
            width = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val body = Path().apply {
            moveTo(x(21.174f), y(6.812f))
            cubicTo(x(21.702f), y(6.284f), x(21.702f), y(5.428f), x(21.174f), y(4.9f))
            lineTo(x(19.1f), y(2.826f))
            cubicTo(x(18.572f), y(2.298f), x(17.716f), y(2.298f), x(17.188f), y(2.826f))
            lineTo(x(3.842f), y(16.174f))
            cubicTo(x(3.604f), y(16.411f), x(3.433f), y(16.695f), x(3.342f), y(17.004f))
            lineTo(x(2.021f), y(21.356f))
            cubicTo(x(1.918f), y(21.695f), x(2.238f), y(22.015f), x(2.644f), y(21.978f))
            lineTo(x(6.997f), y(20.658f))
            cubicTo(x(7.305f), y(20.567f), x(7.589f), y(20.396f), x(7.826f), y(20.159f))
            lineTo(x(21.174f), y(6.812f))
        }
        drawPath(body, color = color, style = stroke)
        val capPath = Path().apply {
            moveTo(x(15f), y(5f))
            lineTo(x(19f), y(9f))
        }
        drawPath(capPath, color = color, style = stroke)
    }
}
