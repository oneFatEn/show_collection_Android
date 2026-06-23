package com.jishi.collection.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.jishi.collection.AppActions
import com.jishi.collection.AppScreen
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.TopTextButton
import com.jishi.collection.ui.screens.HiddenRednoteSyncWebView
import com.jishi.collection.ui.screens.HomeScreen
import com.jishi.collection.ui.screens.LoginScreen
import com.jishi.collection.ui.screens.NotesScreen
import com.jishi.collection.ui.screens.SettingsScreen
import com.jishi.collection.ui.screens.SuggestionsScreen
import com.jishi.collection.ui.theme.JiShiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(state: AppUiState, actions: AppActions) {
    Scaffold(
        topBar = {
            if (state.screen != AppScreen.Home) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (state.screen) {
                                AppScreen.Home -> "分类"
                                AppScreen.Login -> "小红书登录"
                                AppScreen.CategoryNotes -> state.selectedCategoryName
                                AppScreen.Suggestions -> "待确认分类"
                                AppScreen.Settings -> "设置"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        TopTextButton(text = "返回") {
                            actions.go(AppScreen.Home)
                            actions.refreshHome()
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = JiShiColors.Paper),
                )
            }
        },
        containerColor = JiShiColors.Paper,
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(padding)) {
            when (state.screen) {
                AppScreen.Home -> HomeScreen(state, actions)
                AppScreen.Login -> LoginScreen(actions.syncFromJson)
                AppScreen.CategoryNotes -> NotesScreen(state, actions.openNote)
                AppScreen.Suggestions -> SuggestionsScreen(state, actions)
                AppScreen.Settings -> SettingsScreen(actions)
            }
            if (state.rednoteSyncRequested) {
                HiddenRednoteSyncWebView(
                    syncFromJson = actions.syncFromJson,
                    postStatus = actions.updateRednoteSyncStatus,
                )
            }
        }
    }
}
