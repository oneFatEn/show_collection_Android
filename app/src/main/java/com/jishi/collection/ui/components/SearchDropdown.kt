package com.jishi.collection.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jishi.collection.CategorySummary
import com.jishi.collection.SearchableNote
import com.jishi.collection.ui.theme.JiShiColors

private const val CONTEXT_CHARS = 16
private val ItemHeight = 76.dp
private val DropdownHeight = ItemHeight * 4
private val MatchColor = Color(0xFFD93025)

sealed interface SearchSuggestion {
    val id: String

    data class Category(val category: CategorySummary) : SearchSuggestion {
        override val id: String = "category:${category.id}"
    }

    data class Note(val note: SearchableNote) : SearchSuggestion {
        override val id: String = "note:${note.rednoteId}"
    }
}

@Composable
fun SearchDropdown(
    query: String,
    categories: List<CategorySummary>,
    notes: List<SearchableNote>,
    onResultClick: (SearchSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return

    val sourceNotes = notes.ifEmpty { mockSearchNotes }
    val matched = remember(categories, sourceNotes, trimmed) {
        searchSuggestions(categories, sourceNotes, trimmed)
    }
    val showEndLine = matched.size <= 4

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DropdownHeight)
            .background(JiShiColors.Paper, RoundedCornerShape(16.dp))
            .border(1.dp, JiShiColors.Hairline, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (matched.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DropdownHeight)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Text("无相关收藏", color = JiShiColors.TextTertiary, fontSize = 13.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(12.dp))
                EndLine()
                Spacer(Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DropdownHeight),
            ) {
                itemsIndexed(matched, key = { _, result -> result.id }) { index, result ->
                    when (result) {
                        is SearchSuggestion.Category -> CategoryResultRow(
                            category = result.category,
                            query = trimmed,
                            onClick = { onResultClick(result) },
                        )
                        is SearchSuggestion.Note -> NoteResultRow(
                            note = result.note,
                            query = trimmed,
                            onClick = { onResultClick(result) },
                        )
                    }
                    if (index < matched.lastIndex) {
                        HorizontalDivider(
                            color = JiShiColors.Hairline,
                            thickness = 1.dp,
                            modifier = Modifier.padding(start = 60.dp, end = 16.dp),
                        )
                    }
                }
                if (showEndLine) {
                    item {
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                            EndLine()
                        }
                    }
                }
            }
        }
    }
}

private val mockSearchNotes = listOf(
    SearchableNote(
        rednoteId = "mock_101",
        categoryId = "mock_cooking",
        title = "零失败奶油蘑菇意面做法",
        desc = "用黄油炒香蒜末，加入口蘑翻炒至金黄，倒入淡奶油小火收汁，拌入煮好的意面即可。",
        coverUrl = "https://images.unsplash.com/photo-1621996346565-e3dbc646d9a9?w=80&h=80&fit=crop&auto=format",
        categoryName = "做饭",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_102",
        categoryId = "mock_cooking",
        title = "低卡减脂餐｜鸡胸肉沙拉配方",
        desc = "鸡胸肉用柠檬汁和黑胡椒腌制30分钟，煎至两面金黄，切片放在混合生菜上，淋上酸奶沙拉酱。",
        coverUrl = "https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=80&h=80&fit=crop&auto=format",
        categoryName = "做饭",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_103",
        categoryId = "mock_travel",
        title = "京都必去清单｜岚山竹林最佳拍照时间",
        desc = "建议早上七点前到达，人少光线好。竹林小道全程约200米，建议反方向走避开人流。",
        coverUrl = "https://images.unsplash.com/photo-1528360983277-13d401cdc186?w=80&h=80&fit=crop&auto=format",
        categoryName = "旅行",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_104",
        categoryId = "mock_travel",
        title = "北海道自驾攻略｜租车+住宿全规划",
        desc = "从新千岁机场取车，沿着道央自动车道北上，推荐富良野薰衣草田和美瑛蓝池，住宿建议订温泉旅馆。",
        coverUrl = "https://images.unsplash.com/photo-1494256997604-768d1f608cac?w=80&h=80&fit=crop&auto=format",
        categoryName = "旅行",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_105",
        categoryId = "mock_food",
        title = "上海隐藏小馆｜这家红烧肉绝了",
        desc = "藏在弄堂里的老字号，红烧肉肥而不腻入口即化，每天限量50份，建议提前电话预订。",
        coverUrl = "https://images.unsplash.com/photo-1569050467447-ce54b3bbc37d?w=80&h=80&fit=crop&auto=format",
        categoryName = "美食",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_106",
        categoryId = "mock_food",
        title = "广州早茶全攻略｜必点虾饺和肠粉",
        desc = "陶陶居的虾饺皮薄馅大，推荐拉肠粉配甜酱，搭配一壶普洱，完美的周末早晨从这里开始。",
        coverUrl = "https://images.unsplash.com/photo-1563245372-f21724e3856d?w=80&h=80&fit=crop&auto=format",
        categoryName = "美食",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_107",
        categoryId = "mock_gift",
        title = "今年最爱的生日礼物清单",
        desc = "香薰蜡烛套装、手账本、耳机收纳包，这几样送女生最合适，价格不高但很有心意。",
        coverUrl = "https://images.unsplash.com/photo-1549465220-1a8b9238cd48?w=80&h=80&fit=crop&auto=format",
        categoryName = "礼物",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_108",
        categoryId = "mock_outfit",
        title = "秋冬穿搭公式｜毛呢大衣怎么搭都好看",
        desc = "驼色大衣+白色高领毛衣+深蓝牛仔裤是黄金组合，再配一双白色小皮鞋，整体干净利落。",
        coverUrl = "https://images.unsplash.com/photo-1558769132-cb1aea458c5e?w=80&h=80&fit=crop&auto=format",
        categoryName = "穿搭",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_109",
        categoryId = "mock_reading",
        title = "今年读过最好的10本书",
        desc = "《置身事内》《万历十五年》《项塔兰》《人类简史》，这几本读完都有很深的触动，强烈推荐。",
        coverUrl = "https://images.unsplash.com/photo-1481627834876-b7833e8f5570?w=80&h=80&fit=crop&auto=format",
        categoryName = "读书",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_110",
        categoryId = "mock_skincare",
        title = "护肤新手入门｜这5步就够了",
        desc = "洁面、爽肤水、精华、乳液、防晒，每一步都不能省。精华推荐烟酰胺和玻尿酸，新手最不容易踩雷。",
        coverUrl = "https://images.unsplash.com/photo-1570194065650-d99fb4b38b55?w=80&h=80&fit=crop&auto=format",
        categoryName = "护肤",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_111",
        categoryId = "mock_home",
        title = "租房改造｜小户型也能住出高级感",
        desc = "用奶油白乳胶漆刷墙，换掉出租屋原有的黄灯，加一盏暖光落地灯，整个空间气质立刻不一样。",
        coverUrl = "https://images.unsplash.com/photo-1586023492125-27b2c045efd7?w=80&h=80&fit=crop&auto=format",
        categoryName = "家居",
        noteUrl = "",
    ),
    SearchableNote(
        rednoteId = "mock_112",
        categoryId = "mock_cooking",
        title = "做饭新手必学｜5道零失败家常菜",
        desc = "番茄炒蛋、红烧豆腐、蒜蓉西兰花、土豆丝、酸辣汤，每道菜都有详细步骤，零基础也能做好。",
        coverUrl = "https://images.unsplash.com/photo-1493770348161-369560ae357d?w=80&h=80&fit=crop&auto=format",
        categoryName = "做饭",
        noteUrl = "",
    ),
)

private fun searchSuggestions(
    categories: List<CategorySummary>,
    notes: List<SearchableNote>,
    query: String,
): List<SearchSuggestion> {
    val matchedCategories = categories
        .filter { category -> category.name.contains(query, ignoreCase = true) }
        .map { category -> SearchSuggestion.Category(category) }
    val matchedNotes = notes.filter { note ->
        note.title.contains(query, ignoreCase = true) || note.desc.contains(query, ignoreCase = true)
    }.map { note -> SearchSuggestion.Note(note) }
    return matchedCategories + matchedNotes
}

@Composable
private fun CategoryResultRow(category: CategorySummary, query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactPhotoStack(
            urls = category.previews,
            modifier = Modifier.size(width = 44.dp, height = 52.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = matchedText(category.name, query),
            color = JiShiColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactPhotoStack(urls: List<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val visibleUrls = urls.filter { it.isNotBlank() }.take(3)
        val slots = compactPhotoSlots(visibleUrls.size)
        visibleUrls.forEachIndexed { index, url ->
            val slot = slots[index]
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 44.dp)
                    .offset(slot.x.dp, slot.y.dp)
                    .graphicsLayer { rotationZ = slot.rotation }
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .border(1.5.dp, Color.White, RoundedCornerShape(6.dp))
                    .clip(RoundedCornerShape(6.dp))
                    .background(JiShiColors.SearchSurface),
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

private fun compactPhotoSlots(count: Int): List<CompactStackSlot> {
    return when (count) {
        1 -> listOf(CompactStackSlot(x = 4, y = 4, rotation = 0f))
        2 -> listOf(
            CompactStackSlot(x = 1, y = 4, rotation = -10f),
            CompactStackSlot(x = 7, y = 4, rotation = 10f),
        )
        else -> listOf(
            CompactStackSlot(x = 5, y = 6, rotation = 3f),
            CompactStackSlot(x = 1, y = 3, rotation = -10f),
            CompactStackSlot(x = 6, y = 2, rotation = 10f),
        )
    }
}

private data class CompactStackSlot(
    val x: Int,
    val y: Int,
    val rotation: Float,
)

@Composable
private fun NoteResultRow(note: SearchableNote, query: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ItemHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(JiShiColors.SearchSurface),
        ) {
            if (note.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = note.coverUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.categoryName,
                    color = JiShiColors.TextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                Text(" · ", color = JiShiColors.Hairline, fontSize = 10.sp)
                Text(
                    text = matchedText(note.title, query),
                    color = JiShiColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = matchedText(note.desc, query),
                color = JiShiColors.TextTertiary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun matchedText(text: String, query: String): AnnotatedString {
    val result = matchContext(text, query) ?: return AnnotatedString(text)
    return buildAnnotatedString {
        append(result.before)
        pushStyle(SpanStyle(color = MatchColor, fontWeight = FontWeight.Medium))
        append(result.match)
        pop()
        append(result.after)
    }
}

private fun matchContext(text: String, query: String): MatchContext? {
    val index = text.indexOf(query, ignoreCase = true)
    if (index < 0) return null
    val beforeRaw = text.substring(0, index)
    val afterRaw = text.substring(index + query.length)
    val before = if (beforeRaw.length > CONTEXT_CHARS) "…" + beforeRaw.takeLast(CONTEXT_CHARS) else beforeRaw
    val after = if (afterRaw.length > CONTEXT_CHARS) afterRaw.take(CONTEXT_CHARS) + "…" else afterRaw
    return MatchContext(
        before = before,
        match = text.substring(index, index + query.length),
        after = after,
    )
}

@Composable
private fun EndLine() {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(color = JiShiColors.Hairline, modifier = Modifier.weight(1f), thickness = 1.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(JiShiColors.Hairline),
        )
        HorizontalDivider(color = JiShiColors.Hairline, modifier = Modifier.weight(1f), thickness = 1.dp)
    }
}

private data class MatchContext(
    val before: String,
    val match: String,
    val after: String,
)
