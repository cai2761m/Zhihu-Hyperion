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

// 来源：androidx.compose.material3:material3:1.5.0-alpha22（JetBrains material3 1.12.0-alpha03）的 ModalBottomSheet。
// 与上游的差异：返回键直接关闭（上游会先收到半展开）、可选不使用平台窗口、macOS 窗口扣除原生侧栏、可关闭圆角。
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "INVISIBLE_SETTER")

package com.github.zly2006.zhihu.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetImpl
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetDialog
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue.Hidden
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.internal.PredictiveBack
import androidx.compose.material3.internal.Strings
import androidx.compose.material3.internal.getString
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.tokens.MotionSchemeKeyTokens
import androidx.compose.material3.value
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.zly2006.zhihu.platform.PlatformPredictiveBackHandler
import com.github.zly2006.zhihu.platform.SettingsStore
import com.github.zly2006.zhihu.platform.exportTestTagsForUiAutomation
import com.github.zly2006.zhihu.platform.platformName
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

const val DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY = "disableBottomSheetRoundedCorners"

@Composable
@ExperimentalMaterial3Api
fun MyModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // The deprecated factory keeps the legacy rule that drops PartiallyExpanded for sheets shorter than half the
    // screen; rememberBottomSheetState has no equivalent, so callers relying on it keep this default.
    @Suppress("DEPRECATION") sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    usePlatformWindow: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val settings = koinInject<SettingsStore>()
    val bottomSheetShape = if (settings.getBoolean(DISABLE_BOTTOM_SHEET_ROUNDED_CORNERS_PREFERENCE_KEY, false)) {
        RectangleShape
    } else {
        shape
    }
    val showMotion = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val hideMotion = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    // BottomSheetImpl 不负责初始化过渡规格，SheetState 默认的 show/hide 规格为 snap。
    SideEffect {
        sheetState.showMotionSpec = showMotion
        sheetState.hideMotionSpec = hideMotion
    }
    val scope = rememberCoroutineScope()
    val animateToDismiss: () -> Unit = {
        // K2 cannot resolve the implicit invoke operator on this internal property; the explicit call compiles.
        if (sheetState.confirmValueChange.invoke(Hidden)) {
            scope
                .launch { sheetState.hide() }
                .invokeOnCompletion {
                    if (!sheetState.isVisible) {
                        onDismissRequest()
                    }
                }
        }
    }
    val predictiveBackProgress = remember { Animatable(initialValue = 0f) }

    @Composable
    fun SheetContent() {
        // Back dismisses the sheet directly instead of collapsing it to PartiallyExpanded first.
        PlatformPredictiveBackHandler(
            enabled = properties.shouldDismissOnBackPress && sheetState.targetValue != Hidden,
            onProgress = { progress ->
                scope.launch { predictiveBackProgress.snapTo(PredictiveBack.transform(progress)) }
            },
            onCancel = {
                scope.launch { predictiveBackProgress.animateTo(0f) }
            },
            onBack = animateToDismiss,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .semantics {
                    isTraversalGroup = true
                }.exportTestTagsForUiAutomation(),
        ) {
            MyBottomSheetScrim(
                color = scrimColor,
                onDismissRequest = animateToDismiss,
                visible = sheetState.targetValue != Hidden,
                dismissEnabled = properties.shouldDismissOnClickOutside,
            )
            BottomSheetImpl(
                predictiveBackProgress = predictiveBackProgress.value,
                modifier = modifier.align(Alignment.TopCenter),
                state = sheetState,
                onDismissRequest = onDismissRequest,
                maxWidth = sheetMaxWidth,
                gesturesEnabled = sheetGesturesEnabled,
                shape = bottomSheetShape,
                containerColor = containerColor,
                contentColor = contentColor,
                tonalElevation = tonalElevation,
                dragHandle = dragHandle,
                contentWindowInsets = contentWindowInsets,
                content = content,
            )
        }
    }

    if (usePlatformWindow) {
        val dismissFromWindow: () -> Unit = {
            scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
        }
        if (platformName == "macOS") {
            MacosModalBottomSheetDialog(
                properties = properties,
                contentColor = contentColor,
                onDismissRequest = dismissFromWindow,
                content = { SheetContent() },
            )
        } else {
            ModalBottomSheetDialog(
                properties = properties,
                contentColor = contentColor,
                onDismissRequest = dismissFromWindow,
                content = { SheetContent() },
            )
        }
    } else {
        SheetContent()
    }
    if (sheetState.hasExpandedState) {
        LaunchedEffect(sheetState) { sheetState.show() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal expect fun MacosModalBottomSheetDialog(
    onDismissRequest: () -> Unit,
    contentColor: Color,
    properties: ModalBottomSheetProperties,
    content: @Composable () -> Unit,
)

@Composable
private fun MyBottomSheetScrim(
    color: Color,
    onDismissRequest: () -> Unit,
    visible: Boolean,
    dismissEnabled: Boolean,
) {
    if (color.isSpecified) {
        val alpha by
            animateFloatAsState(
                targetValue = if (visible) 1f else 0f,
                animationSpec = MotionSchemeKeyTokens.SlowEffects.value(),
                label = "ScrimAlphaAnimation",
            )
        val closeSheet = getString(Strings.CloseSheet)
        val dismissSheet =
            if (dismissEnabled) {
                Modifier
                    .pointerInput(onDismissRequest) { detectTapGestures { onDismissRequest() } }
                    .semantics(mergeDescendants = true) {
                        traversalIndex = 1f
                        contentDescription = closeSheet
                        onClick {
                            onDismissRequest()
                            true
                        }
                    }
            } else {
                Modifier
            }
        Canvas(Modifier.fillMaxSize().then(dismissSheet)) {
            drawRect(color = color, alpha = alpha.coerceIn(0f, 1f))
        }
    }
}
