package com.jishi.collection.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jishi.collection.NoteCard
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun NoteListItem(
    note: NoteCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val rowBackground by animateColorAsState(
        targetValue = if (highlighted) Color(0x1AD93025) else Color.Transparent,
        label = "note-row-highlight",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
        ) {
            AsyncImage(
                model = note.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(JiShiColors.SearchSurface),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    note.title,
                    color = JiShiColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    note.desc,
                    color = JiShiColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.authorName.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(note.authorName, color = JiShiColors.TextTertiary, fontSize = 12.sp)
                }
            }
        }
        Divider(color = JiShiColors.Hairline, thickness = 0.6.dp)
    }
}
