package com.jishi.collection.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jishi.collection.CategorySummary

@Stable
class CategorySearchState internal constructor() {
    var query by mutableStateOf("")
        private set

    var focused by mutableStateOf(false)
        private set

    private var categories by mutableStateOf<List<CategorySummary>>(emptyList())

    val filteredCategories: List<CategorySummary>
        get() = filterCategories(categories, query)

    val isDropdownVisible: Boolean
        get() = query.trim().isNotBlank()

    fun updateCategories(next: List<CategorySummary>) {
        categories = next
    }

    fun updateQuery(next: String) {
        query = next
        if (next.isNotBlank()) {
            focused = true
        }
    }

    fun updateFocus(next: Boolean) {
        focused = next
    }

    fun dismissDropdown() {
        focused = false
    }

    fun submit() {
        query = query.trim()
    }

    fun clear() {
        query = ""
    }
}

@Composable
fun rememberCategorySearchState(categories: List<CategorySummary>): CategorySearchState {
    val state = remember { CategorySearchState() }
    LaunchedEffect(categories) {
        state.updateCategories(categories)
    }
    return state
}

@Composable
fun CategorySearchField(
    state: CategorySearchState,
    modifier: Modifier = Modifier,
) {
    QuietSearchField(
        value = state.query,
        onValueChange = state::updateQuery,
        onSearch = state::submit,
        onFocusChanged = state::updateFocus,
        modifier = modifier,
    )
}

fun filterCategories(categories: List<CategorySummary>, query: String): List<CategorySummary> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return categories
    return categories.filter { category ->
        category.name.contains(trimmed, ignoreCase = true)
    }
}
