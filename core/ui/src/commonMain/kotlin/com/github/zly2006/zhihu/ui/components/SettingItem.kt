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

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.icons.AppIcons
import com.github.zly2006.zhihu.icons.Icon
import com.github.zly2006.zhihu.util.ProvideContentColorTextStyle
import kotlinx.coroutines.delay

/**
 * 为设置项提供跳转高亮和滚动定位能力。
 *
 * 账号页、分享弹窗等入口会带着具体 `settingKey` 打开设置页；目标设置项通过这个 modifier 注册位置并播放短暂高亮，
 * 让用户知道刚才跳到的是哪一项。新增可被外部入口直达的设置时，应复用这个机制，而不是手写一次性滚动逻辑。
 */
fun Modifier.highlightSetting(
    settingKey: String?,
    highlightedKey: String,
    onPositioned: ((Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    shape: Shape = RectangleShape,
    color: Color? = null,
): Modifier = composed {
    var modifier = this
    val actualBringIntoViewRequester = remember(settingKey, bringIntoViewRequester) {
        bringIntoViewRequester ?: if (settingKey != null) BringIntoViewRequester() else null
    }

    if (actualBringIntoViewRequester != null) {
        modifier = modifier.bringIntoViewRequester(actualBringIntoViewRequester)
    }

    if (onPositioned != null) {
        modifier = modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInRoot().y.toInt())
        }
    }

    if (settingKey != null) {
        val isTarget = settingKey.isNotEmpty() && settingKey == highlightedKey
        val highlightAlpha = remember { Animatable(0f) }
        val highlightColor = color ?: MaterialTheme.colorScheme.primaryContainer

        LaunchedEffect(isTarget) {
            if (isTarget) {
                actualBringIntoViewRequester?.let {
                    delay(200)
                    it.bringIntoView()
                }
                // 闪烁两次：亮、暗、亮、暗、亮。
                repeat(2) {
                    highlightAlpha.animateTo(0.4f, tween(200, easing = LinearEasing))
                    highlightAlpha.animateTo(0.1f, tween(200, easing = LinearEasing))
                }
                highlightAlpha.animateTo(0.4f, tween(200))
                delay(2000)
                highlightAlpha.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
            } else {
                highlightAlpha.animateTo(0f, tween(500))
            }
        }

        modifier = modifier.drawWithContent {
            drawContent()
            if (highlightAlpha.value > 0f) {
                drawOutline(
                    shape.createOutline(size, layoutDirection, this),
                    highlightColor.copy(alpha = highlightAlpha.value),
                )
            }
        }
    }

    modifier
}

/**
 * 设置页的分组容器。
 *
 * 每组可以包含标题、顶部总控件、底部说明和多个设置项。内部使用自定义 Layout 压缩动画隐藏项之间的间距，
 * 让 `AnimatedVisibility` 展开/收起时不会留下突兀空白。视觉上它是设置页的主要节奏单位，新增设置页时优先用它组织内容。
 * 组按 Material 3 Expressive 分段列表的规格裁出大圆角外轮廓，组内各项之间留出分段间隙。
 */
@Composable
fun SettingItemGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .highlightSetting(
                settingKey = settingKey,
                highlightedKey = highlightedKey,
                onPositioned = onPositioned,
                bringIntoViewRequester = bringIntoViewRequester,
            ).padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .then(modifier),
    ) {
        title?.let {
            Text(
                text = title,
                modifier = Modifier.padding(8.dp, 0.dp, 8.dp, 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        header?.let { it() }

        Layout(
            content = content,
            modifier = modifier.clip(MaterialTheme.shapes.large),
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val spacing = ListItemDefaults.SegmentedGap.roundToPx()
            val baseItemHeight = 48.dp.toPx()

            var yPosition = 0
            val positions = mutableListOf<Int>()
            var hasVisibleBefore = false

            for (i in placeables.indices) {
                val placeable = placeables[i]
                val scale = (placeable.height / baseItemHeight).coerceIn(0f, 1f)

                // 非首个可见元素前按比例补间距；元素缩小时，它前面的间距也同步收缩。
                if (hasVisibleBefore && scale > 0f) {
                    yPosition += (spacing * scale).toInt()
                }

                positions.add(yPosition)
                yPosition += placeable.height

                if (scale > 0f) {
                    hasVisibleBefore = true
                }
            }

            layout(constraints.maxWidth, yPosition) {
                for (i in placeables.indices) {
                    placeables[i].placeRelative(0, positions[i])
                }
            }
        }

        footer?.let {
            Column(
                modifier = Modifier.padding(8.dp, 16.dp, 8.dp, 8.dp),
            ) {
                ProvideContentColorTextStyle(
                    textStyle = MaterialTheme.typography.bodyMedium,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    it()
                }
            }
        }
    }
}

/**
 * 分组顶部的强调型总开关。
 *
 * 适合“启用全部”“开发者模式”这类会影响一组子设置的主控制。它用更强的背景色和圆角药丸形态区别于普通行，
 * 点击整行即可切换，具体批量写入哪些 preference key 由调用方负责。
 */
@Composable
fun SettingItemOverall(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    SettingRow(
        modifier = modifier.padding(bottom = 16.dp),
        title = title,
        description = description,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        endAction = {
            SwitchWithIcon(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        bottomAction = null,
        enabled = enabled,
        settingKey = settingKey,
        highlightedKey = highlightedKey,
        onPositioned = onPositioned,
        bringIntoViewRequester = bringIntoViewRequester,
        checked = checked,
        shapes = ListItemDefaults.shapes(shape = CircleShape),
        colors = ListItemDefaults.segmentedColors(
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            supportingContentColor = colorScheme.onPrimaryContainer,
            trailingContentColor = colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        iconContentColor = colorScheme.onPrimaryContainer,
    )
}

/**
 * 标准开关设置行。
 *
 * 标题、说明、可选图标和右侧带图标 Switch 组成一行，整行点击与直接点开关等价。大多数布尔设置都应使用这个组件，
 * 以保持触控区域、禁用态、高亮跳转和测试定位行为一致。
 */
@Composable
fun SettingItemWithSwitch(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    SettingRow(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        endAction = {
            SwitchWithIcon(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
        },
        bottomAction = null,
        enabled = enabled,
        settingKey = settingKey,
        highlightedKey = highlightedKey,
        onPositioned = onPositioned,
        bringIntoViewRequester = bringIntoViewRequester,
        checked = checked,
    )
}

/**
 * 带选中/未选中图标的开关。
 *
 * 这是设置页统一使用的 Switch 视觉样式：开启时显示对勾，关闭时显示叉号。调用方通常不直接使用它，而是通过
 * [SettingItemWithSwitch] 或 [SettingItemOverall] 获得完整的设置行语义。
 */
@Composable
fun SwitchWithIcon(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = {
            if (checked) {
                Icon(
                    icon = AppIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                    tint = if (enabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                )
            } else {
                Icon(
                    icon = AppIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize),
                )
            }
        },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
    )
}

/**
 * 设置页的基础行组件。
 *
 * 一行由左侧可选图标、标题/说明、右侧动作和底部扩展内容组成，可表示普通导航入口、数值配置、下拉选择或开关行的底层布局。
 * 它同时接入高亮跳转、禁用态和统一圆角/背景色，是所有设置项视觉一致性的基础。
 * 图标放在色调圆形容器中；有整行动作的行使用 Material 3 Expressive 分段列表项，按下时圆角会形变。
 */
@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    endAction: (@Composable () -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    settingKey: String? = null,
    highlightedKey: String = "",
    onPositioned: ((rootY: Int) -> Unit)? = null,
    bringIntoViewRequester: BringIntoViewRequester? = null,
) {
    SettingRow(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        onClick = onClick,
        endAction = endAction,
        bottomAction = bottomAction,
        enabled = enabled,
        settingKey = settingKey,
        highlightedKey = highlightedKey,
        onPositioned = onPositioned,
        bringIntoViewRequester = bringIntoViewRequester,
    )
}

/**
 * 三种设置行的共同实现。
 *
 * Material 3 Expressive 的列表类型仍是实验 API，只在这里使用，公开的设置行组件不暴露这些类型；
 * 上游 API 变化时只需改这一处，调用方不受影响。
 */
@Composable
private fun SettingRow(
    modifier: Modifier,
    title: @Composable () -> Unit,
    description: (@Composable () -> Unit)?,
    icon: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    endAction: (@Composable () -> Unit)?,
    bottomAction: (@Composable () -> Unit)?,
    enabled: Boolean,
    settingKey: String?,
    highlightedKey: String,
    onPositioned: ((rootY: Int) -> Unit)?,
    bringIntoViewRequester: BringIntoViewRequester?,
    checked: Boolean? = null,
    shapes: ListItemShapes = ListItemDefaults.shapes(),
    colors: ListItemColors = ListItemDefaults.segmentedColors(),
    contentPadding: PaddingValues = ListItemDefaults.ContentPadding,
    iconContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowModifier = modifier
        .highlightSetting(
            settingKey = settingKey,
            highlightedKey = highlightedKey,
            onPositioned = onPositioned,
            bringIntoViewRequester = bringIntoViewRequester,
            shape = shapes.shape,
        ).then(
            if (checked != null) {
                Modifier.semantics {
                    role = Role.Switch
                    toggleableState = ToggleableState(checked)
                }
            } else {
                Modifier
            },
        )
    val leading: (@Composable () -> Unit)? = icon?.let {
        {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides if (enabled) iconContentColor else colorScheme.onSurface.copy(alpha = 0.38f),
                    content = it,
                )
            }
        }
    }
    // 分段列表项默认把尾部当作 labelSmall 辅助文字；设置行尾部放的是数值、开关和跳转箭头，沿用正文字号。
    val trailing: (@Composable () -> Unit)? = endAction?.let {
        { ProvideTextStyle(MaterialTheme.typography.bodyLarge, it) }
    }
    // 底部内容是滑杆、输入框、按钮组等控件，按正文样式而不是说明文字样式显示。
    val bottom: (@Composable () -> Unit)? = bottomAction?.let {
        {
            ProvideContentColorTextStyle(
                contentColor = colors.contentColor(enabled = enabled, selected = false, dragged = false),
                textStyle = MaterialTheme.typography.bodyLarge,
                content = it,
            )
        }
    }
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = rowModifier,
            enabled = enabled,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = if (description == null && bottom == null) {
                null
            } else {
                {
                    Column {
                        description?.invoke()
                        bottom?.invoke()
                    }
                }
            },
            colors = colors,
            contentPadding = contentPadding,
            content = title,
        )
    } else {
        // material3 1.12.0-alpha03 的 SegmentedListItem 只有可点击、单选和多选重载，没有整行动作的行（滑杆、输入框等）
        // 不能用它；旧版 ListItem 会合并子节点语义并使用另一套间距，因此按分段列表项的 token 排出同样的一行：
        // 最小高度 56dp（ItemOneLineContainerHeight），图标、文字与尾部之间 12dp（ItemBetweenSpace）。
        // 底部控件横跨整行宽度，不挤在文字列里。
        Column(
            rowModifier
                .background(colors.containerColor(enabled = enabled, selected = false, dragged = false), shapes.shape)
                .heightIn(min = 56.dp)
                .padding(contentPadding),
        ) {
            Row(verticalAlignment = ListItemDefaults.verticalAlignment()) {
                leading?.let { Box(Modifier.padding(end = 12.dp)) { it() } }
                Column(Modifier.weight(1f)) {
                    ProvideContentColorTextStyle(
                        contentColor = colors.contentColor(enabled = enabled, selected = false, dragged = false),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        content = title,
                    )
                    description?.let {
                        ProvideContentColorTextStyle(
                            contentColor = colors.supportingContentColor(enabled = enabled, selected = false, dragged = false),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            content = it,
                        )
                    }
                }
                trailing?.let {
                    Box(Modifier.padding(start = 12.dp)) {
                        CompositionLocalProvider(
                            LocalContentColor provides colors.trailingContentColor(enabled = enabled, selected = false, dragged = false),
                            content = it,
                        )
                    }
                }
            }
            bottom?.invoke()
        }
    }
}
