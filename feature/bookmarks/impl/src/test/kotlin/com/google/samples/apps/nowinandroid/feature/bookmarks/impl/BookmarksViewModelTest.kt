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

import com.google.samples.apps.nowinandroid.core.data.repository.CompositeUserNewsResourceRepository
import com.google.samples.apps.nowinandroid.core.data.repository.UserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.data.newsResourcesTestData
import com.google.samples.apps.nowinandroid.core.testing.repository.TestNewsRepository
import com.google.samples.apps.nowinandroid.core.testing.repository.TestUserDataRepository
import com.google.samples.apps.nowinandroid.core.testing.util.MainDispatcherRule
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Loading
import com.google.samples.apps.nowinandroid.core.ui.NewsFeedUiState.Success
import com.google.samples.apps.nowinandroid.feature.bookmarks.impl.BookmarksViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * To learn more about how this test handles Flows created with stateIn, see
 * https://developer.android.com/kotlin/flow/test#statein
 */
class BookmarksViewModelTest {
    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userDataRepository = TestUserDataRepository()
    private val newsRepository = TestNewsRepository()
    private val userNewsResourceRepository = CompositeUserNewsResourceRepository(
        newsRepository = newsRepository,
        userDataRepository = userDataRepository,
    )
    private lateinit var viewModel: BookmarksViewModel

    @Before
    fun setup() {
        viewModel = BookmarksViewModel(
            userDataRepository = userDataRepository,
            userNewsResourceRepository = userNewsResourceRepository,
        )
    }

    @Test
    fun stateIsInitiallyLoading() = runTest {
        assertEquals(Loading, viewModel.feedUiState.value)
    }

    @Test
    fun oneBookmark_showsInFeed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        val item = viewModel.feedUiState.value
        assertIs<Success>(item)
        assertEquals(item.feed.size, 1)
    }

    @Test
    fun oneBookmark_whenRemoving_removesFromFeed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }
        // Set the news resources to be used by this test
        newsRepository.sendNewsResources(newsResourcesTestData)
        // Start with the resource saved
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        // Use viewModel to remove saved resource
        viewModel.removeFromSavedResources(newsResourcesTestData[0].id)
        // Verify list of saved resources is now empty
        val item = viewModel.feedUiState.value
        assertIs<Success>(item)
        assertEquals(item.feed.size, 0)
        assertTrue(viewModel.shouldDisplayUndoBookmark)
    }

    @Test
    fun feedUiState_resourceIsViewed_setResourcesViewed() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        // Given
        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        val itemBeforeViewed = viewModel.feedUiState.value
        assertIs<Success>(itemBeforeViewed)
        assertFalse(itemBeforeViewed.feed.first().hasBeenViewed)

        // When
        viewModel.setNewsResourceViewed(newsResourcesTestData[0].id, true)

        // Then
        val item = viewModel.feedUiState.value
        assertIs<Success>(item)
        assertTrue(item.feed.first().hasBeenViewed)
    }

    @Test
    fun feedUiState_undoneBookmarkRemoval_bookmarkIsRestored() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher()) { viewModel.feedUiState.collect() }

        // Given
        newsRepository.sendNewsResources(newsResourcesTestData)
        userDataRepository.setNewsResourceBookmarked(newsResourcesTestData[0].id, true)
        viewModel.removeFromSavedResources(newsResourcesTestData[0].id)
        assertTrue(viewModel.shouldDisplayUndoBookmark)
        val itemBeforeUndo = viewModel.feedUiState.value
        assertIs<Success>(itemBeforeUndo)
        assertEquals(0, itemBeforeUndo.feed.size)

        // When
        viewModel.undoBookmarkRemoval()

        // Then
        assertFalse(viewModel.shouldDisplayUndoBookmark)
        val item = viewModel.feedUiState.value
        assertIs<Success>(item)
        assertEquals(1, item.feed.size)
    }

    @Test
    fun bulkUndo_restoresAllBookmarksAndTheirNotes() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.feedUiState.collect() }
        newsRepository.sendNewsResources(newsResourcesTestData)
        val ids = newsResourcesTestData.take(2).map { it.id }.toSet()
        userDataRepository.setNewsResourcesBookmarked(ids, true)
        ids.forEach { viewModel.saveNote(it, "Note for $it") }
        ids.forEach(viewModel::toggleSelection)
        assertEquals(ids, viewModel.selectedIds)

        viewModel.removeSelected()
        assertTrue(userDataRepository.userData.first().bookmarkedNewsResources.isEmpty())
        assertEquals(ids, viewModel.pendingRemoval?.ids)
        assertFalse(viewModel.selectionMode)
        assertEquals(2, userDataRepository.userData.first().bookmarkNotes.size)

        viewModel.undoBookmarkRemoval()
        assertEquals(ids, userDataRepository.userData.first().bookmarkedNewsResources)
        assertEquals(ids.associateWith { "Note for $it" }, userDataRepository.userData.first().bookmarkNotes)
        assertFalse(viewModel.shouldDisplayUndoBookmark)
    }

    @Test
    fun dismissedUndo_deletesOnlyUnbookmarkedNotes() = runTest {
        val ids = newsResourcesTestData.take(2).map { it.id }
        userDataRepository.setNewsResourcesBookmarked(ids.toSet(), true)
        ids.forEach { viewModel.saveNote(it, "Keep $it") }
        viewModel.removeFromSavedResources(ids[0])
        viewModel.clearUndoState()
        assertEquals(mapOf(ids[1] to "Keep ${ids[1]}"), userDataRepository.userData.first().bookmarkNotes)
    }

    @Test
    fun nextRemoval_finalizesPreviousBatchAndIgnoresOldSnackbarCallbacks() = runTest {
        val ids = newsResourcesTestData.take(2).map { it.id }
        userDataRepository.setNewsResourcesBookmarked(ids.toSet(), true)
        ids.forEach { viewModel.saveNote(it, "Note") }
        viewModel.removeFromSavedResources(ids[0])
        val oldToken = viewModel.pendingRemoval!!.token
        viewModel.removeFromSavedResources(ids[1])
        assertEquals(mapOf(ids[1] to "Note"), userDataRepository.userData.first().bookmarkNotes)

        viewModel.clearUndoState(oldToken)
        viewModel.undoBookmarkRemoval(oldToken)
        assertEquals(setOf(ids[1]), viewModel.pendingRemoval?.ids)
        viewModel.undoBookmarkRemoval()
        assertEquals(setOf(ids[1]), userDataRepository.userData.first().bookmarkedNewsResources)
    }

    @Test
    fun rebookmarkedBeforeDismissal_preservesNote() = runTest {
        userDataRepository.setNewsResourceBookmarked("one", true)
        viewModel.saveNote("one", "Keep me")
        viewModel.removeFromSavedResources("one")
        userDataRepository.setNewsResourceBookmarked("one", true)
        viewModel.clearUndoState()
        assertEquals("Keep me", userDataRepository.userData.first().bookmarkNotes["one"])
    }

    @Test
    fun recreatedViewModel_cleansAbandonedUndoAndKeepsSavedNotes() = runTest {
        userDataRepository.setNewsResourcesBookmarked(setOf("one", "two"), true)
        viewModel.saveNote("one", "Expired")
        viewModel.saveNote("two", "Still saved")
        viewModel.removeFromSavedResources("one")
        BookmarksViewModel(userDataRepository, userNewsResourceRepository)
        assertEquals(mapOf("two" to "Still saved"), userDataRepository.userData.first().bookmarkNotes)
    }

    @Test
    fun note_canBeEditedDeletedAndRejectsOversizedInput() = runTest {
        userDataRepository.setNewsResourceBookmarked("one", true)
        assertTrue(viewModel.saveNote("one", "a".repeat(500)))
        assertFalse(viewModel.saveNote("one", "a".repeat(501)))
        assertEquals(500, userDataRepository.userData.first().bookmarkNotes["one"]?.length)
        assertTrue(viewModel.saveNote("one", "Revised"))
        assertEquals("Revised", userDataRepository.userData.first().bookmarkNotes["one"])
        assertTrue(viewModel.saveNote("one", ""))
        assertTrue(userDataRepository.userData.first().bookmarkNotes.isEmpty())
    }

    @Test
    fun selection_toggleCancelAndExternalRemovalStayConsistent() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.feedUiState.collect() }
        newsRepository.sendNewsResources(newsResourcesTestData)
        val ids = newsResourcesTestData.take(2).map { it.id }
        userDataRepository.setNewsResourcesBookmarked(ids.toSet(), true)
        viewModel.selectAll()
        assertEquals(ids.toSet(), viewModel.selectedIds)
        viewModel.toggleSelection(ids[0])
        assertEquals(setOf(ids[1]), viewModel.selectedIds)
        userDataRepository.setNewsResourceBookmarked(ids[1], false)
        assertTrue(viewModel.selectedIds.isEmpty())
        viewModel.cancelSelection()
        assertFalse(viewModel.selectionMode)
        viewModel.toggleSelection("missing")
        assertTrue(viewModel.selectedIds.isEmpty())
    }

    @Test
    fun failedNoteWrite_isReportedWithoutChangingStoredNote() = runTest {
        userDataRepository.setNewsResourceBookmarked("one", true)
        userDataRepository.setBookmarkNote("one", "Original")
        val failingRepository = object : UserDataRepository by userDataRepository {
            override suspend fun setBookmarkNote(newsResourceId: String, note: String) {
                throw IOException("Disk full")
            }
        }
        val subject = BookmarksViewModel(failingRepository, userNewsResourceRepository)
        assertFalse(subject.saveNote("one", "Replacement"))
        assertTrue(subject.operationFailed)
        assertFalse(subject.isUpdating)
        assertEquals("Original", userDataRepository.userData.first().bookmarkNotes["one"])
    }
}
