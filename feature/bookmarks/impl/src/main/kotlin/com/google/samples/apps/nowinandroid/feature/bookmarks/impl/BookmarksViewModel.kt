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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject

internal const val MAX_NOTE_LENGTH = 500

data class BookmarkRemoval(val token: Long, val ids: Set<String>)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val userDataRepository: UserDataRepository,
    userNewsResourceRepository: UserNewsResourceRepository,
) : ViewModel() {
    private val mutationMutex = Mutex()
    private var nextRemovalToken = 0L

    var pendingRemoval: BookmarkRemoval? by mutableStateOf(null)
        private set
    val shouldDisplayUndoBookmark: Boolean get() = pendingRemoval != null

    var selectedIds: Set<String> by mutableStateOf(emptySet())
        private set
    var selectionMode by mutableStateOf(false)
        private set
    var isUpdating by mutableStateOf(false)
        private set
    var operationFailed by mutableStateOf(false)
        private set

    val feedUiState: StateFlow<NewsFeedUiState> =
        userNewsResourceRepository.observeAllBookmarked()
            .map<List<UserNewsResource>, NewsFeedUiState>(NewsFeedUiState::Success)
            .onStart { emit(Loading) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)

    val notes: StateFlow<Map<String, String>> = userDataRepository.userData
        .map { it.bookmarkNotes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        // An old process cannot offer Undo. Finish any interrupted removal on entering Saved.
        mutate { userDataRepository.deleteUnbookmarkedNotes() }
        viewModelScope.launch {
            userDataRepository.userData.collect { data ->
                selectedIds = selectedIds.intersect(data.bookmarkedNewsResources)
                if (data.bookmarkedNewsResources.isEmpty()) cancelSelection()
            }
        }
    }

    fun toggleSelection(id: String) {
        if (isUpdating) return
        val visibleIds = (feedUiState.value as? NewsFeedUiState.Success)?.feed?.map { it.id }.orEmpty()
        if (id !in visibleIds) return
        selectionMode = true
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun selectAll() {
        if (isUpdating) return
        selectedIds = (feedUiState.value as? NewsFeedUiState.Success)?.feed
            ?.map { it.id }?.toSet().orEmpty()
        selectionMode = selectedIds.isNotEmpty()
    }

    fun cancelSelection() {
        selectedIds = emptySet()
        selectionMode = false
    }

    fun removeSelected() = removeBookmarks(selectedIds)

    fun removeFromSavedResources(newsResourceId: String) = removeBookmarks(setOf(newsResourceId))

    private fun removeBookmarks(ids: Set<String>) {
        if (ids.isEmpty()) return
        mutate {
            val existingIds = ids.intersect(userDataRepository.userData.first().bookmarkedNewsResources)
            if (existingIds.isNotEmpty()) {
                // Replacing the snackbar closes the previous batch's undo window.
                userDataRepository.deleteUnbookmarkedNotes()
                pendingRemoval = null
                userDataRepository.setNewsResourcesBookmarked(existingIds, false)
                pendingRemoval = BookmarkRemoval(++nextRemovalToken, existingIds)
                cancelSelection()
            }
        }
    }

    fun undoBookmarkRemoval(expectedToken: Long? = pendingRemoval?.token) {
        mutate {
            val removal = pendingRemoval
            if (removal != null && removal.token == expectedToken) {
                userDataRepository.setNewsResourcesBookmarked(removal.ids, true)
                pendingRemoval = null
            }
        }
    }

    fun clearUndoState(expectedToken: Long? = pendingRemoval?.token) {
        mutate {
            if (pendingRemoval?.token == expectedToken) {
                userDataRepository.deleteUnbookmarkedNotes()
                pendingRemoval = null
            }
        }
    }

    suspend fun saveNote(id: String, note: String): Boolean = mutationMutex.withLock {
        if (note.length > MAX_NOTE_LENGTH) return@withLock false
        isUpdating = true
        try {
            userDataRepository.setBookmarkNote(id, note)
            true
        } catch (_: IOException) {
            operationFailed = true
            false
        } finally {
            isUpdating = false
        }
    }

    fun dismissError() {
        operationFailed = false
    }

    fun setNewsResourceViewed(newsResourceId: String, viewed: Boolean) {
        mutate { userDataRepository.setNewsResourceViewed(newsResourceId, viewed) }
    }

    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            mutationMutex.withLock {
                isUpdating = true
                try {
                    action()
                } catch (_: IOException) {
                    operationFailed = true
                } finally {
                    isUpdating = false
                }
            }
        }
    }
}
