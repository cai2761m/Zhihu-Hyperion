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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.icons.AppIcon
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.notification.NotificationSettingsStore
import com.github.zly2006.zhihu.notification.NotificationType
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.SettingItemWithSwitch
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import org.koin.compose.koinInject
import com.github.zly2006.zhihu.notification.matchNotificationType as sharedMatchNotificationType

object NotificationPreferences {
    fun matchNotificationType(verb: String): NotificationType? = sharedMatchNotificationType(verb)
}

/**
 * 通知设置页。
 *
 * 页面分为阅读行为、系统通知和应用内显示三组：自动已读控制进入通知页后的处理方式，未读红点控制入口 badge，
 * 系统通知控制是否向 OS 发通知，应用内显示控制通知中心是否展示某类消息。这里使用 [NotificationSettingsStore]，
 * 不要和普通偏好设置 key 混用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    setting: String? = null,
) {
    val navigator = LocalNavigator.current
    val settingsStore = koinInject<NotificationSettingsStore>()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val highlightedSetting = setting.orEmpty()
    val scrollState = rememberScrollState()
    val pageTurnTarget = rememberPageTurnTarget(scrollState, enabled = true)

    var systemNotificationSettings by remember {
        mutableStateOf(
            NotificationType.entries.associateWith {
                settingsStore.getSystemNotificationEnabled(it)
            },
        )
    }

    var displayInAppSettings by remember {
        mutableStateOf(
            NotificationType.entries.associateWith {
                settingsStore.getDisplayInAppEnabled(it)
            },
        )
    }
    var autoMarkAsRead by remember { mutableStateOf(settingsStore.getAutoMarkAsReadEnabled()) }
    var unreadBadgeEnabled by remember { mutableStateOf(settingsStore.getUnreadBadgeEnabled()) }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("通知设置") },
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
                .padding(innerPadding)
                .padding(vertical = 16.dp),
        ) {
            SettingItemGroup(title = "阅读行为") {
                SettingItemWithSwitch(
                    title = { Text("打开通知自动已读") },
                    icon = { Icon(AppIcons.DoneAll, contentDescription = null) },
                    description = { Text("进入通知板块后，自动把当前查看的板块标记为已读") },
                    checked = autoMarkAsRead,
                    onCheckedChange = { checked ->
                        autoMarkAsRead = checked
                        settingsStore.setAutoMarkAsReadEnabled(checked)
                    },
                    settingKey = "autoMarkAsRead",
                    highlightedKey = highlightedSetting,
                )
                SettingItemWithSwitch(
                    title = { Text("显示未读红点") },
                    icon = { Icon(AppIcons.NotificationsUnread, contentDescription = null) },
                    checked = unreadBadgeEnabled,
                    onCheckedChange = { checked ->
                        unreadBadgeEnabled = checked
                        settingsStore.setUnreadBadgeEnabled(checked)
                    },
                    settingKey = "unreadBadge",
                    highlightedKey = highlightedSetting,
                )
            }

            SettingItemGroup(
                title = "系统通知",
                settingKey = "systemNotifications",
                highlightedKey = highlightedSetting,
            ) {
                NotificationType.entries.forEach { type ->
                    SettingItemWithSwitch(
                        title = { Text(type.displayName) },
                        icon = { Icon(type.settingIcon(), contentDescription = null) },
                        checked = systemNotificationSettings[type] ?: false,
                        onCheckedChange = { checked ->
                            systemNotificationSettings = systemNotificationSettings.toMutableMap().apply {
                                put(type, checked)
                            }
                            settingsStore.setSystemNotificationEnabled(type, checked)
                        },
                    )
                }
            }

            SettingItemGroup(
                title = "应用内显示",
                footer = { Text("选择在通知页面显示哪些通知") },
                settingKey = "displayInAppNotifications",
                highlightedKey = highlightedSetting,
            ) {
                NotificationType.entries.forEach { type ->
                    SettingItemWithSwitch(
                        title = { Text(type.displayName) },
                        icon = { Icon(type.settingIcon(), contentDescription = null) },
                        checked = displayInAppSettings[type] ?: true,
                        onCheckedChange = { checked ->
                            displayInAppSettings = displayInAppSettings.toMutableMap().apply {
                                put(type, checked)
                            }
                            settingsStore.setDisplayInAppEnabled(type, checked)
                        },
                    )
                }
            }
        }
    }
}

private fun NotificationType.settingIcon(): AppIcon = when (this) {
    NotificationType.LIKE_ANSWER -> AppIcons.Favorite
    NotificationType.LIKE_COMMENT -> AppIcons.AddReaction
    NotificationType.REPLY_COMMENT -> AppIcons.Reply
    NotificationType.INVITE_ANSWER -> AppIcons.ContactSupport
}
