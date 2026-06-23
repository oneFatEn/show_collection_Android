package com.jishi.collection.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.AppScreen
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.CategoryEditDialog
import com.jishi.collection.ui.components.TopTextButton
import com.jishi.collection.ui.screens.HiddenRednoteSyncWebView
import com.jishi.collection.ui.screens.HomeScreen
import com.jishi.collection.ui.screens.LoginScreen
import com.jishi.collection.ui.screens.NotesScreen
import com.jishi.collection.ui.screens.ProfileScreen
import com.jishi.collection.ui.screens.SettingsScreen
import com.jishi.collection.ui.theme.JiShiColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(state: AppUiState, actions: AppActions) {
    Scaffold(
        topBar = {
            if (state.screen != AppScreen.Home && state.screen != AppScreen.Profile) {
                TopAppBar(
                    title = {
                        Text(
                            text = when (state.screen) {
                                AppScreen.Home -> "分类"
                                AppScreen.Profile -> "我的"
                                AppScreen.Login -> "小红书登录"
                                AppScreen.CategoryNotes -> state.selectedCategoryName
                                AppScreen.Settings -> "设置"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = {
                        TopTextButton(text = "返回") {
                            actions.back()
                            actions.refreshHome()
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = JiShiColors.Paper),
                )
            }
        },
        bottomBar = {
            if (state.editingCategoryId == null && (state.screen == AppScreen.Home || state.screen == AppScreen.Profile)) {
                BottomTabs(
                    selected = state.screen,
                    onSelect = actions.go,
                )
            }
        },
        containerColor = JiShiColors.Paper,
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            Box(modifier = if (state.editingCategoryId != null) Modifier.blur(16.dp) else Modifier) {
                when (state.screen) {
                    AppScreen.Home -> HomeScreen(state, actions)
                    AppScreen.Profile -> ProfileScreen(state, actions)
                    AppScreen.Login -> LoginScreen(actions.syncFromJson)
                    AppScreen.CategoryNotes -> NotesScreen(state, actions.openNote)
                    AppScreen.Settings -> SettingsScreen(actions)
                }
            }
            if (state.rednoteSyncRequested) {
                HiddenRednoteSyncWebView(
                    syncFromJson = actions.syncFromJson,
                    postStatus = actions.updateRednoteSyncStatus,
                )
            }
            val editingCategory = state.categories.firstOrNull { it.id == state.editingCategoryId }
            if (editingCategory != null) {
                CategoryEditDialog(
                    category = editingCategory,
                    onDismiss = actions.dismissCategoryEditor,
                    onSave = { name -> actions.saveCategoryName(editingCategory.id, name) },
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(selected: AppScreen, onSelect: (AppScreen) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(JiShiColors.Paper)
            .navigationBarsPadding(),
    ) {
        HorizontalDivider(color = JiShiColors.Hairline, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .padding(start = 32.dp, end = 32.dp, top = 12.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomTabItem(
                text = "首页",
                selected = false,
                icon = BottomIcon.Home,
                onClick = {},
            )
            BottomTabItem(
                text = "分类",
                selected = selected == AppScreen.Home,
                icon = BottomIcon.Grid,
                onClick = { onSelect(AppScreen.Home) },
            )
            BottomTabItem(
                text = "发现",
                selected = false,
                icon = BottomIcon.Compass,
                onClick = {},
            )
            BottomTabItem(
                text = "我的",
                selected = selected == AppScreen.Profile,
                icon = BottomIcon.User,
                onClick = { onSelect(AppScreen.Profile) },
            )
        }
    }
}

@Composable
private fun BottomTabItem(text: String, selected: Boolean, icon: BottomIcon, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BottomTabIcon(
            icon = icon,
            active = selected,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            color = if (selected) JiShiColors.TextPrimary else JiShiColors.TextTertiary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun BottomTabIcon(icon: BottomIcon, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) JiShiColors.TextPrimary else JiShiColors.TextTertiary
    Canvas(modifier = modifier.size(22.dp)) {
        val stroke = Stroke(
            width = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (icon) {
            BottomIcon.Home -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.13f, size.height * 0.43f)
                    lineTo(size.width * 0.5f, size.height * 0.14f)
                    lineTo(size.width * 0.87f, size.height * 0.43f)
                    lineTo(size.width * 0.87f, size.height * 0.86f)
                    lineTo(size.width * 0.13f, size.height * 0.86f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawLine(color, Offset(size.width * 0.38f, size.height * 0.86f), Offset(size.width * 0.38f, size.height * 0.56f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.62f, size.height * 0.86f), Offset(size.width * 0.62f, size.height * 0.56f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.38f, size.height * 0.56f), Offset(size.width * 0.62f, size.height * 0.56f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
            }
            BottomIcon.Grid -> {
                val w = size.width * 0.31f
                val gap = size.width * 0.13f
                val x1 = size.width * 0.16f
                val x2 = x1 + w + gap
                val y1 = size.height * 0.16f
                val y2 = y1 + w + gap
                listOf(x1 to y1, x2 to y1, x1 to y2, x2 to y2).forEachIndexed { index, (x, y) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, y),
                        size = Size(w, w),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                        style = if (active && index == 0) androidx.compose.ui.graphics.drawscope.Fill else stroke,
                    )
                }
            }
            BottomIcon.Compass -> {
                drawCircle(color, radius = size.minDimension * 0.38f, center = Offset(size.width / 2f, size.height / 2f), style = stroke)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.68f, size.height * 0.32f)
                    lineTo(size.width * 0.59f, size.height * 0.59f)
                    lineTo(size.width * 0.32f, size.height * 0.68f)
                    lineTo(size.width * 0.41f, size.height * 0.41f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            BottomIcon.User -> {
                drawCircle(color, radius = size.minDimension * 0.17f, center = Offset(size.width * 0.5f, size.height * 0.34f), style = stroke)
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.17f, size.height * 0.5f),
                    size = Size(size.width * 0.66f, size.height * 0.52f),
                    style = stroke,
                )
            }
        }
    }
}

private enum class BottomIcon {
    Home,
    Grid,
    Compass,
    User,
}
