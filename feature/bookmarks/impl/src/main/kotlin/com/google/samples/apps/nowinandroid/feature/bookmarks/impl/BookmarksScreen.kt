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

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.samples.apps.nowinandroid.core.analytics.LocalAnalyticsHelper
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaLoadingWheel
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTextButton
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.DraggableScrollbar
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.rememberDraggableScroller
import com.google.samples.apps.nowinandroid.core.designsystem.component.scrollbar.scrollbarState
import com.google.samples.apps.nowinandroid.core.designsystem.theme.LocalTintTheme
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Success
import com.google.samples.apps.nowinandroid.core.ui.TrackScreenViewEvent
import com.google.samples.apps.nowinandroid.core.ui.TrackScrollJank
import com.google.samples.apps.nowinandroid.core.ui.UserNewsResourcePreviewParameterProvider
import com.google.samples.apps.nowinandroid.core.ui.launchCustomChromeTab
import com.google.samples.apps.nowinandroid.core.ui.logNewsResourceOpened
import com.google.samples.apps.nowinandroid.feature.bookmarks.api.R as ApiR

@Composable
internal fun BookmarksScreen(
    onTopicClick: (String) -> Unit,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    modifier: Modifier = Modifier,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val feedState by viewModel.feedUiState.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val removal = viewModel.pendingRemoval
    BookmarksScreen(
        feedState = feedState,
        onShowSnackbar = onShowSnackbar,
        removeFromBookmarks = viewModel::removeFromSavedResources,
        onNewsResourceViewed = { viewModel.setNewsResourceViewed(it, true) },
        onTopicClick = onTopicClick,
        modifier = modifier,
        shouldDisplayUndoBookmark = viewModel.shouldDisplayUndoBookmark,
        undoBookmarkRemoval = { viewModel.undoBookmarkRemoval(removal?.token) },
        clearUndoState = { viewModel.clearUndoState(removal?.token) },
        removalToken = removal?.token,
        removalCount = removal?.ids?.size ?: 0,
        notes = notes,
        selectedIds = viewModel.selectedIds,
        selectionMode = viewModel.selectionMode,
        toggleSelection = viewModel::toggleSelection,
        cancelSelection = viewModel::cancelSelection,
        selectAll = viewModel::selectAll,
        removeSelected = viewModel::removeSelected,
        saveNote = viewModel::saveNote,
        isUpdating = viewModel.isUpdating,
        operationFailed = viewModel.operationFailed,
        dismissError = viewModel::dismissError,
    )
}

/**
 * Displays the user's bookmarked articles. Includes support for loading and empty states.
 */
@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun BookmarksScreen(
    feedState: NewsFeedUiState,
    onShowSnackbar: suspend (String, String?) -> Boolean,
    removeFromBookmarks: (String) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    shouldDisplayUndoBookmark: Boolean = false,
    undoBookmarkRemoval: () -> Unit = {},
    clearUndoState: () -> Unit = {},
    removalToken: Long? = null,
    removalCount: Int = 1,
    notes: Map<String, String> = emptyMap(),
    selectedIds: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    toggleSelection: (String) -> Unit = {},
    cancelSelection: () -> Unit = {},
    selectAll: () -> Unit = {},
    removeSelected: () -> Unit = {},
    saveNote: suspend (String, String) -> Boolean = { _, _ -> true },
    isUpdating: Boolean = false,
    operationFailed: Boolean = false,
    dismissError: () -> Unit = {},
) {
    val bookmarkRemovedMessage = pluralStringResource(
        R.plurals.feature_bookmarks_impl_removed,
        removalCount,
        removalCount,
    )
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectionMode, onBack = cancelSelection)
    val undoText = stringResource(id = ApiR.string.feature_bookmarks_api_undo)

    LaunchedEffect(shouldDisplayUndoBookmark, removalToken) {
        if (shouldDisplayUndoBookmark) {
            val snackBarResult = onShowSnackbar(bookmarkRemovedMessage, undoText)
            if (snackBarResult) {
                undoBookmarkRemoval()
            } else {
                clearUndoState()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        clearUndoState()
    }

    Column(modifier.fillMaxSize()) {
        if (selectionMode) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(
                            R.plurals.feature_bookmarks_impl_selected,
                            selectedIds.size,
                            selectedIds.size,
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    NiaTextButton(onClick = removeSelected, enabled = selectedIds.isNotEmpty() && !isUpdating) {
                        Text(stringResource(R.string.feature_bookmarks_impl_remove))
                    }
                    NiaTextButton(onClick = cancelSelection) {
                        Text(stringResource(R.string.feature_bookmarks_impl_cancel))
                    }
                }
                NiaTextButton(onClick = selectAll, enabled = !isUpdating) {
                    Text(stringResource(R.string.feature_bookmarks_impl_select_all))
                }
            }
        }
        if (operationFailed) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.feature_bookmarks_impl_write_error),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.error,
                )
                NiaTextButton(onClick = dismissError) {
                    Text(stringResource(R.string.feature_bookmarks_impl_dismiss))
                }
            }
        }
        when (feedState) {
            Loading -> LoadingState(Modifier.weight(1f))
            is Success -> if (feedState.feed.isNotEmpty()) {
                BookmarksGrid(
                    feedState = feedState,
                    removeFromBookmarks = removeFromBookmarks,
                    onNewsResourceViewed = onNewsResourceViewed,
                    onTopicClick = onTopicClick,
                    modifier = Modifier.weight(1f),
                    notes = notes,
                    selectedIds = selectedIds,
                    selectionMode = selectionMode,
                    toggleSelection = toggleSelection,
                    editNote = { editingId = it },
                    isUpdating = isUpdating,
                )
            } else {
                EmptyState(Modifier.weight(1f))
            }
        }
    }
    editingId?.let { id ->
        val resource = (feedState as? Success)?.feed?.find { it.id == id }
        if (resource != null) {
            BookmarkNoteDialog(
                resourceId = id,
                title = resource.title,
                initialNote = notes[id].orEmpty(),
                onDismiss = { editingId = null },
                onSave = { saveNote(id, it) },
            )
        } else {
            LaunchedEffect(id) { editingId = null }
        }
    }

    TrackScreenViewEvent(screenName = "Saved")
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    NiaLoadingWheel(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentSize()
            .testTag("bookmarks:loading"),
        contentDesc = stringResource(id = ApiR.string.feature_bookmarks_api_loading),
    )
}

@Composable
private fun BookmarksGrid(
    feedState: NewsFeedUiState,
    removeFromBookmarks: (String) -> Unit,
    onNewsResourceViewed: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    notes: Map<String, String> = emptyMap(),
    selectedIds: Set<String> = emptySet(),
    selectionMode: Boolean = false,
    toggleSelection: (String) -> Unit = {},
    editNote: (String) -> Unit = {},
    isUpdating: Boolean = false,
) {
    val scrollableState = rememberLazyStaggeredGridState()
    TrackScrollJank(scrollableState = scrollableState, stateName = "bookmarks:grid")
    Box(
        modifier = modifier
            .fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(300.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalItemSpacing = 24.dp,
            state = scrollableState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("bookmarks:feed"),
        ) {
            val resources = (feedState as? Success)?.feed.orEmpty()
            items(resources, key = { it.id }, contentType = { "bookmark" }) { resource ->
                val context = LocalContext.current
                val analytics = LocalAnalyticsHelper.current
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                BookmarkCard(
                    resource = resource,
                    note = notes[resource.id].orEmpty(),
                    selected = if (selectionMode) resource.id in selectedIds else null,
                    enabled = !isUpdating,
                    onClick = {
                        if (selectionMode) {
                            toggleSelection(resource.id)
                        } else {
                            analytics.logNewsResourceOpened(resource.id)
                            launchCustomChromeTab(context, Uri.parse(resource.url), backgroundColor)
                            onNewsResourceViewed(resource.id)
                        }
                    },
                    onLongClick = { toggleSelection(resource.id) },
                    onRemove = { removeFromBookmarks(resource.id) },
                    onEditNote = { editNote(resource.id) },
                    onTopicClick = onTopicClick,
                    modifier = Modifier.padding(horizontal = 8.dp).animateItem(),
                )
            }
            item(span = StaggeredGridItemSpan.FullLine) {
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
        val itemsAvailable = when (feedState) {
            Loading -> 1
            is Success -> feedState.feed.size
        }
        val scrollbarState = scrollableState.scrollbarState(
            itemsAvailable = itemsAvailable,
        )
        scrollableState.DraggableScrollbar(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 2.dp)
                .align(Alignment.CenterEnd),
            state = scrollbarState,
            orientation = Orientation.Vertical,
            onThumbMoved = scrollableState.rememberDraggableScroller(
                itemsAvailable = itemsAvailable,
            ),
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
            .testTag("bookmarks:empty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconTint = LocalTintTheme.current.iconTint
        Image(
            modifier = Modifier.fillMaxWidth(),
            painter = painterResource(id = ApiR.drawable.feature_bookmarks_api_mg_empty_bookmarks),
            colorFilter = if (iconTint != Color.Unspecified) ColorFilter.tint(iconTint) else null,
            contentDescription = null,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = stringResource(id = ApiR.string.feature_bookmarks_api_empty_error),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(id = ApiR.string.feature_bookmarks_api_empty_description),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview
@Composable
private fun LoadingStatePreview() {
    NiaTheme {
        LoadingState()
    }
}

@Preview
@Composable
private fun BookmarksGridPreview(
    @PreviewParameter(UserNewsResourcePreviewParameterProvider::class)
    userNewsResources: List<UserNewsResource>,
) {
    NiaTheme {
        BookmarksGrid(
            feedState = Success(userNewsResources),
            removeFromBookmarks = {},
            onNewsResourceViewed = {},
            onTopicClick = {},
        )
    }
}

@Preview
@Composable
private fun EmptyStatePreview() {
    NiaTheme {
        EmptyState()
    }
}
