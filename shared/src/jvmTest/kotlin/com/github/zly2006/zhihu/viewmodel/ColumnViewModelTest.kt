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

import com.github.zly2006.zhihu.data.ZhihuJson
import com.github.zly2006.zhihu.data.installZhihuCommonClientConfig
import com.github.zly2006.zhihu.data.navDestination
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.ArticleType
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.ui.PeopleColumnContributionsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ColumnViewModelTest {
    @Test
    fun pinnedArticlesPrecedeOrdinaryArticlesAndAppearOnlyOnceAcrossPages() = runTest {
        val pinned = fixture("pinned-items.json")
        val pinnedData = ZhihuJson.json
            .parseToJsonElement(pinned)
            .jsonObject
            .getValue("data")
            .jsonArray
        val firstPage = ZhihuJson.json.parseToJsonElement(fixture("page0.json")).jsonObject
        val overlap = JsonObject(firstPage + ("data" to JsonArray(firstPage.getValue("data").jsonArray + pinnedData.reversed())))
        withEnvironment(
            handler = { request ->
                respond(
                    when {
                        request.url.encodedPath.endsWith("/pinned-items/v2") -> pinned
                        request.url.encodedPath.endsWith("/articles") -> overlap.toString()
                        else -> fixture("detail.json")
                    },
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { environment, requests ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals(listOf(531315462L, 434074509L, 374403541L), model.displayItems.take(3).map { (it.navDestination as Article).id })
            assertTrue(model.displayItems.take(3).all { it.details.startsWith("置顶 · ") })
            assertEquals(4, model.displayItems.size)
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(4, model.displayItems.size)
            assertEquals(1, requests.count { it.encodedPath.endsWith("/pinned-items/v2") })
        }
    }

    @Test
    fun verifiedColumnResponsesLoadNativeArticleDestinationsAndServerPaging() = runTest {
        val firstPage = fixture("page0.json")
        val secondPage = fixture("page1.json")
        withEnvironment(
            handler = { request ->
                respond(
                    if (request.url.encodedPath.endsWith("/pinned-items/v2")) {
                        fixture("pinned-empty.json")
                    } else if (request.url.encodedPath.endsWith("/articles")) {
                        if (request.url.parameters["offset"] == "0") firstPage else secondPage
                    } else {
                        fixture("detail.json")
                    },
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { environment, requests ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("走向奇点", model.detail?.title)
            assertEquals(21, model.detail?.articlesCount)
            assertEquals(73, model.detail?.followers)
            assertEquals(
                "https://pic1.zhimg.com/v2-61bb5d458e8ec2b2827ca1dfef22dc14_l.jpg?source=d16d100b",
                ZhihuJson.json
                    .encodeToJsonElement(model.detail!!)
                    .jsonObject["imageUrl"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(1, model.allData.size)
            val article = model.displayItems.single().navDestination as Article
            assertEquals(2086872663780282481L, article.id)
            assertEquals(ArticleType.Article, article.type)
            assertEquals("在「万能机器」之后", article.title)

            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(2, model.allData.size)
            val articleRequests = requests.filter { it.encodedPath.endsWith("/articles") }
            assertEquals("10", articleRequests[0].parameters["limit"])
            assertEquals("1", articleRequests[1].parameters["limit"])
            assertEquals("1", articleRequests[1].parameters["offset"])
            assertEquals("https", articleRequests[1].protocol.name)
            assertNull(model.errorMessage)
        }
    }

    @Test
    fun failedNextPagePreservesLoadedArticlesAndRetryResumesTheSamePage() = runTest {
        var secondPageAttempts = 0
        withEnvironment(
            handler = { request ->
                val body = if (request.url.encodedPath.endsWith("/pinned-items/v2")) {
                    fixture("pinned-empty.json")
                } else if (!request.url.encodedPath.endsWith("/articles")) {
                    fixture("detail.json")
                } else if (request.url.parameters["offset"] == "0") {
                    fixture("page0.json")
                } else {
                    if (secondPageAttempts++ == 0) throw IOException("连接中断")
                    fixture("page1.json")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { environment, requests ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            model.loadMore(environment)
            advanceUntilIdle()
            assertNotNull(model.errorMessage)
            assertEquals(1, model.allData.size)
            model.retry(environment)
            advanceUntilIdle()
            assertNull(model.errorMessage)
            assertEquals(2, model.allData.size)
            assertEquals(requests[requests.lastIndex - 1], requests.last())
        }
    }

    @Test
    fun detailFailureKeepsLoadedContentAndSupportsRetry() = runTest {
        var detailAttempts = 0
        withEnvironment(
            handler = { request ->
                val body = if (request.url.encodedPath.endsWith("/pinned-items/v2")) {
                    fixture("pinned-empty.json")
                } else if (request.url.encodedPath.endsWith("/articles")) {
                    fixture("page0.json")
                } else {
                    if (detailAttempts++ == 1) throw IOException("专栏信息连接中断")
                    fixture("detail.json")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { environment, _ ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            model.refresh(environment)
            advanceUntilIdle()
            assertEquals("走向奇点", model.detail?.title)
            assertEquals(1, model.displayItems.size)
            assertNull(model.pinnedFailure)
            assertNotNull(model.detailFailure)
            model.loadDetail(environment)
            assertNull(model.detailFailure)
            assertEquals(1, model.displayItems.size)
        }
    }

    @Test
    fun overlappingPageDoesNotRepeatArticleCards() = runTest {
        val first = ZhihuJson.json.parseToJsonElement(fixture("page0.json")).jsonObject
        val second = ZhihuJson.json.parseToJsonElement(fixture("page1.json")).jsonObject
        // Controlled overlap keeps each captured article unchanged and exercises the production page merge.
        val overlap = JsonObject(second + ("data" to JsonArray(first.getValue("data").jsonArray + second.getValue("data").jsonArray)))
        withEnvironment(
            handler = { request ->
                respond(
                    if (request.url.encodedPath.endsWith("/pinned-items/v2")) {
                        fixture("pinned-empty.json")
                    } else if (!request.url.encodedPath.endsWith("/articles")) {
                        fixture("detail.json")
                    } else if (request.url.parameters["offset"] == "0") {
                        first.toString()
                    } else {
                        overlap.toString()
                    },
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { environment, _ ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(2, model.displayItems.size)
            assertEquals(
                2,
                model.displayItems
                    .map { it.stableKey }
                    .toSet()
                    .size,
            )
        }
    }

    @Test
    fun contributionRequestsProjectCountsInsideTheNestedColumn() = runTest {
        withEnvironment(
            handler = { request ->
                val include = request.url.parameters["include"].orEmpty()
                respond(
                    fixture(if (include.startsWith("data[*].column.")) "contributions.json" else "contributions-missing-counts.json"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { environment, requests ->
            val model = PeopleColumnContributionsViewModel(Person(id = "profile", urlToken = "column-author"))
            model.loadMore(environment)
            advanceUntilIdle()
            assertEquals(21, model.allData.first { it.id == COLUMN_ID }.articlesCount)
            assertEquals(73, model.allData.first { it.id == COLUMN_ID }.followers)
            assertEquals(
                "https://picx.zhimg.com/v2-61bb5d458e8ec2b2827ca1dfef22dc14_720w.jpg?source=d16d100b",
                ZhihuJson.json
                    .encodeToJsonElement(model.allData.first { it.id == COLUMN_ID })
                    .jsonObject["imageUrl"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertEquals(
                1,
                requests
                    .single()
                    .parameters
                    .getAll("include")
                    ?.size,
            )
        }
    }

    @Test
    fun pinnedFailureKeepsLoadedArticlesAndRetryCanClearRemovedPins() = runTest {
        var pinnedAttempts = 0
        withEnvironment(
            handler = { request ->
                val body = when {
                    request.url.encodedPath.endsWith("/pinned-items/v2") -> when (pinnedAttempts++) {
                        0 -> fixture("pinned-items.json")
                        1 -> throw IOException("置顶文章连接中断")
                        else -> fixture("pinned-empty.json")
                    }

                    request.url.encodedPath.endsWith("/articles") -> fixture("page0.json")

                    else -> fixture("detail.json")
                }
                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) { environment, _ ->
            val model = ColumnViewModel(COLUMN_ID)
            model.refresh(environment)
            advanceUntilIdle()
            model.refresh(environment)
            advanceUntilIdle()
            assertNotNull(model.pinnedFailure)
            assertEquals(4, model.displayItems.size)
            model.loadPinnedArticles(environment)
            assertNull(model.pinnedFailure)
            assertTrue(model.pinnedArticles.isEmpty())
            assertEquals(1, model.displayItems.size)
            assertFalse(
                model.displayItems
                    .single()
                    .details
                    .startsWith("置顶"),
            )
        }
    }

    @Test
    fun followAndUnfollowUseVerifiedPutRoutesAndUpdateCountsOnlyAfterSuccess() = runTest {
        val methods = mutableListOf<HttpMethod>()
        withEnvironment(
            handler = { request ->
                methods += request.method
                respond(
                    if (request.method == HttpMethod.Put) """{"success":true}""" else fixture("detail.json"),
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) { environment, requests ->
            val model = ColumnViewModel(COLUMN_ID)
            model.loadDetail(environment)
            model.toggleFollow(environment)
            assertTrue(model.detail!!.isFollowing)
            assertEquals(74, model.detail!!.followers)
            model.toggleFollow(environment)
            assertFalse(model.detail!!.isFollowing)
            assertEquals(73, model.detail!!.followers)
            assertFalse(model.isFollowLoading)
            assertEquals(listOf(HttpMethod.Get, HttpMethod.Put, HttpMethod.Put), methods)
            assertEquals(listOf("/api/v4/columns/$COLUMN_ID/follow", "/api/v4/columns/$COLUMN_ID/unfollow"), requests.drop(1).map { it.encodedPath })
        }
    }

    @Test
    fun failedOrCancelledFollowKeepsRelationshipAndCountsAndAllowsRetry() = runTest {
        var attempts = 0
        withEnvironment(
            handler = { request ->
                if (request.method != HttpMethod.Put) {
                    respond(fixture("detail.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    when (attempts++) {
                        0 -> respond("""{"error":{"message":"关注失败"}}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
                        1 -> throw CancellationException("取消关注请求")
                        else -> respond("""{"success":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                }
            },
        ) { environment, _ ->
            val model = ColumnViewModel(COLUMN_ID)
            model.loadDetail(environment)
            val before = model.detail
            assertFailsWith<Exception> { model.toggleFollow(environment) }
            assertEquals(before, model.detail)
            assertFalse(model.isFollowLoading)
            assertFailsWith<CancellationException> { model.toggleFollow(environment) }
            assertEquals(before, model.detail)
            assertFalse(model.isFollowLoading)
            model.toggleFollow(environment)
            assertTrue(model.detail!!.isFollowing)
        }
    }

    @Test
    fun pendingFollowIgnoresRepeatedClicksAndRefreshUntilTheResponseArrives() = runTest {
        val responseReady = CompletableDeferred<Unit>()
        var followRequests = 0
        withEnvironment(
            handler = { request ->
                if (request.method == HttpMethod.Put) {
                    followRequests++
                    responseReady.await()
                    respond("""{"success":true}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond(fixture("detail.json"), headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
            },
        ) { environment, requests ->
            val model = ColumnViewModel(COLUMN_ID)
            model.loadDetail(environment)
            val pending = launch { model.toggleFollow(environment) }
            advanceUntilIdle()
            assertTrue(model.isFollowLoading)
            assertFalse(model.detail!!.isFollowing)
            assertEquals(73, model.detail!!.followers)
            model.toggleFollow(environment)
            model.refresh(environment)
            model.loadDetail(environment)
            assertEquals(2, requests.size)
            responseReady.complete(Unit)
            pending.join()
            assertEquals(1, followRequests)
            assertTrue(model.detail!!.isFollowing)
            assertFalse(model.isFollowLoading)
        }
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader.getResourceAsStream("column/$name"))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    private suspend fun TestScope.withEnvironment(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        block: suspend (ZhihuApiEnvironment, MutableList<Url>) -> Unit,
    ) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val requests = mutableListOf<Url>()
        val client = HttpClient(MockEngine) {
            engine {
                this.dispatcher = dispatcher
                addHandler { request ->
                    requests += request.url
                    handler(request)
                }
            }
            installZhihuCommonClientConfig(mutableMapOf(), "test")
        }
        val environment = object : ZhihuApiEnvironment {
            override fun httpClient() = client

            override fun authenticatedCookies() = mapOf("d_c0" to "device", "_xsrf" to "xsrf")

            override suspend fun handleFetchFailure(tag: String?, error: Exception) = Unit
        }
        try {
            block(environment, requests)
        } finally {
            client.close()
            Dispatchers.resetMain()
        }
    }

    private companion object {
        const val COLUMN_ID = "c_2085111823087547085"
    }
}
