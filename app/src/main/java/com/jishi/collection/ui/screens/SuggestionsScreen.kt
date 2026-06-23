package com.jishi.collection.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jishi.collection.AppActions
import com.jishi.collection.AppUiState
import com.jishi.collection.PendingCategorySuggestion
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.theme.JiShiColors

@Composable
fun SuggestionsScreen(state: AppUiState, actions: AppActions) {
    if (state.suggestions.isEmpty()) {
        EmptyState("没有待确认分类", modifier = Modifier.fillMaxSize())
        return
    }
    JiShiLazyColumn(
        contentPadding = PaddingValues(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.suggestions, key = { it.id }) { suggestion ->
            SuggestionCard(suggestion, actions)
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: PendingCategorySuggestion, actions: AppActions) {
    var name by remember(suggestion.id) { mutableStateOf(suggestion.suggestedName) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = JiShiColors.Surface),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${suggestion.rednoteIds.size} 条新增收藏可能适合新分类", fontWeight = FontWeight.SemiBold)
            Text(suggestion.reason, color = JiShiColors.TextSecondary, fontSize = 13.sp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { actions.acceptSuggestion(suggestion.id, name) }) { Text("创建") }
                OutlinedButton(onClick = { actions.dismissSuggestion(suggestion.id) }) { Text("放入待整理") }
            }
        }
    }
}
