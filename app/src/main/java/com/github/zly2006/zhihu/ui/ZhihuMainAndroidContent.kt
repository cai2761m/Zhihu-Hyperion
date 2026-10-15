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

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.github.zly2006.zhihu.MainActivity
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.androidUserMessageSink
import com.github.zly2006.zhihu.ui.components.rememberObservedSetting
import com.github.zly2006.zhihu.ui.subscreens.ENABLE_PREDICTIVE_BACK_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerSwitchState
import com.github.zly2006.zhihu.viewmodel.ArticleAnswerTransitionDirection
import com.github.zly2006.zhihu.viewmodel.ArticleViewModel
import org.koin.compose.koinInject

/**
 * Android 平台的 Zhihu-Hyperion 主界面入口。
 *
 * 这里把 [MainActivity] 持有的导航、偏好设置、文章页 ViewModel、回答切换转场和 NLP 页面适配到共享 [ZhihuMain]。
 * UI 结构仍由 common 主壳负责，Android 只提供生命周期、Activity、ViewModel 和平台专属页面实现。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AndroidZhihuMain(navController: NavHostController) {
    val motion = MaterialTheme.motionScheme
    val activity = LocalActivity.current as MainActivity
    val answerSwitchState = koinInject<ArticleAnswerSwitchState>()
    val settings = koinInject<SettingsStore>()
    val enablePredictiveBack by rememberObservedSetting(settings, ENABLE_PREDICTIVE_BACK_PREFERENCE_KEY) {
        getBoolean(ENABLE_PREDICTIVE_BACK_PREFERENCE_KEY, true)
    }
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val interceptBack = !enablePredictiveBack &&
        LocalWindowInfo.current.isWindowFocused &&
        !WindowInsets.isImeVisible
    ZhihuMain(
        navController = navController,
        mainTabNavigationTarget = activity.mainTabNavigationTarget,
        navigate = activity::navigate,
        navigateContent = activity::navigateIn,
        enableLandscapeListDetail = true,
        isListDetailCapable = isTablet,
        setCurrentMainTabOpenFrom = activity::setCurrentMainTabOpenFrom,
        consumeMainTabNavigationTarget = activity::consumeMainTabNavigationTarget,
        preferenceState = rememberAndroidZhihuMainPreferenceState(),
        predictiveBackEnabled = enablePredictiveBack,
        articleEnterTransition = {
            when (answerSwitchState.answerTransitionDirection) {
                ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                    slideInVertically(motion.slowSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                    slideInVertically(motion.slowSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                    slideInHorizontally(motion.slowSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                    slideInHorizontally(motion.slowSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec())
                }

                else -> {
                    slideInHorizontally(motion.slowSpatialSpec()) { it }
                }
            }
        },
        articleExitTransition = {
            when (answerSwitchState.answerTransitionDirection) {
                ArticleAnswerTransitionDirection.VERTICAL_NEXT -> {
                    slideOutVertically(motion.slowSpatialSpec()) { -it } + fadeOut(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.VERTICAL_PREVIOUS -> {
                    slideOutVertically(motion.slowSpatialSpec()) { it } + fadeOut(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_NEXT -> {
                    slideOutHorizontally(motion.slowSpatialSpec()) { -it } + fadeOut(motion.defaultEffectsSpec())
                }

                ArticleAnswerTransitionDirection.HORIZONTAL_PREVIOUS -> {
                    slideOutHorizontally(motion.slowSpatialSpec()) { it } + fadeOut(motion.defaultEffectsSpec())
                }

                else -> {
                    ExitTransition.None
                }
            }
        },
        articleContent = { article, navEntry ->
            val viewModel: ArticleViewModel = viewModel(navEntry) {
                ArticleViewModel(article, activity.httpClient, androidUserMessageSink(activity)) { onPause ->
                    navEntry.lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onPause(owner: LifecycleOwner) {
                            onPause()
                        }
                    })
                }
            }
            ArticleScreen(article, viewModel)
        },
        sentenceSimilarityContent = {
            SentenceSimilarityTestScreen()
        },
        blocklistSettingsNlpContent = { onNavigateBack ->
            NLPKeywordManagementScreen(
                innerPadding = PaddingValues(),
                onNavigateBack = onNavigateBack,
            )
        },
    )
    val dispatcher = checkNotNull(LocalNavigationEventDispatcherOwner.current).navigationEventDispatcher
    val currentInterceptBack by rememberUpdatedState(interceptBack)
    val committedInput = remember(dispatcher) {
        object : NavigationEventInput() {
            fun complete() = dispatchOnBackCompleted()
        }
    }
    val committedBack = remember(dispatcher, committedInput) {
        object : NavigationEventHandler<NavigationEventInfo>(NavigationEventInfo.None, isBackEnabled = false) {
            override fun onBackCompleted() {
                // Navigation Compose 默认处理手势进度；关闭预览时仅转发已提交的返回。
                // Overlay 优先级只屏蔽页面预览，提交时让原 dispatcher 按页面和弹层顺序消费返回；
                // 输入法可见时不启用此 handler，保留系统先收起键盘的语义。
                isBackEnabled = false
                try {
                    // 使用独立输入源转交提交事件：原输入源在本回调返回前仍处于手势中，重入同一输入会丢弃返回。
                    committedInput.complete()
                } finally {
                    isBackEnabled = currentInterceptBack
                }
            }
        }
    }
    LifecycleStartEffect(interceptBack, committedBack) {
        committedBack.isBackEnabled = interceptBack
        onStopOrDispose { committedBack.isBackEnabled = false }
    }
    DisposableEffect(dispatcher, committedBack, committedInput) {
        dispatcher.addInput(committedInput)
        dispatcher.addHandler(committedBack, NavigationEventDispatcher.PRIORITY_OVERLAY)
        onDispose {
            committedBack.remove()
            dispatcher.removeInput(committedInput)
        }
    }
}
