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

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.google.samples.apps.nowinandroid.core.testing.data.userNewsResourcesTestData
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.google.samples.apps.nowinandroid.feature.bookmarks.api.R as ApiR

/**
 * UI tests for [BookmarksScreen] composable.
 */
class BookmarksScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loading_showsLoadingSpinner() {
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Loading,
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = {},
                onTopicClick = {},
                onNewsResourceViewed = {},
            )
        }

        composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.resources.getString(ApiR.string.feature_bookmarks_api_loading),
            )
            .assertExists()
    }

    @Test
    fun feed_whenHasBookmarks_showsBookmarks() {
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(
                    userNewsResourcesTestData.take(2),
                ),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = {},
                onTopicClick = {},
                onNewsResourceViewed = {},
            )
        }

        composeTestRule
            .onNodeWithText(
                userNewsResourcesTestData[0].title,
                substring = true,
            )
            .assertExists()
            .assertHasClickAction()

        composeTestRule.onNode(hasScrollToNodeAction())
            .performScrollToNode(
                hasText(
                    userNewsResourcesTestData[1].title,
                    substring = true,
                ),
            )

        composeTestRule
            .onNodeWithText(
                userNewsResourcesTestData[1].title,
                substring = true,
            )
            .assertExists()
            .assertHasClickAction()
    }

    @Test
    fun feed_whenRemovingBookmark_removesBookmark() {
        var removeFromBookmarksCalled = false

        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(
                    userNewsResourcesTestData.take(2),
                ),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = { newsResourceId ->
                    assertEquals(userNewsResourcesTestData[0].id, newsResourceId)
                    removeFromBookmarksCalled = true
                },
                onTopicClick = {},
                onNewsResourceViewed = {},
            )
        }

        composeTestRule
            .onAllNodesWithContentDescription(
                composeTestRule.activity.getString(
                    com.google.samples.apps.nowinandroid.core.ui.R.string.core_ui_unbookmark,
                ),
            ).filter(
                hasAnyAncestor(
                    hasText(
                        userNewsResourcesTestData[0].title,
                        substring = true,
                    ),
                ),
            )
            .assertCountEquals(1)
            .onFirst()
            .performClick()

        assertTrue(removeFromBookmarksCalled)
    }

    @Test
    fun feed_whenHasNoBookmarks_showsEmptyState() {
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(emptyList()),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = {},
                onTopicClick = {},
                onNewsResourceViewed = {},
            )
        }

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(ApiR.string.feature_bookmarks_api_empty_error),
            )
            .assertExists()

        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(ApiR.string.feature_bookmarks_api_empty_description),
            )
            .assertExists()
    }

    @Test
    fun feed_whenLifecycleStops_undoBookmarkedStateIsCleared() = runTest {
        var undoStateCleared = false
        val testLifecycleOwner = TestLifecycleOwner(initialState = Lifecycle.State.STARTED)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                BookmarksScreen(
                    feedState = NewsFeedUiState.Success(emptyList()),
                    onShowSnackbar = { _, _ -> false },
                    removeFromBookmarks = {},
                    onTopicClick = {},
                    onNewsResourceViewed = {},
                    clearUndoState = {
                        undoStateCleared = true
                    },
                )
            }
        }

        assertEquals(false, undoStateCleared)
        testLifecycleOwner.handleLifecycleEvent(event = Lifecycle.Event.ON_STOP)
        assertEquals(true, undoStateCleared)
    }

    @Test
    fun longPress_entersSelectionWithoutOpeningTheArticle() {
        val item = userNewsResourcesTestData.first()
        var selectedId: String? = null
        var opened = false
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(listOf(item)),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = {},
                onTopicClick = {},
                onNewsResourceViewed = { opened = true },
                toggleSelection = { selectedId = it },
            )
        }
        composeTestRule.onNodeWithTag("newsResourceCard:${item.id}")
            .performTouchInput { longClick() }
        assertEquals(item.id, selectedId)
        assertFalse(opened)
    }

    @Test
    fun selectionMode_tapTogglesAndBackCancels() {
        val item = userNewsResourcesTestData.first()
        var toggled: String? = null
        var cancelled = false
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(listOf(item)),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = { error("Selection must not unbookmark an individual item") },
                onTopicClick = {},
                onNewsResourceViewed = { error("Selection must not open an article") },
                selectionMode = true,
                selectedIds = setOf(item.id),
                toggleSelection = { toggled = it },
                cancelSelection = { cancelled = true },
            )
        }
        composeTestRule.onNodeWithText("1 selected").assertExists()
        composeTestRule.onNodeWithTag("newsResourceCard:${item.id}").performClick()
        assertEquals(item.id, toggled)
        composeTestRule.activity.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertTrue(cancelled)
    }

    @Test
    fun notePreviewAndEditor_areAvailableOnTheCard() {
        val item = userNewsResourcesTestData.first()
        composeTestRule.setContent {
            BookmarksScreen(
                feedState = NewsFeedUiState.Success(listOf(item)),
                onShowSnackbar = { _, _ -> false },
                removeFromBookmarks = {},
                onTopicClick = {},
                onNewsResourceViewed = {},
                notes = mapOf(item.id to "My private reminder"),
            )
        }
        composeTestRule.onNodeWithText("My private reminder").assertExists()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_bookmarks_impl_edit_note))
            .performClick()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_bookmarks_impl_private_note))
            .assertExists()
    }

    @Test
    fun noteEditor_limitsInputAndSavesOnlyOnConfirmation() {
        var saved: String? = null
        var dismissed = false
        composeTestRule.setContent {
            BookmarkNoteDialog(
                resourceId = "one",
                title = "Article",
                initialNote = "",
                onDismiss = { dismissed = true },
                onSave = {
                    saved = it
                    true
                },
            )
        }
        composeTestRule.onNode(hasSetTextAction()).performTextInput("a".repeat(510))
        assertEquals(null, saved)
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_bookmarks_impl_save))
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("a".repeat(500), saved)
        assertTrue(dismissed)
    }

    @Test
    fun noteEditor_cancelLeavesExistingNoteUntouched() {
        var saved = false
        var dismissed = false
        composeTestRule.setContent {
            BookmarkNoteDialog("one", "Article", "Original", { dismissed = true }, {
                saved = true
                true
            })
        }
        composeTestRule.onNode(hasSetTextAction()).performTextInput(" extra")
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_bookmarks_impl_cancel))
            .performClick()
        assertTrue(dismissed)
        assertFalse(saved)
    }

    @Test
    fun noteEditor_deleteSavesAnEmptyNote() {
        var saved: String? = null
        composeTestRule.setContent {
            BookmarkNoteDialog("one", "Article", "Original", {}, {
                saved = it
                true
            })
        }
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.feature_bookmarks_impl_delete_note))
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals("", saved)
    }
}
