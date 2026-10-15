/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2026, eltavine <me@eltavine.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.FollowUserUpdates
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.EmptyState
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.feed.FollowViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUserUpdatesScreen(destination: FollowUserUpdates) {
    val navigator = LocalNavigator.current
    val settings = koinInject<SettingsStore>()
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val model: FollowViewModel = viewModel(key = "follow_updates_${destination.id}") {
        FollowViewModel(settings, destination.urlToken.ifBlank { destination.id })
    }
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(listState = listState, enabled = true)
    val readingQueueSourceId = "follow:user:${destination.id}"
    RegisterReadingQueueSource(readingQueueSourceId, model.displayItems)

    LaunchedEffect(model) {
        if (model.displayItems.isEmpty() && !model.isLoading && model.errorMessage == null && !model.isEnd) {
            model.loadMore(environment)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("follow_user_updates_screen"),
        topBar = {
            TopAppBar(
                title = { Text(destination.name.takeIf { it.isNotBlank() }?.let { "${it}的更新" } ?: "用户更新") },
                navigationIcon = {
                    IconButton(onClick = navigator.onNavigateBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        AppPullToRefreshBox(
            isRefreshing = model.isLoading && model.displayItems.isEmpty(),
            onRefresh = { model.refresh(environment) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            PaginatedList(
                items = model.displayItems,
                listState = listState,
                modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget).testTag("follow_user_updates_list"),
                key = { it.stableKey },
                onLoadMore = { model.loadMore(environment) },
                isEnd = { model.isEnd },
                footer = ProgressIndicatorFooter,
                loadFailed = model.errorMessage != null,
                onRetry = { model.retry(environment) },
                loadFailureMessage = model.apiError?.message,
                loadFailureDescription = model.errorMessage.takeIf { model.apiError == null },
                onLogin = (::requestLoginNavigation).takeIf { model.apiError?.needLogin == true },
                emptyContent = { EmptyState(AppIcons.Group, "该用户暂时没有更新") },
                centerStatusWhenEmpty = true,
            ) { item ->
                FeedCard(
                    item = item,
                    readingQueueSourceId = readingQueueSourceId,
                    showSourceLabel = true,
                    modifier = Modifier.testTag("follow_user_update_${item.stableKey}"),
                )
            }
        }
    }
}
