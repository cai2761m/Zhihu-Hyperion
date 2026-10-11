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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.data.DataHolder
import com.github.zly2006.zhihu.data.Feed
import com.github.zly2006.zhihu.data.FeedDisplayItem
import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.executeZhihuAuthenticatedRequest
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.util.ZhihuApiErrorException
import com.github.zly2006.zhihu.util.jsonObject
import com.github.zly2006.zhihu.util.raiseForStatus
import com.github.zly2006.zhihu.util.signZhihuFetchRequest
import com.github.zly2006.zhihu.util.zhihuApiErrorOrNull
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.reflect.typeOf

class ColumnViewModel(
    val columnId: String,
) : PaginationViewModel<Feed.ArticleTarget>(typeOf<Feed.ArticleTarget>()) {
    var detail by mutableStateOf<DataHolder.Column?>(null)
        private set
    var detailFailure by mutableStateOf<ContentLoadFailure?>(null)
        private set
    var isDetailLoading by mutableStateOf(false)
        private set
    var isFollowLoading by mutableStateOf(false)
        private set
    var pinnedArticles by mutableStateOf<List<Feed.ArticleTarget>>(emptyList())
        private set
    var pinnedFailure by mutableStateOf<ContentLoadFailure?>(null)
        private set
    private var detailJob: Job? = null

    override val initialUrl = "https://www.zhihu.com/api/v4/columns/$columnId/articles?limit=10&offset=0"
    override val include = ""

    val displayItems by derivedStateOf {
        val pinnedIds = pinnedArticles.mapTo(mutableSetOf()) { it.id }
        (pinnedArticles + allData).distinctBy { it.id }.map { article ->
            FeedDisplayItem(
                title = article.title,
                summary = article.excerpt,
                details = if (article.id in pinnedIds) "置顶 · ${article.detailsText}" else article.detailsText,
                feed = null,
                navDestinationJson = article.navDestination?.toFeedDisplayItemNavDestinationJson(),
                avatarSrc = article.author.avatarUrl,
                authorName = article.author.name,
                authorBadgeV2 = article.author.badgeV2,
            )
        }
    }

    suspend fun loadDetail(environment: ZhihuApiEnvironment) {
        if (isFollowLoading) return
        isDetailLoading = true
        detailFailure = null
        try {
            val response = environment.fetchJson(
                "https://www.zhihu.com/api/v4/columns/$columnId",
                "intro,description,articles_count,followers,author,is_following",
            ) ?: error("专栏信息为空")
            response.zhihuApiErrorOrNull()?.let { throw it }
            detail = ZhihuJson.decodeJson<DataHolder.Column>(response)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            detailFailure = ContentLoadFailure(error.message ?: "专栏信息加载失败", (error as? ZhihuApiErrorException)?.needLogin == true)
        } finally {
            isDetailLoading = false
        }
    }

    suspend fun loadPinnedArticles(environment: ZhihuApiEnvironment) {
        pinnedFailure = null
        try {
            val response = environment.fetchJson("https://www.zhihu.com/api/v4/columns/$columnId/pinned-items/v2", "")
                ?: error("置顶文章信息为空")
            response.zhihuApiErrorOrNull()?.let { throw it }
            val data = response["data"] as? JsonArray ?: error("置顶文章列表缺失")
            pinnedArticles = decodePage(environment, data)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            pinnedFailure = ContentLoadFailure(error.message ?: "置顶文章加载失败", (error as? ZhihuApiErrorException)?.needLogin == true)
        }
    }

    suspend fun toggleFollow(environment: ZhihuApiEnvironment) {
        val current = detail ?: return
        if (isFollowLoading || isDetailLoading) return
        isFollowLoading = true
        val following = !current.isFollowing
        try {
            val response = environment.withAuthenticatedClient { client, cookies ->
                executeZhihuAuthenticatedRequest(client, "https://www.zhihu.com/api/v4/columns/$columnId/${if (following) "follow" else "unfollow"}") {
                    method = HttpMethod.Put
                    signZhihuFetchRequest(cookies)
                }
            }
            val result = response.jsonObject()
            result.zhihuApiErrorOrNull()?.let { throw it }
            response.raiseForStatus()
            check(result["success"]?.jsonPrimitive?.booleanOrNull == true) { "专栏关注操作未成功" }
            val followers = (current.followers.coerceAtLeast(current.followerCount) + if (following) 1 else -1).coerceAtLeast(0)
            detail = current.copy(isFollowing = following, followers = followers, followerCount = followers)
        } finally {
            isFollowLoading = false
        }
    }

    override fun refresh(environment: ZhihuApiEnvironment) {
        if (isFollowLoading) return
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            coroutineScope {
                launch { loadDetail(environment) }
                launch { loadPinnedArticles(environment) }
            }
        }
        super.refresh(environment)
    }

    override suspend fun processResponse(environment: ZhihuApiEnvironment, data: List<Feed.ArticleTarget>, rawData: JsonArray) {
        val seen = allData.mapTo(mutableSetOf()) { it.id }
        super.processResponse(environment, data.filter { seen.add(it.id) }, rawData)
    }
}
