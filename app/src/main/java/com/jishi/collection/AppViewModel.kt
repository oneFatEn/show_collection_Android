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
        _uiState.update { it.copy(aiSettings = repository.loadAiSettings()) }
        refreshHome()
    }

    fun refreshHome() {
        viewModelScope.launch {
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    searchableNotes = home.searchableNotes,
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
                    highlightedNoteId = null,
                    message = null,
                )
            }
        }
    }

    fun openSearchResult(categoryId: String, noteId: String?) {
        viewModelScope.launch {
            val category = _uiState.value.categories.firstOrNull { it.id == categoryId } ?: return@launch
            val notes = repository.notesForCategory(category.id)
            _uiState.update {
                it.copy(
                    screen = AppScreen.CategoryNotes,
                    previousScreen = AppScreen.Home,
                    selectedCategoryId = category.id,
                    selectedCategoryName = category.name,
                    notes = notes,
                    highlightedNoteId = noteId,
                    message = null,
                )
            }
        }
    }

    fun editCategory(categoryId: String) {
        _uiState.update {
            it.copy(
                editingCategoryId = categoryId,
                message = null,
            )
        }
    }

    fun dismissCategoryEditor() {
        _uiState.update { it.copy(editingCategoryId = null) }
    }

    fun saveCategoryName(categoryId: String, name: String) {
        viewModelScope.launch {
            repository.renameCategory(categoryId, name)
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    categories = home.categories,
                    suggestions = home.suggestions,
                    searchableNotes = home.searchableNotes,
                    selectedCategoryName = home.categories.firstOrNull { category ->
                        category.id == it.selectedCategoryId
                    }?.name ?: it.selectedCategoryName,
                    editingCategoryId = null,
                    message = null,
                )
            }
        }
    }

    fun startRednoteSync() {
        if (!repository.hasRednoteLoginState()) {
            _uiState.update {
                it.copy(
                    screen = AppScreen.Login,
                    previousScreen = it.screen,
                    pendingSyncAfterLogin = true,
                    rednoteSyncRequested = false,
                    isSyncing = false,
                    message = "请先登录小红书，登录成功后会自动同步收藏",
                )
            }
            return
        }
        _uiState.update {
                it.copy(
                    rednoteSyncRequested = true,
                    isSyncing = true,
                    syncStartActiveCount = it.activeCategoryCount(),
                    incrementalAiNoteIds = emptySet(),
                    message = null,
                )
            }
    }

    fun onRednoteLoginReady() {
        _uiState.update {
            if (it.pendingSyncAfterLogin) {
                it.copy(
                    screen = AppScreen.Home,
                    previousScreen = null,
                    pendingSyncAfterLogin = false,
                    rednoteSyncRequested = true,
                    isSyncing = true,
                    syncStartActiveCount = it.activeCategoryCount(),
                    incrementalAiNoteIds = emptySet(),
                    message = "登录成功，正在同步收藏...",
                )
            } else {
                it.copy(
                    screen = AppScreen.Home,
                    previousScreen = null,
                    pendingSyncAfterLogin = false,
                    message = "小红书登录态已就绪",
                )
            }
        }
    }

    fun updateRednoteSyncStatus(message: String, done: Boolean) {
        if (!done) {
            _uiState.update { it.copy(isSyncing = true, message = message) }
            return
        }
        if (message.requiresRednoteLogin()) {
            _uiState.update {
                it.copy(
                    screen = AppScreen.Login,
                    previousScreen = AppScreen.Home,
                    pendingSyncAfterLogin = true,
                    rednoteSyncRequested = false,
                    isSyncing = false,
                    message = "请先登录小红书，登录成功后会自动同步收藏",
                )
            }
            return
        }
        viewModelScope.launch {
            val home = repository.loadHome()
            _uiState.update {
                if (!it.rednoteSyncRequested) {
                    return@update it.copy(
                        categories = home.categories,
                        suggestions = home.suggestions,
                        searchableNotes = home.searchableNotes,
                    )
                }
                it.copy(
                    rednoteSyncRequested = false,
                    isSyncing = false,
                    categories = home.categories,
                    suggestions = home.suggestions,
                    searchableNotes = home.searchableNotes,
                    message = syncDoneMessage(message, home.categories.sumOf { category -> category.count }),
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
                    searchableNotes = home.searchableNotes,
                    message = "已更新 +${result.inserted}",
                )
            }
        }
    }

    fun syncFromJson(json: String) {
        val fromHiddenSync = _uiState.value.rednoteSyncRequested
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = "收到 WebView 收藏数据，正在入库...") }
            runCatching { repository.syncFromJson(json) }
                .onSuccess { result ->
                    val home = repository.loadHome()
                    _uiState.update {
                        if (fromHiddenSync && !it.rednoteSyncRequested) {
                            return@update it.copy(
                                categories = home.categories,
                                suggestions = home.suggestions,
                                searchableNotes = home.searchableNotes,
                            )
                        }
                        it.copy(
                            isSyncing = fromHiddenSync,
                            incrementalAiNoteIds = it.incrementalAiNoteIds + result.changedNoteIds,
                            categories = home.categories,
                            suggestions = home.suggestions,
                            searchableNotes = home.searchableNotes,
                            message = if (fromHiddenSync) {
                                "已入库 ${result.inserted} 条收藏，继续同步..."
                            } else {
                                "已更新 +${result.inserted}"
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

    fun completeRednoteSync(json: String) {
        viewModelScope.launch {
            runCatching { repository.completeSync(json) }
                .onSuccess { result ->
                    val home = repository.loadHome()
                    _uiState.update {
                        if (!result.success && result.message.requiresRednoteLogin()) {
                            return@update it.copy(
                                screen = AppScreen.Login,
                                previousScreen = AppScreen.Home,
                                pendingSyncAfterLogin = true,
                                rednoteSyncRequested = false,
                                isSyncing = false,
                                categories = home.categories,
                                suggestions = home.suggestions,
                                searchableNotes = home.searchableNotes,
                                message = "请先登录小红书，登录成功后会自动同步收藏",
                            )
                        }
                        val netChange = home.categories.activeCount() - it.syncStartActiveCount
                        val changedIds = it.incrementalAiNoteIds
                        val willMatch = result.success && changedIds.isNotEmpty() && it.aiSettings.smartClassificationEnabled
                        it.copy(
                            rednoteSyncRequested = false,
                            isSyncing = false,
                            isAiClassifying = willMatch,
                            syncStartActiveCount = home.categories.activeCount(),
                            categories = home.categories,
                            suggestions = home.suggestions,
                            searchableNotes = home.searchableNotes,
                            message = if (result.success) {
                                if (willMatch) {
                                    "同步完成，正在整理新增笔记..."
                                } else {
                                    syncNetMessage(netChange)
                                }
                            } else {
                                "同步失败：${result.message}"
                            },
                        )
                    }
                    if (result.success) {
                        val changedIds = _uiState.value.incrementalAiNoteIds
                        if (changedIds.isNotEmpty() && _uiState.value.aiSettings.smartClassificationEnabled) {
                            runIncrementalClassification(changedIds)
                        } else {
                            _uiState.update { it.copy(incrementalAiNoteIds = emptySet()) }
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            rednoteSyncRequested = false,
                            isSyncing = false,
                            message = "同步状态保存失败：${error.message ?: "未知错误"}",
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
                    searchableNotes = home.searchableNotes,
                    message = "待整理不能直接创建一级分类，请先完成受限分类",
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
                    searchableNotes = home.searchableNotes,
                    message = "已保留在待整理",
                )
            }
        }
    }

    fun saveAiSettings(settings: AiClassificationSettings) {
        viewModelScope.launch {
            repository.saveAiSettings(settings)
            _uiState.update {
                it.copy(
                    screen = AppScreen.Home,
                    previousScreen = null,
                    aiSettings = settings,
                    message = if (settings.smartClassificationEnabled) {
                        "已保存，点击首页“分类”按钮开始整理收藏"
                    } else {
                        "已保存，未配置 API Key 时使用固定分类词"
                    },
                )
            }
        }
    }

    fun classifyCollections() {
        _uiState.update {
            it.copy(
                isAiClassifying = true,
                message = if (it.aiSettings.smartClassificationEnabled) {
                    "粗分类中，正在等待大模型返回..."
                } else {
                    "正在按固定分类词整理..."
                },
            )
        }
        if (_uiState.value.aiSettings.smartClassificationEnabled) {
            runCoarseClassification()
        } else {
            runRuleClassification()
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
                    searchableNotes = home.searchableNotes,
                    message = "已清除本地索引和分类关系",
                )
            }
        }
    }

    fun clearInvalidNotes() {
        viewModelScope.launch {
            val deleted = repository.clearInvalidNotes()
            val home = repository.loadHome()
            _uiState.update {
                it.copy(
                    screen = AppScreen.Home,
                    previousScreen = null,
                    categories = home.categories,
                    suggestions = home.suggestions,
                    searchableNotes = home.searchableNotes,
                    notes = emptyList(),
                    selectedCategoryId = null,
                    selectedCategoryName = "",
                    message = "已清理 $deleted 条失效收藏",
                )
            }
        }
    }

    fun clearWebLoginState() {
        viewModelScope.launch {
            repository.clearWebLoginState()
            repository.clearAllLocalData()
            val home = repository.loadHome()
            _uiState.update {
                AppUiState(
                    screen = AppScreen.Home,
                    categories = home.categories,
                    suggestions = home.suggestions,
                    searchableNotes = home.searchableNotes,
                    message = "已退出登录，并清空本地收藏索引",
                )
            }
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
        _uiState.update {
            it.copy(
                screen = screen,
                previousScreen = if (screen.isTopLevel()) it.previousScreen else it.screen,
                message = null,
            )
        }
    }

    fun back() {
        _uiState.update {
            it.copy(
                screen = it.previousScreen ?: AppScreen.Home,
                previousScreen = null,
                message = null,
            )
        }
    }

    private fun runRuleClassification() {
        viewModelScope.launch {
            runCatching { repository.runRuleClassification() }
                .onSuccess { classified ->
                    finishClassification("规则分类完成：整理 $classified 条")
                }
                .onFailure { error ->
                    failClassification("规则分类失败：${error.message ?: "未知错误"}")
                }
        }
    }

    /** 分类按钮：粗分类 + 约束裂变 */
    private fun runCoarseClassification() {
        viewModelScope.launch {
            runCatching { repository.runCoarseClassification() }
                .onSuccess { result ->
                    finishClassification(coarseClassificationMessage(result))
                }
                .onFailure { error ->
                    failClassification("AI 分类失败：${error.message ?: "接口异常"}")
                }
        }
    }

    /** 同步后的增量路径：只对新增/恢复笔记执行受限结构化分类。 */
    private fun runIncrementalClassification(noteIds: Set<String>) {
        viewModelScope.launch {
            runCatching { repository.runIncrementalClassification(noteIds) }
                .onSuccess { result ->
                    finishClassification(incrementalMatchMessage(result, noteIds.size))
                }
                .onFailure { error ->
                    failClassification("增量分类失败：${error.message ?: "接口异常"}，新增笔记已放入待整理")
                }
        }
    }

    private suspend fun finishClassification(message: String) {
        val home = repository.loadHome()
        val selectedCategoryId = _uiState.value.selectedCategoryId
        val selectedNotes = selectedCategoryId?.let { repository.notesForCategory(it) }
        _uiState.update {
            it.copy(
                categories = home.categories,
                suggestions = home.suggestions,
                searchableNotes = home.searchableNotes,
                notes = selectedNotes ?: it.notes,
                incrementalAiNoteIds = emptySet(),
                isAiClassifying = false,
                message = message,
            )
        }
    }

    private fun failClassification(message: String) {
        _uiState.update {
            it.copy(
                incrementalAiNoteIds = emptySet(),
                isAiClassifying = false,
                message = message,
            )
        }
    }
}

private fun syncDoneMessage(raw: String, fallbackTotal: Int): String {
    if (raw.startsWith("同步失败")) return raw
    if (raw.requiresRednoteLogin()) return raw
    val count = Regex("""共读取\s*(\d+)\s*条""").find(raw)?.groupValues?.getOrNull(1)
        ?: Regex("""(\d+)""").find(raw)?.groupValues?.getOrNull(1)
    return "已同步 ${count ?: fallbackTotal}"
}

private fun syncNetMessage(netChange: Int): String {
    val sign = if (netChange > 0) "+" else ""
    return "已同步 $sign$netChange"
}

private fun AppUiState.activeCategoryCount(): Int {
    return categories.activeCount()
}

private fun List<CategorySummary>.activeCount(): Int {
    return filterNot { it.isInvalid }.sumOf { it.count }
}

private fun String.requiresRednoteLogin(): Boolean {
    return contains("未检测到小红书登录态") ||
        contains("请先登录") ||
        contains("未获取到 user_id") ||
        contains("登录态")
}

data class AppUiState(
    val screen: AppScreen = AppScreen.Home,
    val previousScreen: AppScreen? = null,
    val categories: List<CategorySummary> = emptyList(),
    val suggestions: List<PendingCategorySuggestion> = emptyList(),
    val searchableNotes: List<SearchableNote> = emptyList(),
    val notes: List<NoteCard> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedCategoryName: String = "",
    val highlightedNoteId: String? = null,
    val editingCategoryId: String? = null,
    val isSyncing: Boolean = false,
    val rednoteSyncRequested: Boolean = false,
    val pendingSyncAfterLogin: Boolean = false,
    val syncStartActiveCount: Int = 0,
    val incrementalAiNoteIds: Set<String> = emptySet(),
    val aiSettings: AiClassificationSettings = AiClassificationSettings(),
    val isAiClassifying: Boolean = false,
    val message: String? = null,
)

private fun coarseClassificationMessage(result: AiClassificationRunResult): String {
    result.skippedReason?.let { return it }
    val base = "分类完成：归类 ${result.matched} 条"
    return if (result.splitCategories > 0) "$base，裂变出 ${result.splitCategories} 个细分分类" else base
}

private fun incrementalMatchMessage(result: AiClassificationRunResult, total: Int): String {
    result.skippedReason?.let { return it }
    val unmatched = (total - result.matched).coerceAtLeast(0)
    return if (unmatched > 0) {
        "已同步：${result.matched} 条完成分类，$unmatched 条在待整理"
    } else {
        "已同步：${result.matched} 条完成分类"
    }
}

enum class AppScreen {
    Home,
    Profile,
    Login,
    CategoryNotes,
    Settings,
}

private fun AppScreen.isTopLevel(): Boolean {
    return this == AppScreen.Home || this == AppScreen.Profile
}
