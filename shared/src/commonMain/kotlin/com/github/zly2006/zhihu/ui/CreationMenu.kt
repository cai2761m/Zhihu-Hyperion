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

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.navigation.NavDestination
import com.github.zly2006.zhihu.navigation.WritePin
import com.github.zly2006.zhihu.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.ui.components.FabMenuAction

const val HOME_CREATE_TOP_BUTTON_TAG = "home_create_top_button"
const val MAIN_CREATE_NAV_BUTTON_TAG = "main_create_nav_button"

/** 三种发布入口共享动作与导航，入口位置不改变已有创作能力。 */
@Composable
internal fun creationMenuActions(onNavigate: (NavDestination) -> Unit): List<FabMenuAction> {
    val userMessages = rememberUserMessageSink()
    return listOf(
        FabMenuAction("提问题", AppIcons.Help, HOME_WRITE_QUESTION_BUTTON_TAG) { userMessages.showShortMessage("正在施工") },
        FabMenuAction("写回答", AppIcons.Edit, HOME_WRITE_ANSWER_BUTTON_TAG) { userMessages.showShortMessage("正在施工") },
        FabMenuAction("发想法", AppIcons.AddComment, HOME_WRITE_PIN_BUTTON_TAG) { onNavigate(WritePin()) },
    )
}

@Composable
internal fun CreationDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNavigate: (NavDestination) -> Unit,
    offset: DpOffset = DpOffset.Zero,
) {
    val actions = creationMenuActions(onNavigate)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, contentDescription = null) },
                modifier = Modifier.testTag(requireNotNull(action.testTag)),
                onClick = {
                    onDismiss()
                    action.onClick()
                },
            )
        }
    }
}

@Composable
internal fun CreationTopBarButton(isActive: Boolean) {
    val navigator = LocalNavigator.current
    var expanded by remember(isActive) { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag(HOME_CREATE_TOP_BUTTON_TAG)
                .clickable(role = Role.Button) { expanded = !expanded },
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Add, contentDescription = "发布")
        }
        CreationDropdownMenu(expanded, { expanded = false }, navigator.onNavigate)
    }
}
