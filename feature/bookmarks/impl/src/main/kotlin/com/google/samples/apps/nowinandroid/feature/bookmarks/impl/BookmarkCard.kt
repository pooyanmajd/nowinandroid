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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.samples.apps.nowinandroid.core.designsystem.component.NiaTextButton
import com.google.samples.apps.nowinandroid.core.model.data.UserNewsResource
import com.google.samples.apps.nowinandroid.core.ui.BookmarkButton
import com.google.samples.apps.nowinandroid.core.ui.NewsResourceHeaderImage
import com.google.samples.apps.nowinandroid.core.ui.NewsResourceMetaData
import com.google.samples.apps.nowinandroid.core.ui.NewsResourceShortDescription
import com.google.samples.apps.nowinandroid.core.ui.NewsResourceTitle
import com.google.samples.apps.nowinandroid.core.ui.NewsResourceTopics
import com.google.samples.apps.nowinandroid.core.ui.NotificationDot

/** Saved-specific interactions, using the same content components as the shared news card. */
@Composable
internal fun BookmarkCard(
    resource: UserNewsResource,
    note: String,
    selected: Boolean?,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onEditNote: () -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .combinedClickable(
                enabled = enabled,
                onClickLabel = stringResource(
                    if (selected == null) {
                        R.string.feature_bookmarks_impl_open
                    } else {
                        R.string.feature_bookmarks_impl_toggle_selection
                    },
                ),
                onLongClickLabel = stringResource(R.string.feature_bookmarks_impl_select),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { if (selected != null) this.selected = selected }
            .testTag("newsResourceCard:${resource.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (selected == true) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        if (!resource.headerImageUrl.isNullOrEmpty()) {
            NewsResourceHeaderImage(resource.headerImageUrl)
        }
        Column(Modifier.padding(16.dp)) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                NewsResourceTitle(resource.title, Modifier.weight(1f))
                if (selected == null) {
                    BookmarkButton(resource.isSaved, { if (enabled) onRemove() })
                } else {
                    Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!resource.hasBeenViewed) {
                    NotificationDot(MaterialTheme.colorScheme.tertiary, Modifier.size(8.dp))
                    Spacer(Modifier.size(6.dp))
                }
                NewsResourceMetaData(resource.publishDate, resource.type)
            }
            Spacer(Modifier.height(14.dp))
            NewsResourceShortDescription(resource.content)
            Spacer(Modifier.height(12.dp))
            if (selected == null) {
                NewsResourceTopics(resource.followableTopics, onTopicClick)
            }
            if (note.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag("bookmarkNote:${resource.id}"),
                )
            }
            if (selected == null) {
                NiaTextButton(onClick = onEditNote, enabled = enabled) {
                    Text(
                        stringResource(
                            if (note.isBlank()) {
                                R.string.feature_bookmarks_impl_add_note
                            } else {
                                R.string.feature_bookmarks_impl_edit_note
                            },
                        ),
                    )
                }
            }
        }
    }
}
