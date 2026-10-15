/*
 * Copyright 2025 Kyant
 * Copyright 2026 compose-miuix-ui contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from LiquidBottomTabs in the Kyant0/AndroidLiquidGlass catalog (https://github.com/Kyant0/AndroidLiquidGlass)
 * and IosLiquidGlassNavigationBar in the compose-miuix-ui example (https://github.com/compose-miuix-ui/miuix).
 * Modified for Zhihu-Hyperion: declarative tab model, Material 3 colors and typography, reselect callback,
 * test tags and accessibility actions.
 */

package com.github.zly2006.zhihu.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 液态玻璃底栏里的一项；[isNavigation] 为 false 时只执行动作，保留当前页面和选中指示器。 */
@Immutable
class LiquidGlassTab(
    val label: String,
    val testTag: String,
    val icon: @Composable (selected: Boolean) -> Unit,
    val onClick: () -> Unit,
    val isNavigation: Boolean = true,
)

private val LocalTabScale = staticCompositionLocalOf { { 1f } }

/**
 * 悬浮在内容上方的胶囊形液态玻璃底栏，采样 [backdrop] 里录制的页面内容。
 *
 * 点按或拖动底栏任意位置，玻璃指示器都会跟手移动，松手后选中最近的一项；原地点按已选中的一项同样调用它的
 * [LiquidGlassTab.onClick]，调用方据此回到顶部或刷新。
 */
@Composable
fun LiquidGlassTabBar(
    tabs: List<LiquidGlassTab>,
    selectedIndex: Int,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val tabCount = tabs.size
    if (tabCount == 0) return
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = LiquidGlassDefaults.surfaceColor
    val isLightTheme = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val tabsBackdrop = rememberLayerBackdrop()
    val indicatorBackdrop = rememberCombinedBackdrop(backdrop.layer, tabsBackdrop)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val animationScope = rememberCoroutineScope()
    val currentTabs by rememberUpdatedState(tabs)

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }
    var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(0, tabCount - 1)) }

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - with(density) { 4.dp.toPx() }) / tabWidthPx).toInt().coerceIn(0, tabCount - 1)
    }

    fun activate(index: Int) {
        if (currentTabs[index].isNavigation) currentIndex = index
        currentTabs[index].onClick()
    }

    val dampedDrag = remember(animationScope, tabCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position -> position.x in 0f..totalWidthPx },
            onDragStarted = { position -> updateValue(indexAt(position.x).toFloat()) },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabCount - 1)
                if (targetIndex != currentIndex || gestureDistance < touchSlop) activate(targetIndex)
                updateValue(currentIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { dragAmount ->
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    val direction = if (isLtr) 1f else -1f
                    updateValue((targetValue + dragAmount.x / tabWidthPx * direction).coerceIn(0f, (tabCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            },
        )
    }

    LaunchedEffect(selectedIndex, dampedDrag) {
        val index = selectedIndex.coerceIn(0, tabCount - 1)
        if (currentIndex != index) {
            currentIndex = index
            dampedDrag.animateToValue(index.toFloat())
        }
    }

    val interactiveHighlight = remember(animationScope, isLtr, dampedDrag) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                val center = (dampedDrag.value + 0.5f) * tabWidthPx
                Offset(
                    x = (if (isLtr) center else layerSize.width - center) + panelOffset,
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    val bottomSpacing = if (tabBarSitsInHomeIndicatorArea) {
        // 与系统标签栏一致，压进 Home 指示条的安全区，而不是整体抬到安全区之上。
        Modifier.padding(bottom = 20.dp)
    } else {
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(bottom = 8.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .then(bottomSpacing)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .onSizeChanged { size ->
                        totalWidthPx = size.width.toFloat()
                        tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / tabCount).coerceAtLeast(0f)
                    }.graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop.layer,
                        shape = { CircleShape },
                        effects = {
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx(), 24.dp.toPx())
                        },
                        layerBlock = {
                            val scale = lerp(1f, 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f), dampedDrag.pressProgress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(surfaceColor) },
                    ).then(interactiveHighlight.modifier)
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDrag.modifier)
                    .height(64.dp)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    TabContent(
                        tab = tab,
                        selected = tab.isNavigation && index == currentIndex,
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {
                                if (tab.isNavigation) selected = index == currentIndex
                                role = if (tab.isNavigation) Role.Tab else Role.Button
                                onClick(label = tab.label) {
                                    activate(index)
                                    dampedDrag.animateToValue(currentIndex.toFloat())
                                    true
                                }
                            }.testTag(tab.testTag),
                    )
                }
            }
        }

        // 不可见的着色副本：录进 tabsBackdrop 后只在指示器的透镜里露出，选中项因此呈现主题色并随按压放大。
        CompositionLocalProvider(
            LocalTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
            LocalContentColor provides contentColor,
        ) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop.layer,
                        shape = { CircleShape },
                        effects = {
                            val progress = dampedDrag.pressProgress
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(24.dp.toPx() * progress, 24.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = dampedDrag.pressProgress) },
                        onDrawSurface = { drawRect(surfaceColor) },
                    ).then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab -> TabContent(tab = tab, selected = true) }
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    val offset = dampedDrag.value * tabWidthPx
                    translationX = (if (isLtr) offset else -offset) + panelOffset
                }.drawBackdrop(
                    backdrop = indicatorBackdrop,
                    shape = { CircleShape },
                    effects = {
                        val progress = dampedDrag.pressProgress
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDrag.pressProgress) },
                    shadow = { Shadow(alpha = dampedDrag.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * dampedDrag.pressProgress, alpha = dampedDrag.pressProgress) },
                    layerBlock = {
                        val velocity = dampedDrag.velocity / 10f
                        scaleX = dampedDrag.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                        scaleY = dampedDrag.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = {
                        val progress = dampedDrag.pressProgress
                        drawRect(if (isLightTheme) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ).height(56.dp)
                .fillMaxWidth(1f / tabCount),
        )
    }
}

@Composable
private fun RowScope.TabContent(
    tab: LiquidGlassTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale = LocalTabScale.current
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .graphicsLayer {
                val tabScale = scale()
                scaleX = tabScale
                scaleY = tabScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        tab.icon(selected)
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
