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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.zly2006.zhihu.account.ZhihuAccountStore
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.Notification
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.navigation.Person
import com.github.zly2006.zhihu.navigation.requestLoginNavigation
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.platformBottomBarItemLimit
import com.github.zly2006.zhihu.platform.rememberPlainTextClipboard
import com.github.zly2006.zhihu.platform.rememberSystemUrlOpener
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.reading.isReadingPlayerSupported
import com.github.zly2006.zhihu.ui.components.IconShape
import com.github.zly2006.zhihu.ui.components.MediumActionButton
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.ui.components.ShapedIcon
import com.github.zly2006.zhihu.ui.components.pageTurnViewportWithGuide
import com.github.zly2006.zhihu.ui.components.rememberPageTurnTarget
import com.github.zly2006.zhihu.ui.subscreens.BOTTOM_BAR_ITEMS_PREFERENCE_KEY
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.shouldShowAccountHistoryShortcut
import com.github.zly2006.zhihu.update.UpdateController
import com.github.zly2006.zhihu.update.UpdateState
import com.github.zly2006.zhihu.util.Log
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import zhihu.shared.generated.resources.Res
import zhihu.shared.generated.resources.app_icon
import zhihu.shared.generated.resources.ic_github_24dp

const val ACCOUNT_SETTINGS_SCROLL_TAG = "accountSettings.scroll"
const val ACCOUNT_SETTINGS_LOGIN_ITEM_TAG = "accountSettings.loginItem"
const val ACCOUNT_SETTINGS_PROFILE_HEADER_TAG = "accountSettings.profileHeader"
const val ACCOUNT_SETTINGS_PROFILE_NAME_TAG = "accountSettings.profileName"
const val ACCOUNT_SETTINGS_SCAN_TAG = "accountSettings.scan"
const val ACCOUNT_SETTINGS_LOGOUT_TAG = "accountSettings.logout"
const val ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG = "accountSettings.shortcutCollections"
const val ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG = "accountSettings.shortcutSubscriptions"
const val ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG = "accountSettings.shortcutNotification"
const val ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG = "accountSettings.shortcutHistory"
const val ACCOUNT_SETTINGS_APPEARANCE_TAG = "accountSettings.appearance"
const val ACCOUNT_SETTINGS_READING_TAG = "accountSettings.reading"
const val ACCOUNT_SETTINGS_RECOMMEND_TAG = "accountSettings.recommend"
const val ACCOUNT_SETTINGS_SEARCH_TAG = "accountSettings.search"
const val ACCOUNT_SETTINGS_SYSTEM_TAG = "accountSettings.system"
const val ACCOUNT_SETTINGS_WEBDAV_TAG = "accountSettings.webdav"
const val ACCOUNT_SETTINGS_DEVELOPER_TAG = "accountSettings.developer"
const val ACCOUNT_SETTINGS_LICENSES_TAG = "accountSettings.licenses"
const val ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG = "accountSettings.identityManagement"

/**
 * 账号与设置入口页。
 *
 * 已登录时顶部展示头像、昵称、重新登录和退出登录，Duo3 账号入口迁移开启后会额外展示收藏夹、关注订阅、通知和历史等快捷块；
 * 未登录时只展示登录入口。下方设置区是外观、推荐过滤、系统更新、开发者选项和开源许可的统一入口，其中开发者选项通过连续点击版本号开启。
 *
 * 这个页面既可以作为底部栏 tab 展示，也可以作为主页头像弹出的账号面板内容使用，所以 [innerPadding]、[onDismissRequest] 和
 * `duo3_home_account` 相关逻辑都不能随意删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingScreen(
    innerPadding: PaddingValues,
    unreadCount: Int = 0,
    showUnreadBadge: Boolean = true,
    onDismissRequest: () -> Unit = {},
    refreshAccountProfileOnEnter: Boolean = true,
    isActive: Boolean = true,
) {
    val navigator = LocalNavigator.current
    val accountStore = koinInject<ZhihuAccountStore>()
    val accountState = rememberAccountSettingsAccountState()
    val requestLogin = ::requestLoginNavigation
    val settings = koinInject<SettingsStore>()
    val copyPlainText = rememberPlainTextClipboard()
    val openSystemUrl = rememberSystemUrlOpener()
    val userMessages = rememberUserMessageSink()
    val updateController = koinInject<UpdateController>()
    val versionInfo = rememberAppVersionInfo()
    val readingPlayerSupported = isReadingPlayerSupported
    val launchQrScanner = rememberAccountQrScanAction()

    val useDuo3HomeAccount = remember { settings.getBoolean("duo3_home_account", false) }
    val selectedBottomBarItemKeys = remember {
        normalizeBottomBarSelection(
            settings.getStringSet(
                BOTTOM_BAR_ITEMS_PREFERENCE_KEY,
                defaultBottomBarSelectionKeys(useDuo3HomeAccount, platformBottomBarItemLimit),
            ),
            useDuo3HomeAccount,
            enforceMinimumSelection = true,
            maximumSelection = platformBottomBarItemLimit,
        )
    }
    var isDeveloper by remember { mutableStateOf(settings.getBoolean("developer", false)) }
    var clickTimes by remember { mutableIntStateOf(0) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val pageTurnTarget = rememberPageTurnTarget(
        scrollState = scrollState,
        enabled = isActive && !showLogoutDialog,
    )
    LaunchedEffect(isDeveloper) {
        settings.putBoolean("developer", isDeveloper)
    }
    val liveData by accountState
    val data = liveData

    Scaffold(
        // 外层已按 innerPadding 让出状态栏和导航栏，这里不能再按系统栏重复留白。
        modifier = Modifier
            .fillMaxSize()
            .consumeWindowInsets(innerPadding),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .testTag(ACCOUNT_SETTINGS_SCROLL_TAG)
                .pageTurnViewportWithGuide(pageTurnTarget)
                .verticalScroll(scrollState)
                .padding(padding),
        ) {
            LaunchedEffect(data.login, refreshAccountProfileOnEnter) {
                if (refreshAccountProfileOnEnter && data.login) {
                    try {
                        accountStore.client.refreshAndSaveProfile()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("AccountSettingScreen", "Failed to refresh account profile", e)
                        userMessages.showShortMessage("获取用户信息失败")
                    }
                }
            }

            if (data.login) {
                Surface(
                    onClick = {
                        navigator.onNavigate(
                            Person(
                                id = data.id,
                                urlToken = data.urlToken ?: "",
                                name = data.username,
                            ),
                        )
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .testTag(ACCOUNT_SETTINGS_PROFILE_HEADER_TAG),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = data.avatarUrl,
                            contentDescription = "头像",
                            modifier = Modifier
                                .size(64.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .clip(CircleShape),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = data.username,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag(ACCOUNT_SETTINGS_PROFILE_NAME_TAG),
                            )
                            Text(
                                text = "查看个人主页",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (launchQrScanner != null) {
                            FilledTonalIconButton(
                                onClick = launchQrScanner,
                                modifier = Modifier.testTag(ACCOUNT_SETTINGS_SCAN_TAG),
                            ) {
                                Icon(AppIcons.QrCodeScanner, contentDescription = "扫码登录")
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ShapedIcon(
                            AppIcons.Login,
                            shape = IconShape.Flower,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        Text("登录知乎", style = MaterialTheme.typography.headlineSmallEmphasized)
                        Text(
                            "登录后可查看关注、收藏、通知和浏览历史。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        MediumActionButton(
                            text = "登录",
                            onClick = requestLogin,
                            icon = AppIcons.Login,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ACCOUNT_SETTINGS_LOGIN_ITEM_TAG),
                        )
                    }
                }
            }

            if (useDuo3HomeAccount) {
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (data.login) {
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_COLLECTIONS_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    data.urlToken?.let { navigator.onNavigate(Collections(it)) }
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                AppIcons.BookmarksFilled,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "收藏夹",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    navigator.onNavigate(
                                        Person(
                                            id = data.id,
                                            urlToken = data.urlToken ?: "",
                                            name = data.username,
                                            jumpTo = "关注订阅",
                                        ),
                                    )
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                AppIcons.Groups,
                                null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "关注订阅",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Column(
                            Modifier
                                .testTag(ACCOUNT_SETTINGS_SHORTCUT_NOTIFICATION_TAG)
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    onDismissRequest()
                                    navigator.onNavigate(Notification)
                                }.padding(8.dp, 16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            BadgedBox(
                                badge = {
                                    if (showUnreadBadge && unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(
                                    AppIcons.Notifications,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "通知",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (shouldShowAccountHistoryShortcut(useDuo3HomeAccount, selectedBottomBarItemKeys)) {
                            Column(
                                Modifier
                                    .testTag(ACCOUNT_SETTINGS_SHORTCUT_HISTORY_TAG)
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        onDismissRequest()
                                        navigator.onNavigateTopLevel(OnlineHistory)
                                    }.padding(8.dp, 16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    AppIcons.History,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "浏览历史",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            } else if (data.login) {
                SettingItemGroup {
                    SettingItem(
                        title = { Text("查看收藏夹") },
                        icon = { Icon(AppIcons.Bookmarks, null) },
                        onClick = {
                            data.urlToken?.let { navigator.onNavigate(Collections(it)) }
                        },
                    )
                    SettingItem(
                        title = { Text("查看关注订阅") },
                        description = { Text("话题、问题、专栏和收藏夹") },
                        icon = { Icon(AppIcons.Groups, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_SHORTCUT_SUBSCRIPTIONS_TAG),
                        onClick = {
                            navigator.onNavigate(
                                Person(
                                    id = data.id,
                                    urlToken = data.urlToken ?: "",
                                    name = data.username,
                                    jumpTo = "关注订阅",
                                ),
                            )
                        },
                    )
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .testTag(ACCOUNT_SETTINGS_SEARCH_TAG),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = {
                        navigator.onNavigate(Account.SettingsSearch)
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            AppIcons.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "搜索设置项",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            SettingItemGroup {
                if (data.login) {
                    SettingItem(
                        title = { Text("身份管理") },
                        description = { Text("创建马甲号或切换当前账号") },
                        icon = { Icon(AppIcons.SwitchAccount, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_IDENTITY_MANAGEMENT_TAG),
                        onClick = { navigator.onNavigate(Account.IdentityManagement) },
                    )
                }

                SettingItem(
                    title = { Text("外观与阅读体验") },
                    description = { Text("主题颜色、字体大小等") },
                    icon = { Icon(AppIcons.Palette, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_APPEARANCE_TAG),
                    onClick = { navigator.onNavigate(Account.AppearanceSettings()) },
                )

                if (readingPlayerSupported) {
                    SettingItem(
                        title = { Text("朗读与播放") },
                        description = { Text("朗读内容、播放队列与条目过渡") },
                        icon = { Icon(AppIcons.VolumeUp, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_READING_TAG),
                        onClick = { navigator.onNavigate(Account.ReadingSettings) },
                    )
                }

                SettingItem(
                    title = { Text("推荐系统与内容过滤") },
                    description = { Text("推荐、智能过滤、关键词屏蔽等") },
                    icon = { Icon(AppIcons.FilterAlt, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_RECOMMEND_TAG),
                    onClick = { navigator.onNavigate(Account.RecommendSettings()) },
                )

                SettingItem(
                    title = { Text("系统与更新") },
                    description = { Text("GitHub、更新设置等") },
                    icon = { Icon(AppIcons.Settings, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_SYSTEM_TAG),
                    onClick = { navigator.onNavigate(Account.SystemAndUpdateSettings()) },
                )

                SettingItem(
                    title = { Text("WebDAV 同步") },
                    description = { Text("备份和恢复屏蔽列表与设置") },
                    icon = { Icon(AppIcons.CloudSync, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_WEBDAV_TAG),
                    onClick = { navigator.onNavigate(Account.WebDavSync) },
                )

                AnimatedVisibility(isDeveloper) {
                    SettingItem(
                        title = { Text("开发者选项") },
                        icon = { Icon(AppIcons.Code, null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_DEVELOPER_TAG),
                        onClick = { navigator.onNavigate(Account.DeveloperSettings) },
                    )
                }
            }

            val updateState by updateController.state.collectAsState()
            LaunchedEffect(updateState) {
                when (val state = updateState) {
                    is UpdateState.Available -> {
                        userMessages.showShortMessage("发现新版本 ${state.update.displayVersion}")
                    }

                    is UpdateState.Failed -> {
                        userMessages.showLongMessage("检查更新失败: ${state.message}")
                    }

                    else -> {}
                }
            }

            if (data.login) {
                SettingItemGroup {
                    SettingItem(
                        title = { Text("退出登录", color = MaterialTheme.colorScheme.error) },
                        icon = { Icon(AppIcons.Logout, contentDescription = null) },
                        modifier = Modifier.testTag(ACCOUNT_SETTINGS_LOGOUT_TAG),
                        onClick = { showLogoutDialog = true },
                    )
                }
            }

            SettingItemGroup(
                title = "关于",
                footer = { Text("本软件仅供学习交流使用，应用内内容由知乎网站提供，著作权归其对应作者所有。") },
            ) {
                SettingItem(
                    title = { Text("Zhihu-Hyperion") },
                    description = { Text("版本号：$versionInfo") },
                    icon = {
                        Image(
                            painterResource(Res.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .clip(CircleShape)
                                .size(32.dp),
                        )
                    },
                    modifier = Modifier.combinedClickable(
                        enabled = true,
                        onClick = {
                            clickTimes++
                            if (clickTimes == 5) {
                                clickTimes = 0
                                isDeveloper = true
                                userMessages.showShortMessage("You are now a developer")
                            }
                        },
                        onLongClick = {
                            copyPlainText("version", versionInfo)
                            userMessages.showShortMessage("已复制版本号")
                        },
                    ),
                )
                SettingItem(
                    title = { Text("GitHub 项目地址") },
                    description = { Text("https://github.com/eltavine/Zhihu-Hyperion") },
                    icon = { Icon(painterResource(Res.drawable.ic_github_24dp), null) },
                    onClick = {
                        openSystemUrl("https://github.com/eltavine/Zhihu-Hyperion")
                    },
                    endAction = {
                        Icon(
                            AppIcons.ArrowOutward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )

                SettingItem(
                    title = { Text("项目协议") },
                    description = { Text("AGPL-3.0-only") },
                    icon = { Icon(AppIcons.Gavel, null) },
                    onClick = {
                        openSystemUrl("https://github.com/eltavine/Zhihu-Hyperion/blob/master/LICENSE")
                    },
                    endAction = {
                        Icon(
                            AppIcons.ArrowOutward,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                SettingItem(
                    title = { Text("开源许可") },
                    description = { Text("查看第三方组件许可证") },
                    icon = { Icon(AppIcons.License, null) },
                    modifier = Modifier.testTag(ACCOUNT_SETTINGS_LICENSES_TAG),
                    onClick = { navigator.onNavigate(Account.OpenSourceLicenses) },
                )
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            icon = { Icon(AppIcons.Logout, contentDescription = null) },
            title = { Text("退出登录") },
            text = {
                Text(
                    if (accountStore.accounts.size > 1) {
                        "确定退出并移除当前登录账号吗？退出后会自动切换到另一个已保存账号。"
                    } else {
                        "确定要退出登录吗？"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountStore.clear()
                        showLogoutDialog = false
                    },
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            },
        )
    }
}
