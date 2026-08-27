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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.AppScreen
import com.jishi.collection.AppUiState
import com.jishi.collection.AiClassificationSettings
import com.jishi.collection.ui.components.MessageBar
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun ProfileScreen(state: AppUiState, actions: AppActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JiShiColors.Paper)
            .verticalScroll(rememberScrollState())
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
        Spacer(Modifier.height(28.dp))
        AiClassificationSettingsPanel(state = state, actions = actions)
    }
}

private fun isSyncDisplayMessage(message: String): Boolean {
    if (message.startsWith("同步失败")) return false
    return message.startsWith("已更新") ||
        message.startsWith("已同步") ||
        message.startsWith("正在") ||
        message.contains("同步") ||
        message.contains("WebView") ||
        message.contains("获取") ||
        message.contains("入库")
}

@Composable
private fun AiClassificationSettingsPanel(state: AppUiState, actions: AppActions) {
    var deepSeekKey by remember(state.aiSettings) { mutableStateOf(state.aiSettings.deepSeekApiKey) }
    val smartEnabled = deepSeekKey.isNotBlank()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "AI 自动分类",
            color = JiShiColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (smartEnabled) {
                "同步后使用固定一级分类、受控二级分类和多标签自动整理"
            } else {
                "未填写 DeepSeek Key 时使用固定分类词"
            },
            color = JiShiColors.TextTertiary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        ProfileTextField(
            value = deepSeekKey,
            onValueChange = { deepSeekKey = it },
            label = "DeepSeek API Key",
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                actions.saveAiSettings(
                    AiClassificationSettings(
                        deepSeekApiKey = deepSeekKey,
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
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
