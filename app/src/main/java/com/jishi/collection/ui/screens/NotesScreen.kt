package com.jishi.collection.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jishi.collection.AppUiState
import com.jishi.collection.NoteCard
import com.jishi.collection.ui.components.EmptyState
import com.jishi.collection.ui.components.JiShiLazyColumn
import com.jishi.collection.ui.components.NoteListItem

@Composable
fun NotesScreen(state: AppUiState, openNote: (NoteCard) -> Unit) {
    if (state.notes.isEmpty()) {
        EmptyState("这个分类里还没有收藏", modifier = Modifier.fillMaxSize())
        return
    }
    JiShiLazyColumn(
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
    ) {
        items(state.notes, key = { it.rednoteId }) { note ->
            NoteListItem(note = note, onClick = { openNote(note) })
        }
    }
}
