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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.glass.GlassBackdrop
import com.github.zly2006.zhihu.glass.LiquidGlassDefaults
import com.github.zly2006.zhihu.glass.liquidGlass

/**
 * 页面底部居中的浮动操作栏（Material 3 Expressive floating toolbar），避开导航栏，并与屏幕底边留出规范间距。
 *
 * 只放当前页面内容的动作，不超过五六个；滚动时隐藏由调用方控制。传入 [glassBackdrop] 时操作栏改为液态玻璃，
 * 采样调用方录制的页面内容。
 */
@Composable
fun BottomFloatingToolbar(
    modifier: Modifier = Modifier,
    glassBackdrop: GlassBackdrop? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
        contentAlignment = Alignment.Center,
    ) {
        if (glassBackdrop == null) {
            HorizontalFloatingToolbar(expanded = true, content = content)
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier.liquidGlass(glassBackdrop, LiquidGlassDefaults.surfaceColor),
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = Color.Transparent),
                shape = CircleShape,
                expandedShadowElevation = 0.dp,
                content = content,
            )
        }
    }
}

/**
 * 操作栏和列表项里的开关按钮（赞同、点赞、收藏）：默认不绘制独立底色，选中时用 [checkedContentColor] 和填充图标区分状态。
 *
 * [label] 为 null 时是 48dp 的图标开关；带 [label]（如赞同数）时是图标加文字的开关按钮。
 */
@Composable
fun ActionToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    checkedContainerColor: Color = Color.Transparent,
    checkedContentColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (label == null) {
        IconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = modifier,
            enabled = enabled,
            colors = IconButtonDefaults.iconToggleButtonColors(
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            ),
        ) {
            icon()
        }
    } else {
        ToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = ToggleButtonDefaults.toggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            ),
            elevation = null,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            icon()
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(label)
        }
    }
}
