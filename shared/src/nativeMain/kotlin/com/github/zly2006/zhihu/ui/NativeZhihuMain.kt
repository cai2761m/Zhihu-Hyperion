/*
 * Zhihu-Hyperion - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 * Co-author: eltavine <me@eltavine.com>
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

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.filter.ContentOpenEventSupport
import com.github.zly2006.zhihu.filter.ContentOpenTracker
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Article
import com.github.zly2006.zhihu.navigation.CollectionContent
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.History
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MainTabs
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Pin
import com.github.zly2006.zhihu.navigation.Question
import com.github.zly2006.zhihu.navigation.TopLevelDestination
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.START_DESTINATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.bottomBarItemOrderFromPreference
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.navDestinationFromName
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.resolveValidStartDestinationKey
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerTransitionDirection
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import org.koin.compose.koinInject

/**
 * 平台原生窗口外框（macOS 侧栏与工具栏）读取的主壳状态和动作。
 *
 * 外框只按这里的声明渲染，不自己维护一级页面列表或导航逻辑。
 */
class NativeMainChromeState(
    /** 用户在设置里选中的一级页面，按显示顺序排列。 */
    val mainTabs: List<TopLevelDestination>,
    val currentMainTab: TopLevelDestination,
    /** 切到一级页面，并清空当前页面栈。 */
    val openMainTab: (TopLevelDestination) -> Unit,
    val navigate: (NavDestination) -> Unit,
)

/** 由平台提供的原生窗口外框；拿到 [NativeMainChromeState] 和主界面内容后负责摆放两者。 */
typealias NativeMainChromeHost =
    @Composable (state: NativeMainChromeState, content: @Composable (Modifier) -> Unit) -> Unit

/**
 * macOS 与 iOS 共用的 Kotlin/Native 主界面入口。
 *
 * 平台宿主只调用这个入口；所有页面、布局和导航图仍由共享 [ZhihuMain] 提供。
 * 传入 [chrome] 时由原生外框承担一级导航和搜索、通知入口，主壳不再显示底栏和首页顶部按钮。
 */
@Composable
fun NativeZhihuMain(chrome: NativeMainChromeHost? = null) {
    /** 主返回栈控制器，承载 MainTabs 主壳和单栏页面。 */
    val navController = rememberNavController()
    val accountStore = koinInject<ZhihuAccountStore>()
    val contentOpens = koinInject<ContentOpenTracker>()
    val answerSwitchState = koinInject<ArticleAnswerSwitchState>()
    val accounts by accountStore.accountsState.collectAsState()
    val accountSession = accounts.session
    val httpClient = remember(accountStore, accountSession) { accountStore.client.httpClient() }
    val userMessages = rememberUserMessageSink()
    val preferenceState = rememberNativeZhihuMainPreferenceState()
    var mainTabNavigationTarget by remember { mutableStateOf<TopLevelDestination?>(null) }
    var currentMainTabOpenFrom by remember { mutableStateOf<String?>(null) }
    var currentMainTabDestination by remember { mutableStateOf(preferenceState.startDestination) }

    fun navigateToMainTabs(clearRouteStack: Boolean = false) {
        navController.navigate(MainTabs) {
            launchSingleTop = true
            restoreState = !clearRouteStack
            popUpTo(MainTabs) {
                saveState = !clearRouteStack
            }
        }
    }

    /**
     * 从指定返回栈的当前页面读取内容打开来源，支持右侧详情栏。
     *
     * @param controller 提供来源页面的返回栈控制器
     */
    fun currentContentOpenSource(controller: NavHostController = navController): NavDestination? {
        val currentEntry = controller.currentBackStackEntry
        return runCatching {
            currentEntry?.toRoute<Article>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Question>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Pin>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<CollectionContent>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<History>()
        }.getOrNull() ?: runCatching {
            currentEntry?.toRoute<Notification>()
        }.getOrNull()
    }

    /**
     * 通过 [targetController] 指定的主返回栈或详情返回栈打开 [route]。
     *
     * @param route 要打开的页面
     * @param targetController 持有目标页面返回栈的控制器
     */
    fun navigate(route: NavDestination, targetController: NavHostController = navController) {
        when (route) {
            MainTabs -> {
                mainTabNavigationTarget = Home
                navigateToMainTabs()
            }

            else -> {
                contentOpens.prepare(
                    destination = route,
                    openFrom = if (
                        runCatching { navController.currentBackStackEntry?.toRoute<MainTabs>() }.getOrNull() != null
                    ) {
                        currentMainTabOpenFrom
                    } else {
                        null
                    }
                        ?: ContentOpenEventSupport.inferOpenFrom(currentContentOpenSource(targetController), route),
                )
                targetController.navigate(route)
            }
        }
    }

    val content: @Composable (Modifier) -> Unit = { modifier ->
        ZhihuMain(
            modifier = modifier,
            navController = navController,
            mainTabNavigationTarget = mainTabNavigationTarget,
            navigate = ::navigate,
            navigateContent = { destination, targetController -> navigate(destination, targetController) },
            enableLandscapeListDetail = true,
            setCurrentMainTabOpenFrom = { currentMainTabOpenFrom = it },
            consumeMainTabNavigationTarget = { destination ->
                if (mainTabNavigationTarget == destination) {
                    mainTabNavigationTarget = null
                }
            },
            preferenceState = preferenceState,
            showMainNavigationBar = chrome == null,
            showHomeTopActions = chrome == null,
            onCurrentMainTabDestinationChange = { currentMainTabDestination = it },
            articleEnterTransition = {
                when (answerSwitchState.answerTransitionDirection) {
                    ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                        slideInVertically(tween(300)) { it } + fadeIn(tween(300))
                    }

                    ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                        slideInVertically(tween(300)) { -it } + fadeIn(tween(300))
                    }

                    ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                        slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
                    }

                    ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                        slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300))
                    }

                    else -> {
                        slideInHorizontally(tween(300)) { it }
                    }
                }
            },
            articleExitTransition = {
                when (answerSwitchState.answerTransitionDirection) {
                    ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                        slideOutVertically(tween(300)) { -it } + fadeOut(tween(300))
                    }

                    ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                        slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                    }

                    ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                        slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                    }

                    ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                        slideOutHorizontally(tween(300)) { it } + fadeOut(tween(300))
                    }

                    else -> {
                        ExitTransition.None
                    }
                }
            },
            articleContent = { article: Article, navEntry ->
                val articleViewModel: ArticleViewModel = viewModel(navEntry) {
                    ArticleViewModel(article, httpClient, userMessages)
                }
                ArticleScreen(article, articleViewModel)
            },
        )
    }

    if (chrome == null) {
        content(Modifier)
    } else {
        chrome(
            NativeMainChromeState(
                mainTabs = preferenceState.selectedBottomBarItemKeys.map(::navDestinationFromName),
                currentMainTab = currentMainTabDestination,
                openMainTab = { destination ->
                    mainTabNavigationTarget = destination
                    navigateToMainTabs(clearRouteStack = true)
                },
                navigate = { navigate(it) },
            ),
            content,
        )
    }
}

@Composable
private fun rememberNativeZhihuMainPreferenceState(): ZhihuMainPreferenceState {
    val settings = koinInject<SettingsStore>()
    val allBottomBarItemKeys = remember {
        listOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name, MyCollections.name, Account.name)
    }
    return rememberZhihuMainPreferenceState {
        val duo3HomeAccount = settings.getBoolean("duo3_home_account", false)
        val selectedKeys = normalizeBottomBarSelection(
            settings.getStringSet(
                BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
                defaultBottomBarSelectionKeys(duo3HomeAccount, platformBottomBarItemLimit),
            ),
            duo3HomeAccount,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        )
        val orderedSelectedKeys = bottomBarItemOrderFromPreference(
            settings.getStringOrNull(BOTTOM_BAR_ITEM_ORDER_PREFERENCE_KEY),
            selectedKeys,
        )
        ZhihuMainPreferenceSnapshot(
            duo3HomeAccount = duo3HomeAccount,
            tapToScrollToTopEnabled = settings.getBoolean("bottomBarTapScrollToTop", true),
            autoHideBottomBar = settings.getBoolean("autoHideBottomBar", false),
            collectionDirectBrowseEnabled = settings.getBoolean(COLLECTION_DIRECT_BROWSE_PREFERENCE_KEY, false),
            landscapeListDetailEnabled = settings.getBoolean(LANDSCAPE_LIST_DETAIL_PREFERENCE_KEY, true),
            selectedBottomBarItemKeys = orderedSelectedKeys,
            startDestination = navDestinationFromName(
                resolveValidStartDestinationKey(
                    settings.getString(START_DESTINATION_PREFERENCE_KEY, Home.name),
                    orderedSelectedKeys.ifEmpty { allBottomBarItemKeys.filter { it in selectedKeys } },
                ),
            ),
        )
    }
}
