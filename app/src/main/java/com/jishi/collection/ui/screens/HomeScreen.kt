package com.jishi.collection.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jishi.collection.AppScreen
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.CategoryListItem
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.components.QuietPillButton
import com.jishi.collection.ui.components.QuietSearchField
import com.jishi.collection.ui.components.SectionLabel
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
        contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "我的收藏",
                    color = JiShiColors.TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.weight(1f),
                )
                if (state.suggestions.isNotEmpty()) {
                    TextButton(onClick = { actions.go(AppScreen.Suggestions) }) {
                        Text("待确认 ${state.suggestions.size}", color = JiShiColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = { actions.go(AppScreen.Login) }) {
                    Text("登录", color = JiShiColors.TextSecondary, fontSize = 12.sp)
                }
                TextButton(onClick = { actions.go(AppScreen.Settings) }) {
                    Text("设置", color = JiShiColors.TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            QuietSearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
        }

        state.message?.let {
            item {
                MessageBar(message = it)
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            SectionLabel(
                text = "分类 · ${filteredCategories.size}",
                action = {
                    QuietPillButton(
                        text = "同步",
                        enabled = !state.isSyncing,
                        onClick = actions.startRednoteSync,
                        leadingSymbol = "↻",
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
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
