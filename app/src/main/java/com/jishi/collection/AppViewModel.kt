package com.jishi.collection

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppRepository(application)
    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        refreshHome()
    }

    fun refreshHome() {
        viewModelScope.launch {
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    selectedCategoryName = home.categories.firstOrNull { category ->
                        category.id == it.selectedCategoryId
                    }?.name ?: it.selectedCategoryName,
                    message = null,
                )
            }
        }
    }

    fun openCategory(category: CategorySummary) {
        viewModelScope.launch {
            val notes = repository.notesForCategory(category.id)
            _uiState.update {
                it.copy(
                    screen = AppScreen.CategoryNotes,
                    selectedCategoryId = category.id,
                    selectedCategoryName = category.name,
                    notes = notes,
                    message = null,
                )
            }
        }
    }

    fun editCategory(categoryId: String) {
        val categoryName = uiState.value.categories.firstOrNull { it.id == categoryId }?.name ?: "分类"
        _uiState.update { it.copy(message = "分类编辑入口已预留：$categoryName") }
    }

    fun startRednoteSync() {
        _uiState.update {
            it.copy(
                rednoteSyncRequested = true,
                isSyncing = true,
                message = "正在通过隐藏 WebView 同步小红书收藏...",
            )
        }
    }

    fun updateRednoteSyncStatus(message: String, done: Boolean) {
        if (!done) {
            _uiState.update { it.copy(isSyncing = true, message = message) }
            return
        }
        viewModelScope.launch {
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    rednoteSyncRequested = false,
                    isSyncing = false,
                    categories = home.categories,
                    suggestions = home.suggestions,
                    message = message,
                )
            }
        }
    }

    fun syncDemo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = "正在同步示例收藏...") }
            val result = repository.syncDemoNotes()
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    categories = home.categories,
                    suggestions = home.suggestions,
                    message = "新增 ${result.inserted} 条收藏，待确认分类 ${home.suggestions.size} 组",
                )
            }
        }
    }

    fun syncFromJson(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = "收到 WebView 收藏数据，正在入库...") }
            runCatching { repository.syncFromJson(json) }
                .onSuccess { result ->
                    val home = repository.loadHome()
                    _uiState.update {
                        val hiddenSyncActive = it.rednoteSyncRequested
                        it.copy(
                            isSyncing = hiddenSyncActive,
                            categories = home.categories,
                            suggestions = home.suggestions,
                            message = if (hiddenSyncActive) {
                                "已入库 ${result.inserted} 条收藏，继续同步..."
                            } else {
                                "新增 ${result.inserted} 条收藏，待确认分类 ${home.suggestions.size} 组"
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            rednoteSyncRequested = false,
                            isSyncing = false,
                            message = "同步失败：${error.message ?: "数据格式异常"}",
                        )
                    }
                }
        }
    }

    fun acceptSuggestion(suggestionId: String, name: String) {
        viewModelScope.launch {
            repository.acceptSuggestion(suggestionId, name)
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    message = "已创建分类：$name",
                )
            }
        }
    }

    fun dismissSuggestion(suggestionId: String) {
        viewModelScope.launch {
            repository.dismissSuggestion(suggestionId)
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    message = "已保留在待整理",
                )
            }
        }
    }

    fun clearMetadataCache() {
        viewModelScope.launch {
            repository.clearMetadataCache()
            _uiState.update { it.copy(message = "已清除轻量展示缓存") }
        }
    }

    fun clearAllLocalData() {
        viewModelScope.launch {
            repository.clearAllLocalData()
            val home = repository.loadHome()
            _uiState.update {
                AppUiState(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    message = "已清除本地索引和分类关系",
                )
            }
        }
    }

    fun clearWebLoginState() {
        repository.clearWebLoginState()
        _uiState.update {
            it.copy(
                rednoteSyncRequested = false,
                isSyncing = false,
                message = "已清除 WebView 登录态和小红书签名本地数据",
            )
        }
    }

    fun openNote(note: NoteCard) {
        val context = getApplication<Application>()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(note.noteUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                _uiState.update { state -> state.copy(message = "无法打开笔记链接") }
            }
    }

    fun go(screen: AppScreen) {
        _uiState.update { it.copy(screen = screen, message = null) }
    }
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Home,
    val categories: List<CategorySummary> = emptyList(),
    val suggestions: List<PendingCategorySuggestion> = emptyList(),
    val notes: List<NoteCard> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCategoryName: String = "",
    val isSyncing: Boolean = false,
    val rednoteSyncRequested: Boolean = false,
    val message: String? = null,
)

enum class AppScreen {
    Home,
    Login,
    CategoryNotes,
    Suggestions,
    Settings,
}
