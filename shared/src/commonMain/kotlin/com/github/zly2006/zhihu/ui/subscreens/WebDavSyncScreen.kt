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

package com.github.zly2006.zhihu.ui.subscreens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.ui.components.SettingItem
import com.github.zly2006.zhihu.ui.components.SettingItemGroup
import com.github.zly2006.zhihu.viewmodel.WebDavConfigFile
import com.github.zly2006.zhihu.viewmodel.WebDavSyncViewModel
import com.github.zly2006.zhihu.viewmodel.filter.ContentFilterDatabase
import io.ktor.client.engine.HttpClientEngine
import org.koin.compose.koinInject

const val WEBDAV_URL_TAG = "webdav:url"
const val WEBDAV_USERNAME_TAG = "webdav:username"
const val WEBDAV_PASSWORD_TAG = "webdav:password"
const val WEBDAV_TEST_TAG = "webdav:test"
const val WEBDAV_UPLOAD_TAG = "webdav:upload"
const val WEBDAV_RESTORE_TAG = "webdav:restore"
const val WEBDAV_STATUS_TAG = "webdav:status"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSyncScreen() {
    val navigator = LocalNavigator.current
    val settings = koinInject<SettingsStore>()
    val engine = koinInject<HttpClientEngine>()
    val database = koinInject<ContentFilterDatabase>()
    val configFile = koinInject<WebDavConfigFile>()
    val viewModel = viewModel { WebDavSyncViewModel(settings, engine, database, configFile) }
    var showPassword by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    DisposableEffect(viewModel) {
        onDispose { viewModel.saveConfig() }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = { Text("WebDAV 同步") },
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
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(vertical = 16.dp),
        ) {
            SettingItemGroup(
                title = "服务器",
                footer = {
                    Text(
                        "填写 WebDAV 目录地址，例如坚果云的 https://dav.jianguoyun.com/dav/。备份保存在其中的 zhihu-hyperion 文件夹；账号信息只保存在本机。",
                    )
                },
            ) {
                SettingItem(
                    title = { Text("服务器地址") },
                    icon = { Icon(AppIcons.Dns, contentDescription = null) },
                    bottomAction = {
                        OutlinedTextField(
                            value = viewModel.config.url,
                            onValueChange = { viewModel.config = viewModel.config.copy(url = it) },
                            placeholder = { Text("https://dav.example.com/dav/") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(WEBDAV_URL_TAG),
                            singleLine = true,
                        )
                    },
                )
                SettingItem(
                    title = { Text("用户名") },
                    icon = { Icon(AppIcons.Person, contentDescription = null) },
                    bottomAction = {
                        OutlinedTextField(
                            value = viewModel.config.username,
                            onValueChange = { viewModel.config = viewModel.config.copy(username = it) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(WEBDAV_USERNAME_TAG),
                            singleLine = true,
                        )
                    },
                )
                SettingItem(
                    title = { Text("应用密码") },
                    description = { Text("建议在 WebDAV 服务里单独生成应用密码，而不是使用登录密码。") },
                    icon = { Icon(AppIcons.Password, contentDescription = null) },
                    bottomAction = {
                        OutlinedTextField(
                            value = viewModel.config.password,
                            onValueChange = { viewModel.config = viewModel.config.copy(password = it) },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        icon = if (showPassword) AppIcons.Visibility else AppIcons.VisibilityOff,
                                        contentDescription = if (showPassword) "隐藏密码" else "显示密码",
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(WEBDAV_PASSWORD_TAG),
                            singleLine = true,
                        )
                    },
                )
            }

            SettingItemGroup(
                title = "同步",
                footer = {
                    Text(
                        "同步屏蔽词、屏蔽用户、屏蔽提问者、屏蔽话题，以及外观、阅读和内容过滤等偏好。不同步开发者选项、AIGC 标记授权、GitHub 加速、底栏布局和液态玻璃。",
                    )
                },
            ) {
                SettingItem(
                    title = { Text("测试连接") },
                    icon = { Icon(AppIcons.NetworkCheck, null) },
                    enabled = !viewModel.isRunning,
                    onClick = viewModel::testConnection,
                    modifier = Modifier.testTag(WEBDAV_TEST_TAG),
                )
                SettingItem(
                    title = { Text("上传到 WebDAV") },
                    description = { Text("用本机的屏蔽列表和设置覆盖 WebDAV 上的备份") },
                    icon = { Icon(AppIcons.CloudUpload, null) },
                    enabled = !viewModel.isRunning,
                    onClick = viewModel::upload,
                    modifier = Modifier.testTag(WEBDAV_UPLOAD_TAG),
                )
                SettingItem(
                    title = { Text("从 WebDAV 恢复") },
                    description = { Text("把备份合并到本机") },
                    icon = { Icon(AppIcons.CloudDownload, null) },
                    enabled = !viewModel.isRunning,
                    onClick = { confirmRestore = true },
                    modifier = Modifier.testTag(WEBDAV_RESTORE_TAG),
                )
            }

            if (viewModel.isRunning) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            }
            viewModel.status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp).testTag(WEBDAV_STATUS_TAG),
                )
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            icon = { Icon(AppIcons.CloudDownload, contentDescription = null) },
            title = { Text("从 WebDAV 恢复？") },
            text = { Text("备份里的屏蔽条目会合并到本机，本机多出的条目不会删除；同名设置会被备份里的值覆盖。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restore()
                    },
                ) {
                    Text("恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) {
                    Text("取消")
                }
            },
        )
    }
}
