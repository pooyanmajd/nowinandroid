/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.samples.apps.nowinandroid.feature.bookmarks.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTextButton
import kotlinx.coroutines.launch

@Composable
internal fun BookmarkNoteDialog(
    resourceId: String,
    title: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: suspend (String) -> Boolean,
) {
    var draft by rememberSaveable(resourceId) { mutableStateOf(initialNote) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val save: (String) -> Unit = { text ->
        saving = true
        scope.launch {
            try {
                if (onSave(text)) onDismiss() else failed = true
            } finally {
                saving = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.feature_bookmarks_impl_private_note)) },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(MAX_NOTE_LENGTH) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.feature_bookmarks_impl_note_label)) },
                    supportingText = {
                        Text(stringResource(R.string.feature_bookmarks_impl_note_count, draft.length, MAX_NOTE_LENGTH))
                    },
                    minLines = 3,
                    maxLines = 6,
                    enabled = !saving,
                )
                if (failed) {
                    Text(stringResource(R.string.feature_bookmarks_impl_write_error), color = MaterialTheme.colorScheme.error)
                }
                if (initialNote.isNotEmpty()) {
                    NiaTextButton(onClick = { save("") }, enabled = !saving) {
                        Text(stringResource(R.string.feature_bookmarks_impl_delete_note))
                    }
                }
            }
        },
        confirmButton = {
            NiaTextButton(onClick = { save(draft) }, enabled = !saving) {
                Text(stringResource(R.string.feature_bookmarks_impl_save))
            }
        },
        dismissButton = {
            NiaTextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.feature_bookmarks_impl_cancel))
            }
        },
    )
}
