package com.jishi.collection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.AppScreen
import com.jishi.collection.AppUiState
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun ProfileScreen(state: AppUiState, actions: AppActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JiShiColors.Paper)
            .padding(PaddingValues(start = 40.dp, end = 40.dp, top = 36.dp, bottom = 28.dp)),
    ) {
        Text(
            text = "我的",
            color = JiShiColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(28.dp))

        state.message?.takeUnless { state.isSyncing || isSyncDisplayMessage(it) }?.let {
            MessageBar(message = it)
            Spacer(Modifier.height(12.dp))
        }

        ProfileActionRow(text = "小红书登录", onClick = { actions.go(AppScreen.Login) })
        ProfileActionRow(text = "设置", onClick = { actions.go(AppScreen.Settings) })
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

@Composable
private fun ProfileActionRow(text: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                color = JiShiColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = ">",
                color = JiShiColors.TextTertiary,
                fontSize = 18.sp,
            )
        }
        HorizontalDivider(color = JiShiColors.Hairline, thickness = 0.8.dp)
    }
}
