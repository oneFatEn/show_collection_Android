package com.jishi.collection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun SettingsScreen(actions: AppActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JiShiColors.Paper)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("数据安全", color = JiShiColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "登录态仅保存在 WebView；本地索引和缓存可随时清除。",
            color = JiShiColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Divider(color = JiShiColors.Hairline)
        OutlinedButton(
            onClick = actions.clearWebLoginState,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) { Text("退出小红书登录") }
        OutlinedButton(
            onClick = actions.clearMetadataCache,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) { Text("清除展示缓存") }
        OutlinedButton(
            onClick = actions.clearAllLocalData,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
        ) { Text("清除本地索引和分类关系") }
    }
}

