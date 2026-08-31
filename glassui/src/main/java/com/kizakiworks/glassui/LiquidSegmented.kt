package com.kizakiworks.glassui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 液態分段切換共用元件——app 內所有「座標/路線、速度快切、懸浮樣式」類的分段開關
 * 都用這套：選中指示藥丸從舊格「滑」到新格（前緣快後緣慢的雙彈簧，行進中微拉伸），
 * 取代硬切背景色。
 *
 * 用法（三件事）：
 * 1. `val seg = rememberLiquidSegmentState()`，容器內呼叫 `LiquidSegmentAnimation(seg, selectedIndex)`。
 * 2. 容器 Row 的 modifier 鏈上掛 `.liquidSegmentIndicator(seg, fill = ...)`（放在 clip/background 之後、
 *    padding 之前），指示藥丸會畫在所有選項後面。
 * 3. 每個選項 modifier 掛 `.liquidSegmentItem(seg, index)`。選項自己不要再畫選中背景，
 *    文字／邊框色用 animateColorAsState 淡變。
 *
 * 效能鐵則同導覽列藥丸：動畫值只在 drawBehind（繪製階段）讀，動畫幀不重組不排版。
 * 選項寬度不必相等（收藏庫兩格就不等寬），位置靠 onGloballyPositioned 實測。
 */
class LiquidSegmentState internal constructor() {
    internal val bounds = mutableStateMapOf<Int, Rect>()
    internal val leftEdge = Animatable(Float.NaN)
    internal val rightEdge = Animatable(Float.NaN)
    internal val alpha = Animatable(0f)
    internal var top = 0f
    internal var height = 0f

    // 容器（指示繪製那一層）與各選項的實際座標。選項框一律用 localBoundingBoxOf
    // 換算到容器座標系——選項回報的是「padding 之後」的內縮空間、繪製發生在
    // 「padding 之前」的外框空間，直接拿 positionInParent 會差一個內距（踩過，錯位）。
    internal var containerCoords: LayoutCoordinates? = null
    internal val itemCoords = HashMap<Int, LayoutCoordinates>()

    internal fun refresh(index: Int) {
        val container = containerCoords ?: return
        val item = itemCoords[index] ?: return
        if (!container.isAttached || !item.isAttached) return
        val rect = container.localBoundingBoxOf(item, clipBounds = false)
        if (bounds[index] != rect) bounds[index] = rect
    }

    internal fun refreshAll() {
        itemCoords.keys.forEach { refresh(it) }
    }
}

@Composable
fun rememberLiquidSegmentState(): LiquidSegmentState = remember { LiquidSegmentState() }

/**
 * 驅動指示藥丸的動畫。`selectedIndex` 傳 -1（或該格尚未量到位置）時指示淡出，
 * 適合「目前速度不在任何預設上」這種無選中狀態。
 */
@Composable
fun LiquidSegmentAnimation(state: LiquidSegmentState, selectedIndex: Int) {
    val target = state.bounds[selectedIndex]
    LaunchedEffect(selectedIndex, target) {
        if (target == null) {
            state.alpha.animateTo(0f, spring(stiffness = 600f))
            return@LaunchedEffect
        }
        state.top = target.top
        state.height = target.height
        if (state.leftEdge.value.isNaN() || state.alpha.value == 0f) {
            // 第一次出現（或從無選中回來）：直接就位再淡入，不要從外太空滑進來
            state.leftEdge.snapTo(target.left)
            state.rightEdge.snapTo(target.right)
            state.alpha.animateTo(1f, spring(stiffness = 600f))
            return@LaunchedEffect
        }
        val movingRight = target.left > state.leftEdge.value
        val lead = spring<Float>(dampingRatio = 0.68f, stiffness = 900f)
        val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 380f)
        launch { state.alpha.animateTo(1f, spring(stiffness = 600f)) }
        launch { state.leftEdge.animateTo(target.left, if (movingRight) trail else lead) }
        launch { state.rightEdge.animateTo(target.right, if (movingRight) lead else trail) }
    }
}

/** 掛在每個選項上：記下自己的座標，換算交給 [LiquidSegmentState.refresh]。 */
fun Modifier.liquidSegmentItem(state: LiquidSegmentState, index: Int): Modifier =
    onGloballyPositioned { coords ->
        state.itemCoords[index] = coords
        state.refresh(index)
    }

/**
 * 掛在容器上：把指示藥丸畫在所有選項後面。
 * [cornerRadius] 傳 null＝全膠囊（高度一半）。
 */
fun Modifier.liquidSegmentIndicator(
    state: LiquidSegmentState,
    fill: Color,
    cornerRadius: Dp? = null,
    border: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
): Modifier = liquidSegmentIndicatorImpl(
    state, cornerRadius, borderWidth,
    fillColor = fill, borderColor = border, fillBrush = null, borderBrush = null,
)

/**
 * 漸層版指示藥丸——「半透明主色上深下淺＋白色高光描邊」的玻璃藥丸配方
 * 用的就是這個配方。純色版仍走上面的 [liquidSegmentIndicator]。
 */
fun Modifier.liquidSegmentIndicatorBrush(
    state: LiquidSegmentState,
    fill: Brush,
    border: Brush? = null,
    cornerRadius: Dp? = null,
    borderWidth: Dp = 1.dp,
): Modifier = liquidSegmentIndicatorImpl(
    state, cornerRadius, borderWidth,
    fillColor = null, borderColor = null, fillBrush = fill, borderBrush = border,
)

private fun Modifier.liquidSegmentIndicatorImpl(
    state: LiquidSegmentState,
    cornerRadius: Dp?,
    borderWidth: Dp,
    fillColor: Color?,
    borderColor: Color?,
    fillBrush: Brush?,
    borderBrush: Brush?,
): Modifier = onGloballyPositioned { coords ->
    // 在繪製同一個鏈位記座標，指示框與畫布必然同一座標系
    state.containerCoords = coords
    state.refreshAll()
}.drawBehind {
    val a = state.alpha.value
    if (a <= 0.01f) return@drawBehind
    val left = state.leftEdge.value
    val right = state.rightEdge.value
    if (left.isNaN() || right.isNaN() || right <= left) return@drawBehind
    val radiusPx = cornerRadius?.toPx() ?: (state.height / 2f)
    val corner = CornerRadius(radiusPx, radiusPx)
    val topLeft = Offset(left, state.top)
    val size = Size(right - left, state.height)
    if (fillBrush != null) {
        drawRoundRect(brush = fillBrush, topLeft = topLeft, size = size, cornerRadius = corner, alpha = a)
    } else if (fillColor != null) {
        drawRoundRect(
            color = fillColor.copy(alpha = fillColor.alpha * a),
            topLeft = topLeft, size = size, cornerRadius = corner,
        )
    }
    val inset = borderWidth.toPx() / 2f
    val borderTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
    val borderSize = Size(size.width - inset * 2, size.height - inset * 2)
    val borderCorner = CornerRadius(radiusPx - inset, radiusPx - inset)
    if (borderBrush != null) {
        drawRoundRect(
            brush = borderBrush, topLeft = borderTopLeft, size = borderSize,
            cornerRadius = borderCorner, style = Stroke(width = borderWidth.toPx()), alpha = a,
        )
    } else if (borderColor != null && borderColor.alpha > 0f) {
        drawRoundRect(
            color = borderColor.copy(alpha = borderColor.alpha * a),
            topLeft = borderTopLeft, size = borderSize,
            cornerRadius = borderCorner, style = Stroke(width = borderWidth.toPx()),
        )
    }
}
