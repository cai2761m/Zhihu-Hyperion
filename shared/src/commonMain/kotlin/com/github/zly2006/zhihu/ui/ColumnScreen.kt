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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.data.Person
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.markdown.RenderMarkdown
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.RegisterReadingQueueSource
import com.github.zly2006.zhihu.ui.components.AppPullToRefreshBox
import com.github.zly2006.zhihu.ui.components.EmptyState
import com.github.zly2006.zhihu.ui.components.EmptyStateAction
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.ui.components.PaginatedList
import com.github.zly2006.zhihu.ui.components.ProgressIndicatorFooter
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.viewmodel.ColumnViewModel
import com.github.zly2006.zhihu.viewmodel.rememberZhihuApiEnvironment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.github.zly2006.zhihu.navigation.Column as ColumnDestination
import com.github.zly2006.zhihu.navigation.Person as PersonDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnScreen(destination: ColumnDestination) {
    val navigator = LocalNavigator.current
    val environment = rememberZhihuApiEnvironment(allowGuestAccess = false)
    val screenViewModel: ColumnViewModel = viewModel(key = "column_${destination.id}") { ColumnViewModel(destination.id) }
    val pinnedArticleKeys = remember(screenViewModel.pinnedArticles) {
        screenViewModel.pinnedArticles.mapNotNullTo(mutableSetOf()) { it.navDestination?.toFeedDisplayItemNavDestinationJson() }
    }
    val listState = rememberLazyListState()
    val pageTurnTarget = rememberPageTurnTarget(listState = listState, enabled = true)
    val scope = rememberCoroutineScope()
    val userMessages = rememberUserMessageSink()
    val readingQueueSourceId = "column:${destination.id}:articles"
    RegisterReadingQueueSource(readingQueueSourceId, screenViewModel.displayItems)

    LaunchedEffect(screenViewModel) {
        if (screenViewModel.detail == null && screenViewModel.allData.isEmpty() && !screenViewModel.isLoading && screenViewModel.errorMessage == null && screenViewModel.detailFailure == null && !screenViewModel.isEnd) {
            screenViewModel.refresh(environment)
        }
    }

    Scaffold(
        modifier = Modifier.testTag("column_screen"),
        topBar = {
            TopAppBar(
                title = { Text("专栏") },
                navigationIcon = {
                    IconButton(onClick = navigator.onNavigateBack) { Icon(AppIcons.ArrowBack, contentDescription = "返回") }
                },
            )
        },
    ) { padding ->
        AppPullToRefreshBox(
            isRefreshing = screenViewModel.isLoading && screenViewModel.allData.isEmpty(),
            onRefresh = { screenViewModel.refresh(environment) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            PaginatedList(
                items = screenViewModel.displayItems,
                listState = listState,
                key = { it.stableKey },
                onLoadMore = { screenViewModel.loadMore(environment) },
                isEnd = { screenViewModel.isEnd },
                footer = ProgressIndicatorFooter,
                loadFailed = screenViewModel.errorMessage != null,
                loadFailureDescription = screenViewModel.errorMessage,
                onRetry = { screenViewModel.retry(environment) },
                onLogin = (::requestLoginNavigation).takeIf { screenViewModel.apiError?.needLogin == true },
                emptyContent = { EmptyState(AppIcons.AutoStories, "专栏还没有文章") },
                modifier = Modifier.fillMaxSize().pageTurnViewportWithGuide(pageTurnTarget).testTag("column_articles_list"),
                topContent = {
                    item(key = "column_header") {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp).testTag("column_header"),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            val detail = screenViewModel.detail
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (detail != null && detail.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = detail.imageUrl,
                                        contentDescription = "专栏封面",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).testTag("column_cover_image"),
                                    )
                                }
                                Text(
                                    detail?.title ?: destination.title.ifBlank { "专栏" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (detail != null) {
                                val introduction = detail.intro.ifBlank { detail.description }
                                if (introduction.isNotBlank()) {
                                    RenderMarkdown(html = introduction, selectable = false, enableScroll = false)
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "${detail.articlesCount} 篇文章 · ${detail.followers.coerceAtLeast(detail.followerCount)} 人订阅",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedButton(
                                        enabled = !screenViewModel.isFollowLoading && !screenViewModel.isDetailLoading,
                                        modifier = Modifier.testTag("column_follow_button"),
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    screenViewModel.toggleFollow(environment)
                                                } catch (error: CancellationException) {
                                                    throw error
                                                } catch (error: Exception) {
                                                    userMessages.showShortMessage(error.message ?: "专栏关注操作失败")
                                                }
                                            }
                                        },
                                    ) {
                                        Text(if (detail.isFollowing) "已订阅" else "订阅")
                                    }
                                }
                                val author = remember(detail.author) {
                                    detail.author?.let { runCatching { ZhihuJson.decodeJson<Person>(it) }.getOrNull() }
                                }
                                author?.let { author ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().testTag("column_author").clickable {
                                            navigator.onNavigate(PersonDestination(id = author.id, urlToken = author.urlToken.orEmpty(), name = author.name))
                                        },
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(author.avatarUrl, "专栏作者头像", Modifier.size(40.dp).clip(CircleShape))
                                        Column(Modifier.weight(1f)) {
                                            Text(author.name, style = MaterialTheme.typography.titleMedium)
                                            if (author.headline.isNotBlank()) {
                                                Text(author.headline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            val failure = screenViewModel.detailFailure
                            if (failure != null) {
                                EmptyState(
                                    AppIcons.Error,
                                    "专栏信息加载失败",
                                    description = failure.message,
                                    action = if (failure.needLogin) {
                                        EmptyStateAction("登录", AppIcons.Login, onClick = ::requestLoginNavigation)
                                    } else {
                                        EmptyStateAction("重试", AppIcons.Refresh, "column_detail_retry") {
                                            scope.launch { screenViewModel.loadDetail(environment) }
                                        }
                                    },
                                )
                            } else if (detail == null) {
                                Text("正在加载专栏信息…", style = MaterialTheme.typography.bodyMedium)
                            }
                            screenViewModel.pinnedFailure?.let { failure ->
                                EmptyState(
                                    AppIcons.Error,
                                    "置顶文章加载失败",
                                    description = failure.message,
                                    action = if (failure.needLogin) {
                                        EmptyStateAction("登录", AppIcons.Login, onClick = ::requestLoginNavigation)
                                    } else {
                                        EmptyStateAction("重试", AppIcons.Refresh, "column_pinned_retry") {
                                            scope.launch { screenViewModel.loadPinnedArticles(environment) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
            ) { item ->
                FeedCard(
                    item,
                    readingQueueSourceId = readingQueueSourceId,
                    modifier = Modifier.testTag("column_article_${item.stableKey}"),
                    isPinned = item.navDestinationJson in pinnedArticleKeys,
                )
            }
        }
    }
}
