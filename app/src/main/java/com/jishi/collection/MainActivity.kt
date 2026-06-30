package com.jishi.collection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jishi.collection.ui.AppRoot
import com.jishi.collection.ui.theme.JiShiTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JiShiTheme {
                val state by viewModel.uiState.collectAsState()
                AppRoot(
                    state = state,
                    actions = AppActions(
                        go = viewModel::go,
                        back = viewModel::back,
                        refreshHome = viewModel::refreshHome,
                        startRednoteSync = viewModel::startRednoteSync,
                        onRednoteLoginReady = viewModel::onRednoteLoginReady,
                        updateRednoteSyncStatus = viewModel::updateRednoteSyncStatus,
                        completeRednoteSync = viewModel::completeRednoteSync,
                        syncDemo = viewModel::syncDemo,
                        syncFromJson = viewModel::syncFromJson,
                        openCategory = viewModel::openCategory,
                        openSearchResult = viewModel::openSearchResult,
                        editCategory = viewModel::editCategory,
                        dismissCategoryEditor = viewModel::dismissCategoryEditor,
                        saveCategoryName = viewModel::saveCategoryName,
                        openNote = viewModel::openNote,
                        acceptSuggestion = viewModel::acceptSuggestion,
                        dismissSuggestion = viewModel::dismissSuggestion,
                        clearWebLoginState = viewModel::clearWebLoginState,
                        clearMetadataCache = viewModel::clearMetadataCache,
                        clearAllLocalData = viewModel::clearAllLocalData,
                        clearInvalidNotes = viewModel::clearInvalidNotes,
                    ),
                )
            }
        }
    }
}

data class AppActions(
    val go: (AppScreen) -> Unit,
    val back: () -> Unit,
    val refreshHome: () -> Unit,
    val startRednoteSync: () -> Unit,
    val onRednoteLoginReady: () -> Unit,
    val updateRednoteSyncStatus: (String, Boolean) -> Unit,
    val completeRednoteSync: (String) -> Unit,
    val syncDemo: () -> Unit,
    val syncFromJson: (String) -> Unit,
    val openCategory: (CategorySummary) -> Unit,
    val openSearchResult: (String, String?) -> Unit,
    val editCategory: (String) -> Unit,
    val dismissCategoryEditor: () -> Unit,
    val saveCategoryName: (String, String) -> Unit,
    val openNote: (NoteCard) -> Unit,
    val acceptSuggestion: (String, String) -> Unit,
    val dismissSuggestion: (String) -> Unit,
    val clearWebLoginState: () -> Unit,
    val clearMetadataCache: () -> Unit,
    val clearAllLocalData: () -> Unit,
    val clearInvalidNotes: () -> Unit,
)
