package com.jishi.collection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.jishi.collection.AppActions
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.CategoryListItem
import com.jishi.collection.ui.components.CategorySearchField
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.components.SectionLabel
import com.jishi.collection.ui.components.SearchDropdown
import com.jishi.collection.ui.components.SearchSuggestion
import com.jishi.collection.ui.components.SyncStatusButton
import com.jishi.collection.ui.components.rememberCategorySearchState
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun HomeScreen(state: AppUiState, actions: AppActions) {
    val search = rememberCategorySearchState(state.categories)
    val categories = state.categories
    val displayedMessage = state.message?.takeUnless { state.isSyncing || isSyncDisplayMessage(it) }

    fun selectSearchResult(result: SearchSuggestion) {
        search.clear()
        search.dismissDropdown()
        when (result) {
            is SearchSuggestion.Category -> actions.openSearchResult(result.category.id, null)
            is SearchSuggestion.Note -> {
                val targetCategoryId = result.note.categoryId
                    .takeIf { id -> state.categories.any { it.id == id } }
                    ?: state.categories.firstOrNull { it.name == result.note.categoryName }?.id
                    ?: return
                actions.openSearchResult(targetCategoryId, result.note.rednoteId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        JiShiLazyColumn(
            modifier = if (search.isDropdownVisible) Modifier.blur(16.dp) else Modifier,
            contentPadding = PaddingValues(top = 54.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SearchHeader(
                    search = search,
                    showDropdown = false,
                    categories = state.categories,
                    notes = state.searchableNotes,
                    onResultClick = ::selectSearchResult,
                )
                Spacer(Modifier.height(20.dp))
            }

            displayedMessage?.let {
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
                    text = "分类 · ${categories.size}",
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

            items(categories, key = { it.id }) { category ->
                CategoryListItem(
                    category = category,
                    onOpen = { actions.openCategory(category) },
                    onEdit = { actions.editCategory(category.id) },
                )
            }

            if (categories.isEmpty()) {
                item {
                    Spacer(Modifier.height(48.dp))
                    EmptyState("还没有同步收藏", modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (search.isDropdownVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .background(Color(0x8CF8F7F5))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = search::dismissDropdown,
                    ),
            )
            SearchHeader(
                search = search,
                showDropdown = true,
                categories = state.categories,
                notes = state.searchableNotes,
                onResultClick = ::selectSearchResult,
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

@Composable
private fun SearchHeader(
    search: com.jishi.collection.ui.components.CategorySearchState,
    showDropdown: Boolean,
    categories: List<com.jishi.collection.CategorySummary>,
    notes: List<com.jishi.collection.SearchableNote>,
    onResultClick: (SearchSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 54.dp),
    ) {
        Text(
            text = "我的收藏",
            color = JiShiColors.TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        CategorySearchField(
            state = search,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        if (showDropdown) {
            Spacer(Modifier.height(10.dp))
            SearchDropdown(
                query = search.query,
                categories = categories,
                notes = notes,
                onResultClick = onResultClick,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
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
