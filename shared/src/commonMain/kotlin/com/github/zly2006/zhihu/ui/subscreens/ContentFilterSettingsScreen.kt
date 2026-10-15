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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.QUALITY_FILTER_MODE_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.QualityFilterMode
import com.github.zly2006.zhihu.data.RecommendationMode
import com.github.zly2006.zhihu.data.VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.data.VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY
import com.github.zly2006.zhihu.filter.ContentFilterStats
import com.github.zly2006.zhihu.filter.clearAllData
import com.github.zly2006.zhihu.filter.loadFilterStats
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.ChoiceButtonGroup
import com.github.zly2006.zhihu.ui.components.ChoiceOption
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.util.Log
import com.github.zly2006.zhihu.viewmodel.feed.AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.milliseconds

/**
 * 推荐系统与内容过滤设置页。
 *
 * 页面分为推荐来源、智能过滤、关键词/用户/主题屏蔽、广告/付费内容过滤、屏蔽列表、屏蔽记录和过滤统计。这里的开关会影响首页信息流
 * 数据来源和 Feed 卡片的更多菜单行为，不能只按静态设置页验证；改动后要检查推荐拉取、过滤运行时和 Blocklist 管理入口。
 *
 * [setting] 用于从其他 UI 入口跳转并高亮具体设置项，新增过滤能力时应保持这个参数可用，方便用户从提示或弹窗直接回到相关配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentFilterSettingsScreen(
    setting: String? = null,
) {
    val navigator = LocalNavigator.current
    val coroutineScope = rememberCoroutineScope()
    val settings = koinInject<SettingsStore>()
    val contentFilterDao = koinInject<ContentFilterDatabase>().contentFilterDao()
    val userMessages = rememberUserMessageSink()
    val highlightedSetting = setting.orEmpty()

    val scrollState = rememberScrollState()
    val pageTurnTarget = rememberPageTurnTarget(scrollState, enabled = true)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(highlightedSetting) {
        if (highlightedSetting.isNotEmpty()) {
            delay(200.milliseconds)
            // 收缩 LargeTopAppBar（programmatic scroll 不触发 nestedScroll）
            scrollBehavior.state.heightOffset = scrollBehavior.state.heightOffsetLimit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("推荐系统与内容过滤") },
                navigationIcon = {
                    IconButton(
                        onClick = navigator.onNavigateBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageTurnViewportWithGuide(pageTurnTarget)
                .verticalScroll(scrollState)
                .testTag("contentFilterSettings:scroll")
                .padding(innerPadding)
                .padding(vertical = 16.dp),
        ) {
            SettingItemGroup {
                SettingItem(
                    title = { Text("推荐算法") },
                    icon = { Icon(AppIcons.Explore, contentDescription = null) },
                    settingKey = "recommendationMode",
                    highlightedKey = highlightedSetting,
                    bottomAction = {
                        // 推荐模式
                        val currentRecommendationMode = remember {
                            mutableStateOf(
                                RecommendationMode.entries.find {
                                    it.key == settings.getString("recommendationMode", RecommendationMode.MIXED.key)
                                } ?: RecommendationMode.MIXED,
                            )
                        }
                        var expanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = currentRecommendationMode.value.displayName,
                                onValueChange = { },
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                                    .testTag("contentFilterSettings:recommendationModeField"),
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                            ) {
                                RecommendationMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(mode.displayName)
                                                Text(mode.description, style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        trailingIcon = if (mode == currentRecommendationMode.value) {
                                            { Icon(AppIcons.Check, contentDescription = "当前设置") }
                                        } else {
                                            null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                when (mode) {
                                                    RecommendationMode.WEB -> AppIcons.Language
                                                    RecommendationMode.ANDROID -> AppIcons.Android
                                                    RecommendationMode.LOCAL -> AppIcons.Database
                                                    RecommendationMode.MIXED -> AppIcons.Merge
                                                },
                                                contentDescription = null,
                                            )
                                        },
                                        onClick = {
                                            currentRecommendationMode.value = mode
                                            settings.putString("recommendationMode", mode.key)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )

                val isLoginForRecommendation = remember {
                    mutableStateOf(settings.getBoolean("loginForRecommendation", true))
                }
                SettingItemWithSwitch(
                    modifier = Modifier.testTag("contentFilterSettings:loginForRecommendation"),
                    title = { Text("推荐内容时登录") },
                    icon = { Icon(AppIcons.Login, contentDescription = null) },
                    description = { Text("获取推荐内容时携带登录凭证") },
                    checked = isLoginForRecommendation.value,
                    onCheckedChange = { checked ->
                        isLoginForRecommendation.value = checked
                        settings.putBoolean("loginForRecommendation", checked)
                    },
                    settingKey = "loginForRecommendation",
                    highlightedKey = highlightedSetting,
                )

                val autoRefreshHomeOnStartup = remember {
                    mutableStateOf(settings.getBoolean(AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, true))
                }
                SettingItemWithSwitch(
                    modifier = Modifier.testTag("contentFilterSettings:autoRefreshHomeOnStartup"),
                    title = { Text("启动时自动刷新首页") },
                    icon = { Icon(AppIcons.Autorenew, contentDescription = null) },
                    description = { Text("关闭后优先显示上次获取的一批首页推荐；没有缓存时仍会加载新推荐") },
                    checked = autoRefreshHomeOnStartup.value,
                    onCheckedChange = { checked ->
                        autoRefreshHomeOnStartup.value = checked
                        settings.putBoolean(AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY, checked)
                    },
                    settingKey = AUTO_REFRESH_HOME_ON_STARTUP_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                )
            }

            val enableContentFilter = remember { mutableStateOf(settings.getBoolean("enableContentFilter", true)) }
            SettingItemGroup {
                val qualityFilterMode = remember {
                    mutableStateOf(
                        QualityFilterMode.entries.firstOrNull {
                            it.name == settings.getString(QUALITY_FILTER_MODE_PREFERENCE_KEY, QualityFilterMode.RULES.name)
                        } ?: QualityFilterMode.RULES,
                    )
                }
                SettingItem(
                    title = { Text("质量屏蔽") },
                    icon = { Icon(AppIcons.HighQuality, contentDescription = null) },
                    description = { Text("根据赞同数、关注数等指标处理低质量内容") },
                    settingKey = QUALITY_FILTER_MODE_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                    bottomAction = {
                        ChoiceButtonGroup(
                            options = listOf(
                                ChoiceOption(QualityFilterMode.OFF, "不屏蔽"),
                                ChoiceOption(QualityFilterMode.RULES, "屏蔽规则"),
                                ChoiceOption(QualityFilterMode.HIDE, "隐藏"),
                            ),
                            selected = qualityFilterMode.value,
                            onSelect = { mode ->
                                qualityFilterMode.value = mode
                                settings.putString(QUALITY_FILTER_MODE_PREFERENCE_KEY, mode.name)
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    },
                )

                val thresholdValues = remember {
                    mutableStateMapOf(
                        ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY to settings.getInt(ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY, 10),
                        ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY to settings.getInt(ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY, 20),
                        ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to settings.getInt(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50),
                        VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY to settings.getInt(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, 20),
                        VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to settings.getInt(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50),
                        QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY to settings.getInt(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, 0),
                        QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY to settings.getInt(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, 50),
                    )
                }
                var thresholdKey by remember { mutableStateOf<String?>(null) }
                SettingItem(
                    title = { Text("回答最低赞数") },
                    icon = { Icon(AppIcons.ThumbUp, contentDescription = null) },
                    description = { Text("低于此赞同数的未关注作者回答会被过滤") },
                    settingKey = ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                    endAction = { Text(thresholdValues[ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY].toString(), modifier = Modifier.padding(horizontal = 16.dp)) },
                    onClick = { thresholdKey = ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY },
                )
                SettingItem(
                    title = { Text("文章最低赞数") },
                    icon = { Icon(AppIcons.ThumbUp, contentDescription = null) },
                    description = { Text("低于此赞数或作者粉丝低于对应阈值的文章会被过滤") },
                    settingKey = ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                    endAction = { Text(thresholdValues[ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY].toString(), modifier = Modifier.padding(horizontal = 16.dp)) },
                    onClick = { thresholdKey = ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY },
                )
                var advancedThresholdsExpanded by remember {
                    mutableStateOf(
                        highlightedSetting in setOf(
                            ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY,
                            VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY,
                            VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY,
                            QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY,
                            QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY,
                        ),
                    )
                }
                SettingItem(
                    title = { Text("其他质量过滤阈值") },
                    icon = { Icon(AppIcons.InstantMix, contentDescription = null) },
                    description = { Text("文章粉丝数、视频和问题规则") },
                    endAction = { Text(if (advancedThresholdsExpanded) "收起" else "展开", modifier = Modifier.padding(horizontal = 16.dp)) },
                    onClick = { advancedThresholdsExpanded = !advancedThresholdsExpanded },
                )
                AnimatedVisibility(visible = advancedThresholdsExpanded) {
                    Column {
                        listOf(
                            Triple(ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "文章最低粉丝数", AppIcons.Group),
                            Triple(VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY, "视频最低赞数", AppIcons.ThumbUp),
                            Triple(VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "视频最低粉丝数", AppIcons.Group),
                            Triple(QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY, "问题最低回答数", AppIcons.Forum),
                            Triple(QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY, "问题最低关注数", AppIcons.Group),
                        ).forEach { (key, title, icon) ->
                            SettingItem(
                                title = { Text(title) },
                                icon = { Icon(icon, contentDescription = null) },
                                settingKey = key,
                                highlightedKey = highlightedSetting,
                                endAction = { Text(thresholdValues[key].toString(), modifier = Modifier.padding(horizontal = 16.dp)) },
                                onClick = { thresholdKey = key },
                            )
                        }
                    }
                }
                if (thresholdKey != null) {
                    val key = thresholdKey!!
                    val current = thresholdValues[key] ?: 0
                    var input by remember(key) { mutableStateOf(current.toString()) }
                    val thresholdTitle = when (key) {
                        ANSWER_VOTEUP_THRESHOLD_PREFERENCE_KEY -> "回答最低赞数"
                        ARTICLE_VOTEUP_THRESHOLD_PREFERENCE_KEY -> "文章最低赞数"
                        ARTICLE_FOLLOWERS_THRESHOLD_PREFERENCE_KEY -> "文章最低粉丝数"
                        VIDEO_VOTE_THRESHOLD_PREFERENCE_KEY -> "视频最低赞数"
                        VIDEO_FOLLOWERS_THRESHOLD_PREFERENCE_KEY -> "视频最低粉丝数"
                        QUESTION_ANSWER_THRESHOLD_PREFERENCE_KEY -> "问题最低回答数"
                        QUESTION_FOLLOWERS_THRESHOLD_PREFERENCE_KEY -> "问题最低关注数"
                        else -> "质量过滤阈值"
                    }
                    AlertDialog(
                        onDismissRequest = { thresholdKey = null },
                        icon = { Icon(AppIcons.Tune, contentDescription = null) },
                        title = { Text("设置$thresholdTitle") },
                        text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("阈值") }, singleLine = true) },
                        confirmButton = {
                            TextButton(onClick = {
                                input.toIntOrNull()?.takeIf { it >= 0 }?.let { value ->
                                    thresholdValues[key] = value
                                    settings.putInt(key, value)
                                    thresholdKey = null
                                } ?: userMessages.showMessage("请输入不小于 0 的整数")
                            }) { Text("确定") }
                        },
                        dismissButton = { TextButton(onClick = { thresholdKey = null }) { Text("取消") } },
                    )
                }

                SettingItemWithSwitch(
                    modifier = Modifier.testTag("contentFilterSettings:enableContentFilter"),
                    title = { Text("启用智能内容过滤") },
                    icon = { Icon(AppIcons.Psychology, contentDescription = null) },
                    description = { Text("自动过滤首页展示超过2次但用户未点击的内容，减少重复推荐") },
                    checked = enableContentFilter.value,
                    onCheckedChange = {
                        enableContentFilter.value = it
                        settings.putBoolean("enableContentFilter", it)
                    },
                    settingKey = "enableContentFilter",
                    highlightedKey = highlightedSetting,
                )

                val filterFollowedUserContent = remember { mutableStateOf(settings.getBoolean("filterFollowedUserContent", false)) }
                SettingItemWithSwitch(
                    modifier = Modifier.testTag("contentFilterSettings:filterFollowedUserContent"),
                    title = { Text("过滤已关注用户内容") },
                    icon = { Icon(AppIcons.HowToReg, contentDescription = null) },
                    description = { Text("是否对已关注用户的内容也应用过滤规则。关闭此选项可确保关注用户的内容始终显示") },
                    checked = filterFollowedUserContent.value,
                    onCheckedChange = {
                        filterFollowedUserContent.value = it
                        settings.putBoolean("filterFollowedUserContent", it)
                    },
                    enabled = enableContentFilter.value,
                    settingKey = "filterFollowedUserContent",
                    highlightedKey = highlightedSetting,
                )
            }

            SettingItemGroup {
                val enableKeywordBlocking = remember { mutableStateOf(settings.getBoolean("enableKeywordBlocking", true)) }
                SettingItemWithSwitch(
                    title = { Text("启用关键词屏蔽") },
                    icon = { Icon(AppIcons.MatchWord, contentDescription = null) },
                    description = { Text("屏蔽包含特定关键词的内容") },
                    checked = enableKeywordBlocking.value,
                    onCheckedChange = {
                        enableKeywordBlocking.value = it
                        settings.putBoolean("enableKeywordBlocking", it)
                    },
                    settingKey = "enableKeywordBlocking",
                    highlightedKey = highlightedSetting,
                )

                val enableUserBlocking = remember { mutableStateOf(settings.getBoolean("enableUserBlocking", true)) }
                SettingItemWithSwitch(
                    title = { Text("启用用户屏蔽") },
                    icon = { Icon(AppIcons.PersonOff, contentDescription = null) },
                    description = { Text("屏蔽特定用户发布的内容，或由特定用户提出的问题") },
                    checked = enableUserBlocking.value,
                    onCheckedChange = {
                        enableUserBlocking.value = it
                        settings.putBoolean("enableUserBlocking", it)
                    },
                    settingKey = "enableUserBlocking",
                    highlightedKey = highlightedSetting,
                )

                val enableTopicBlocking = remember { mutableStateOf(settings.getBoolean("enableTopicBlocking", true)) }
                SettingItemWithSwitch(
                    title = { Text("启用主题屏蔽") },
                    icon = { Icon(AppIcons.Tag, contentDescription = null) },
                    description = { Text("屏蔽包含特定主题的内容") },
                    checked = enableTopicBlocking.value,
                    onCheckedChange = {
                        enableTopicBlocking.value = it
                        settings.putBoolean("enableTopicBlocking", it)
                    },
                    settingKey = "enableTopicBlocking",
                    highlightedKey = highlightedSetting,
                )

                AnimatedVisibility(visible = enableTopicBlocking.value) {
                    val topicThreshold = remember { mutableStateOf(settings.getInt("topicBlockingThreshold", 1)) }
                    var showThresholdDialog by remember { mutableStateOf(false) }

                    SettingItem(
                        title = { Text("主题屏蔽阈值") },
                        icon = { Icon(AppIcons.LinearScale, contentDescription = null) },
                        description = {
                            Text(
                                "当回答的问题包含 >= ${topicThreshold.value} 个被屏蔽主题时，屏蔽该内容",
                            )
                        },
                        settingKey = "topicBlockingThreshold",
                        highlightedKey = highlightedSetting,
                        endAction = {
                            Text(
                                topicThreshold.value.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        },
                        onClick = { showThresholdDialog = true },
                    )
                    if (showThresholdDialog) {
                        var inputValue by remember { mutableStateOf(topicThreshold.value.toString()) }

                        AlertDialog(
                            onDismissRequest = { showThresholdDialog = false },
                            icon = { Icon(AppIcons.LinearScale, contentDescription = null) },
                            title = { Text("设置主题屏蔽阈值") },
                            text = {
                                Column {
                                    Text("当内容包含的被屏蔽主题数量达到或超过此阈值时，该内容将被屏蔽。")
                                    Spacer(modifier = Modifier.height(16.dp))
                                    OutlinedTextField(
                                        value = inputValue,
                                        onValueChange = { inputValue = it },
                                        label = { Text("阈值") },
                                        singleLine = true,
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val newThreshold = inputValue.toIntOrNull()
                                        if (newThreshold != null && newThreshold > 0) {
                                            topicThreshold.value = newThreshold
                                            settings.putInt("topicBlockingThreshold", newThreshold)
                                            showThresholdDialog = false
                                        } else {
                                            userMessages.showMessage("请输入大于0的整数")
                                        }
                                    },
                                ) {
                                    Text("确定")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showThresholdDialog = false }) {
                                    Text("取消")
                                }
                            },
                        )
                    }
                }
            }

            SettingItemGroup {
                val blockZhihuAdPlatform = remember { mutableStateOf(settings.getBoolean("blockZhihuAdPlatform", true)) }
                SettingItemWithSwitch(
                    title = { Text("屏蔽知乎广告平台内容") },
                    icon = { Icon(AppIcons.AdOff, contentDescription = null) },
                    description = { Text("匹配并屏蔽包含 xg.zhihu.com 的推广内容") },
                    checked = blockZhihuAdPlatform.value,
                    onCheckedChange = {
                        blockZhihuAdPlatform.value = it
                        settings.putBoolean("blockZhihuAdPlatform", it)
                    },
                    settingKey = "blockZhihuAdPlatform",
                    highlightedKey = highlightedSetting,
                )

                val blockZhihuSchool = remember { mutableStateOf(settings.getBoolean("blockZhihuSchool", true)) }
                SettingItemWithSwitch(
                    title = { Text("屏蔽知乎学堂内容") },
                    icon = { Icon(AppIcons.School, contentDescription = null) },
                    description = { Text("匹配并屏蔽包含 d.zhihu.com 或 data-edu-card-id 的内容") },
                    checked = blockZhihuSchool.value,
                    onCheckedChange = {
                        blockZhihuSchool.value = it
                        settings.putBoolean("blockZhihuSchool", it)
                    },
                    settingKey = "blockZhihuSchool",
                    highlightedKey = highlightedSetting,
                )

                val blockWeChatOfficialAccount = remember { mutableStateOf(settings.getBoolean("blockWeChatOfficialAccount", true)) }
                SettingItemWithSwitch(
                    title = { Text("屏蔽微信公众号文章") },
                    icon = { Icon(AppIcons.Chat, contentDescription = null) },
                    description = { Text("匹配并屏蔽包含 mp.weixin.qq.com 的外链文章") },
                    checked = blockWeChatOfficialAccount.value,
                    onCheckedChange = {
                        blockWeChatOfficialAccount.value = it
                        settings.putBoolean("blockWeChatOfficialAccount", it)
                    },
                    settingKey = "blockWeChatOfficialAccount",
                    highlightedKey = highlightedSetting,
                )

                val blockPaidContent = remember { mutableStateOf(settings.getBoolean("blockPaidContent", true)) }
                SettingItemWithSwitch(
                    title = { Text("屏蔽知乎盐选付费内容") },
                    icon = { Icon(AppIcons.Paid, contentDescription = null) },
                    description = { Text("屏蔽知乎盐选会员专享的付费回答和文章") },
                    checked = blockPaidContent.value,
                    onCheckedChange = {
                        blockPaidContent.value = it
                        settings.putBoolean("blockPaidContent", it)
                    },
                    settingKey = "blockPaidContent",
                    highlightedKey = highlightedSetting,
                )

                val reverseBlock = remember { mutableStateOf(settings.getBoolean("reverseBlock", false)) }
                SettingItemWithSwitch(
                    title = { Text("反向屏蔽（吃\uD83D\uDCA9模式）") },
                    icon = { Icon(AppIcons.SwapHoriz, contentDescription = null) },
                    description = { Text("开启后，首页将只保留广告和付费内容，屏蔽其余所有内容") },
                    checked = reverseBlock.value,
                    onCheckedChange = {
                        reverseBlock.value = it
                        settings.putBoolean("reverseBlock", it)
                    },
                    settingKey = "reverseBlock",
                    highlightedKey = highlightedSetting,
                )
            }

            SettingItemGroup {
                SettingItem(
                    modifier = Modifier.testTag("contentFilterSettings:blocklist"),
                    title = { Text("管理屏蔽列表") },
                    icon = { Icon(AppIcons.PlaylistRemove, contentDescription = null) },
                    onClick = { navigator.onNavigate(Account.RecommendSettings.Blocklist) },
                    endAction = {
                        Icon(
                            AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            SettingItemGroup {
                SettingItem(
                    modifier = Modifier.testTag("contentFilterSettings:blockedFeedHistory"),
                    title = { Text("屏蔽记录") },
                    icon = { Icon(AppIcons.ManageHistory, contentDescription = null) },
                    onClick = { navigator.onNavigate(Account.RecommendSettings.BlockedFeedHistory) },
                    endAction = {
                        Icon(
                            AppIcons.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }

            // 过滤统计（简化版）
            var filterStats by remember { mutableStateOf<ContentFilterStats?>(null) }
            var showStatsDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                try {
                    filterStats = contentFilterDao.loadFilterStats()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("ContentFilterSettingsScreen", "Failed to load filter stats", e)
                }
            }

            SettingItemGroup {
                AnimatedVisibility(visible = enableContentFilter.value && filterStats != null) {
                    SettingItem(
                        title = { Text("过滤统计") },
                        icon = { Icon(AppIcons.BarChart, contentDescription = null) },
                        description = {
                            Text(
                                "已累计过滤 ${filterStats?.filteredCount ?: 0} 条内容，点击查看详情",
                            )
                        },
                        onClick = { showStatsDialog = true },
                    )
                }
            }

            if (showStatsDialog && filterStats != null) {
                AlertDialog(
                    onDismissRequest = { showStatsDialog = false },
                    icon = { Icon(AppIcons.BarChart, contentDescription = null) },
                    title = { Text("过滤统计详情") },
                    text = {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("总记录数: ${filterStats?.totalRecords}")
                                Text("过滤率: ${(filterStats?.filterRate ?: 0f) * 100}%%")
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            filterStats = contentFilterDao.clearAllData()
                                            userMessages.showMessage("已清除曝光记录")
                                            showStatsDialog = false
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            // 忽略分享异常。
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("清除曝光记录")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showStatsDialog = false }) {
                            Text("关闭")
                        }
                    },
                )
            }
        }
    }
}
