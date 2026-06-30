package com.jishi.collection.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.jishi.collection.AppUiState
import com.jishi.collection.LocalDatabase
import com.jishi.collection.NoteCard
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.components.NoteListItem

@Composable
fun NotesScreen(
    state: AppUiState,
    openNote: (NoteCard) -> Unit,
    clearInvalidNotes: () -> Unit,
) {
    if (state.notes.isEmpty()) {
        EmptyState("这个分类里还没有收藏", modifier = Modifier.fillMaxSize())
        return
    }
    val isInvalidFolder = state.selectedCategoryId == LocalDatabase.CATEGORY_INVALID
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var highlightedNoteId by remember(state.highlightedNoteId) { mutableStateOf(state.highlightedNoteId) }

    LaunchedEffect(state.highlightedNoteId, state.notes) {
        val targetId = state.highlightedNoteId ?: return@LaunchedEffect
        val noteIndex = state.notes.indexOfFirst { it.rednoteId == targetId }
        if (noteIndex < 0) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val estimatedRowHeight = with(density) { 116.dp.roundToPx() }
        val centerOffset = -((viewportHeight - estimatedRowHeight) / 2).coerceAtLeast(0)
        listState.animateScrollToItem(noteIndex, centerOffset)
    }

    LaunchedEffect(highlightedNoteId) {
        if (highlightedNoteId != null) {
            kotlinx.coroutines.delay(1300)
            highlightedNoteId = null
        }
    }

    JiShiLazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
    ) {
        if (isInvalidFolder) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(onClick = clearInvalidNotes, modifier = Modifier.fillMaxWidth()) {
                        Text("清理失效收藏")
                    }
                }
            }
        }
        items(state.notes, key = { it.rednoteId }) { note ->
            NoteListItem(
                note = note,
                onClick = { openNote(note) },
                highlighted = note.rednoteId == highlightedNoteId,
            )
        }
    }
}
