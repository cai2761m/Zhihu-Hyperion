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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.icons.AppIcon
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.platform.PlatformBackHandler

/** [FabMenu] 中的一个动作；[testTag] 供 UI 自动化定位这一项。 */
@Immutable
class FabMenuAction(
    val label: String,
    val icon: AppIcon,
    val testTag: String? = null,
    val onClick: () -> Unit,
)

/**
 * 展开式 FAB 菜单（Material 3 Expressive FAB menu）。
 *
 * 点按钮展开一列带图标的动作，按钮同时从 [icon] 形变为关闭；点任一动作会先收起菜单再执行动作，展开时返回键也先收起菜单。
 * [buttonAlpha] 作用于收起时的整个按钮图层，阴影随之淡出，不会透过半透明的容器露出来。
 * Expressive FAB 菜单仍是实验 API，只在这里使用，调用方不依赖它。
 */
@Composable
fun FabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: AppIcon,
    contentDescription: String,
    actions: List<FabMenuAction>,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    buttonAlpha: Float = 1f,
) {
    PlatformBackHandler(enabled = expanded) { onExpandedChange(false) }
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
                modifier = buttonModifier.graphicsLayer { alpha = if (expanded) 1f else buttonAlpha },
                containerSize = ToggleFloatingActionButtonDefaults.containerSize(48.dp, 48.dp),
                containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(12.dp, 24.dp),
            ) {
                Icon(
                    if (checkedProgress > 0.5f) AppIcons.Close else icon,
                    contentDescription = if (expanded) "收起" else contentDescription,
                    modifier = Modifier.animateIcon(
                        checkedProgress = { checkedProgress },
                        size = ToggleFloatingActionButtonDefaults.iconSize(22.dp, 22.dp),
                    ),
                )
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    onExpandedChange(false)
                    action.onClick()
                },
                text = { Text(action.label) },
                icon = { Icon(action.icon, contentDescription = null) },
                modifier = action.testTag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}
