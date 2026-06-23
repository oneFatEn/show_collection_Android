package com.jishi.collection.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jishi.collection.CategorySummary
import com.jishi.collection.ui.theme.CategoryArtworkSize
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun CategoryListItem(
    category: CategorySummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoStack(
                urls = category.previews,
                modifier = Modifier.size(72.dp, 58.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    color = JiShiColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "笔记 · ${category.count}",
                    color = JiShiColors.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            QuietIconButton(
                symbol = "✎",
                contentDescription = "编辑分类",
                onClick = onEdit,
            )
        }
        Divider(color = JiShiColors.Hairline, thickness = 0.6.dp)
    }
}

@Composable
fun PhotoStack(urls: List<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val slots = listOf(
            StackSlot(x = 19, y = 1, rotation = 5f, color = Color(0xFFD7D4CA)),
            StackSlot(x = 9, y = 6, rotation = -6f, color = Color(0xFFE3DED5)),
            StackSlot(x = 0, y = 11, rotation = 3f, color = Color(0xFFEAE7DF)),
        )
        slots.forEachIndexed { index, slot ->
            StackedThumb(
                url = urls.getOrNull(index),
                slot = slot,
                modifier = Modifier
                    .size(CategoryArtworkSize.dp)
                    .offset(slot.x.dp, slot.y.dp),
            )
        }
    }
}

@Composable
private fun StackedThumb(url: String?, slot: StackSlot, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer {
                rotationZ = slot.rotation
                shadowElevation = 2.dp.toPx()
            }
            .clip(RoundedCornerShape(6.dp))
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
