package com.jishi.collection.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.CategoryListItem
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.components.QuietSearchField
import com.jishi.collection.ui.components.SectionLabel
import com.jishi.collection.ui.components.SyncStatusButton
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun HomeScreen(state: AppUiState, actions: AppActions) {
    var query by remember { mutableStateOf("") }
    val filteredCategories = remember(state.categories, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            state.categories
        } else {
            state.categories.filter { it.name.contains(trimmed, ignoreCase = true) }
        }
    }

    JiShiLazyColumn(
        contentPadding = PaddingValues(top = 54.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                text = "我的收藏",
                color = JiShiColors.TextTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            QuietSearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(20.dp))
        }

        state.message?.takeUnless { state.isSyncing || isSyncDisplayMessage(it) }?.let {
            item {
                MessageBar(
                    message = it,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            SectionLabel(
                text = "分类 · ${filteredCategories.size}",
                modifier = Modifier.padding(horizontal = 24.dp),
                action = {
                    SyncStatusButton(
                        text = syncButtonText(state),
                        isSyncing = state.isSyncing,
                        onClick = actions.startRednoteSync,
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
        }

        items(filteredCategories, key = { it.id }) { category ->
            CategoryListItem(
                category = category,
                onOpen = { actions.openCategory(category) },
                onEdit = { actions.editCategory(category.id) },
            )
        }

        if (filteredCategories.isEmpty()) {
            item {
                Spacer(Modifier.height(48.dp))
                EmptyState("还没有同步收藏", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun syncButtonText(state: AppUiState): String {
    val message = state.message.orEmpty()
    return when {
        state.isSyncing -> "同步中"
        message.startsWith("已更新") -> message
        else -> "同步"
    }
}

private fun isSyncDisplayMessage(message: String): Boolean {
    if (message.startsWith("同步失败")) return false
    return message.startsWith("已更新") ||
        message.startsWith("正在") ||
        message.contains("同步") ||
        message.contains("WebView") ||
        message.contains("获取") ||
        message.contains("入库")
}
