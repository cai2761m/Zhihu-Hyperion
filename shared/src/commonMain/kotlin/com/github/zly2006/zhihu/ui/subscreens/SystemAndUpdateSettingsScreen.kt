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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.data.AIGC_MARKING_ENABLED_PREFERENCE_KEY
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformName
import com.github.zly2006.zhihu.platform.rememberExternalUrlOpener
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.update.AvailableUpdate
import com.github.zly2006.zhihu.update.CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY
import com.github.zly2006.zhihu.update.GITHUB_ACCELERATION_PREFERENCE_KEY
import com.github.zly2006.zhihu.update.GitHubAcceleration
import com.github.zly2006.zhihu.update.UpdateController
import com.github.zly2006.zhihu.update.UpdateState
import com.github.zly2006.zhihu.update.isInAppUpdateInstallSupported
import com.github.zly2006.zhihu.util.ContinuousUsageReminderPolicy
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import zhihu.shared.generated.resources.Res
import zhihu.shared.generated.resources.ic_github_24dp
import zhihu.shared.generated.resources.ic_telegram_24dp

internal const val CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY = "continuousUsageReminderIntervalMinutes"
const val SYSTEM_SETTINGS_AIGC_MARKING_TAG = "system_settings_aigc_marking"

/**
 * 系统、更新和外部服务设置页。
 *
 * 页面展示更新横幅、下载/安装/跳过版本操作、Nightly 与 GitHub 加速开关、防沉迷提醒和社区链接。
 * 更新状态与动作来自 [UpdateController]；只有 Android 能在应用内下载安装，其他平台在浏览器中打开下载地址。
 * 防沉迷间隔写入 [CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemAndUpdateSettingsScreen(
    setting: String? = null,
) {
    val settings = koinInject<SettingsStore>()
    val updateController = koinInject<UpdateController>()
    val openExternalUrl = rememberExternalUrlOpener()
    val navigator = LocalNavigator.current
    val highlightedSetting = setting.orEmpty()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("系统与更新") },
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
        val scrollState = rememberScrollState()
        val pageTurnTarget = rememberPageTurnTarget(scrollState, enabled = true)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pageTurnViewportWithGuide(pageTurnTarget)
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(vertical = 16.dp),
        ) {
            val updateState by updateController.state.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            val shownUpdate = when (val state = updateState) {
                is UpdateState.Available -> state.update
                is UpdateState.Downloading -> state.update
                is UpdateState.Downloaded -> state.update
                else -> null
            }
            // The banner's exit animation still needs the update it was showing.
            var lastShownUpdate by remember { mutableStateOf<AvailableUpdate?>(null) }
            LaunchedEffect(shownUpdate) {
                if (shownUpdate != null) lastShownUpdate = shownUpdate
            }

            AnimatedVisibility(visible = shownUpdate != null) {
                val update = shownUpdate ?: lastShownUpdate ?: return@AnimatedVisibility
                Surface(
                    color = MaterialTheme.colorScheme.surfaceBright,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp, 12.dp)) {
                        Text(
                            text = "新版本：\n${update.displayVersion}",
                            style = MaterialTheme.typography.titleLarge,
                        )

                        if (update.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(12.dp, 8.dp)) {
                                    Text(
                                        "更新内容",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                    )
                                    SelectionContainer {
                                        Text(
                                            buildAnnotatedString {
                                                // git-cliff writes "(#123)"; manifests published before it link the whole URL.
                                                val prRegex = Regex("https://github.com/eltavine/Zhihu-Hyperion/pull/(\\d+)|#(\\d+)")
                                                var lastIndex = 0
                                                prRegex.findAll(update.notes).forEach { matchResult ->
                                                    append(update.notes.substring(lastIndex, matchResult.range.first))
                                                    val prNumber = matchResult.groupValues[1].ifEmpty { matchResult.groupValues[2] }
                                                    withLink(LinkAnnotation.Url("https://github.com/eltavine/Zhihu-Hyperion/pull/$prNumber")) {
                                                        withStyle(
                                                            MaterialTheme.typography.bodyMedium
                                                                .copy(color = MaterialTheme.colorScheme.primary)
                                                                .toSpanStyle(),
                                                        ) {
                                                            append("#$prNumber")
                                                        }
                                                    }
                                                    lastIndex = matchResult.range.last + 1
                                                }
                                                append(update.notes.substring(lastIndex))
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(
                                        onClick = { openExternalUrl(update.releaseUrl) },
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Text("查看完整更新日志")
                                        Icon(
                                            AppIcons.ArrowOutward,
                                            null,
                                            Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val downloading = updateState is UpdateState.Downloading
                            OutlinedButton(
                                onClick = { updateController.skip(update) },
                                modifier = Modifier.weight(1f),
                                enabled = !downloading,
                            ) {
                                Text("跳过此版本", Modifier.padding(0.dp, 4.dp))
                            }

                            val asset = update.asset
                            Button(
                                onClick = {
                                    when {
                                        asset == null -> openExternalUrl(update.releaseUrl)
                                        !isInAppUpdateInstallSupported -> openExternalUrl(asset.url)
                                        updateState is UpdateState.Downloaded -> updateController.install()
                                        else -> coroutineScope.launch { updateController.download(update) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !downloading,
                            ) {
                                Text(
                                    when {
                                        asset == null -> "前往发布页"
                                        !isInAppUpdateInstallSupported -> "前往下载"
                                        downloading -> "下载中..."
                                        updateState is UpdateState.Downloaded -> "安装更新"
                                        else -> "下载更新"
                                    },
                                    Modifier.padding(0.dp, 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            SettingItemGroup {
                val acceleration by updateController.acceleration.collectAsState()
                SettingItemWithSwitch(
                    title = { Text("GitHub 加速") },
                    icon = { Icon(AppIcons.Bolt, contentDescription = null) },
                    description = { Text("通过第三方加速服务 gh-proxy.com 检查和下载更新，适合直连 GitHub 很慢或失败的网络") },
                    checked = acceleration == GitHubAcceleration.ENABLED,
                    onCheckedChange = {
                        updateController.setAcceleration(if (it) GitHubAcceleration.ENABLED else GitHubAcceleration.DISABLED)
                    },
                    settingKey = GITHUB_ACCELERATION_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                )

                if (platformName == "macOS") {
                    var quitOnWindowClose by remember {
                        mutableStateOf(settings.getBoolean(MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, false))
                    }
                    SettingItemWithSwitch(
                        title = { Text("关闭窗口时退出应用") },
                        icon = { Icon(AppIcons.PowerSettingsNew, contentDescription = null) },
                        description = { Text("关闭最后一个窗口时同时退出 macOS 应用；默认关闭") },
                        checked = quitOnWindowClose,
                        onCheckedChange = {
                            quitOnWindowClose = it
                            settings.putBoolean(MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY, it)
                        },
                        settingKey = MACOS_QUIT_ON_WINDOW_CLOSE_PREFERENCE_KEY,
                        highlightedKey = highlightedSetting,
                    )
                }

                var checkNightlyUpdates by remember { mutableStateOf(settings.getBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, false)) }
                SettingItemWithSwitch(
                    title = { Text("检查 Nightly 版本更新") },
                    icon = { Icon(AppIcons.Science, contentDescription = null) },
                    description = { Text("检查每日构建版本 (可能不稳定)") },
                    checked = checkNightlyUpdates,
                    onCheckedChange = {
                        checkNightlyUpdates = it
                        settings.putBoolean(CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY, it)
                    },
                    settingKey = CHECK_NIGHTLY_UPDATES_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                )

                var aigcMarkingEnabled by remember {
                    mutableStateOf(settings.getBoolean(AIGC_MARKING_ENABLED_PREFERENCE_KEY, false))
                }
                SettingItemWithSwitch(
                    modifier = Modifier.testTag(SYSTEM_SETTINGS_AIGC_MARKING_TAG),
                    title = { Text("启用 AIGC 标记") },
                    icon = { Icon(AppIcons.SmartToy, contentDescription = null) },
                    description = {
                        Text(
                            "如果启用，会把你正在浏览的内容发送到我们的服务器，这样你可以知道其他用户是否认为其疑似 AIGC。默认关闭，不会发送隐私信息。",
                        )
                    },
                    checked = aigcMarkingEnabled,
                    onCheckedChange = {
                        aigcMarkingEnabled = it
                        settings.putBoolean(AIGC_MARKING_ENABLED_PREFERENCE_KEY, it)
                    },
                    settingKey = AIGC_MARKING_ENABLED_PREFERENCE_KEY,
                    highlightedKey = highlightedSetting,
                )
            }

            AnimatedVisibility(visible = shownUpdate == null) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            updateController.checkNow()
                            if (updateController.state.value is UpdateState.Available) {
                                scrollState.animateScrollTo(0)
                            }
                        }
                    },
                    enabled = updateState != UpdateState.Checking,
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp, 16.dp, 16.dp),
                ) {
                    Text(
                        when (updateState) {
                            UpdateState.Checking -> "检查中..."
                            UpdateState.UpToDate -> "已经是最新版本，点击重新检查"
                            is UpdateState.Failed -> "检查更新失败，点击重试"
                            else -> "检查更新"
                        },
                        Modifier.padding(0.dp, 4.dp),
                    )
                }
            }

            var reminderExpanded by remember { mutableStateOf(false) }
            var reminderIntervalMinutes by remember {
                mutableIntStateOf(
                    ContinuousUsageReminderPolicy.normalizeIntervalMinutes(
                        settings.getInt(
                            CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
                            0,
                        ),
                    ),
                )
            }
            val reminderOptions = listOf(
                0 to "关闭",
                15 to "每 15 分钟",
                30 to "每 30 分钟",
                60 to "每 1 小时",
            )

            SettingItemGroup(
                title = "防沉迷",
            ) {
                SettingItem(
                    title = { Text("防沉迷提醒") },
                    icon = { Icon(AppIcons.HourglassTop, contentDescription = null) },
                    description = { Text("你已经连续浏览知乎 N 小时 M 分钟了，休息一下吧。退出后 5 分钟内重开仍视为连续使用。") },
                    settingKey = CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY,
                    highlightedKey = highlightedSetting,
                    bottomAction = {
                        ExposedDropdownMenuBox(
                            expanded = reminderExpanded,
                            onExpandedChange = { reminderExpanded = it },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = reminderOptions
                                    .find { it.first == reminderIntervalMinutes }
                                    ?.second ?: "关闭",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = reminderExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded = reminderExpanded,
                                onDismissRequest = { reminderExpanded = false },
                            ) {
                                reminderOptions.forEach { (minutes, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        trailingIcon = if (minutes == reminderIntervalMinutes) {
                                            { Icon(AppIcons.Check, contentDescription = "当前设置") }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            reminderIntervalMinutes = minutes
                                            settings.putInt(CONTINUOUS_USAGE_REMINDER_INTERVAL_MINUTES_KEY, minutes)
                                            reminderExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            SettingItemGroup(
                title = "交流 & 闲聊",
                footer = { Text("代码和功能反馈请前往 GitHub。Telegram 群组用于用户交流和闲聊，开发者不一定会在线回答问题。") },
            ) {
                SettingItem(
                    title = { Text("Telegram 群组") },
                    description = { Text("Zhihu-Hyperion 用户交流群") },
                    icon = { Icon(painterResource(Res.drawable.ic_telegram_24dp), null) },
                    endAction = {
                        Icon(
                            AppIcons.ArrowOutward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { openExternalUrl("https://t.me/Zhihu_Hyperion") },
                )

                SettingItem(
                    title = { Text("Github issue") },
                    description = { Text("欢迎提交 issue 讨论功能和反馈问题") },
                    icon = { Icon(painterResource(Res.drawable.ic_github_24dp), null) },
                    endAction = {
                        Icon(
                            AppIcons.ArrowOutward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { openExternalUrl("https://github.com/eltavine/Zhihu-Hyperion/issues") },
                )
            }
        }
    }
}
