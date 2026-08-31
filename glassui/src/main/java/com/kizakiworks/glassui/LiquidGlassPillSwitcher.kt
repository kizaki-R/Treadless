package com.kizakiworks.glassui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

private const val LIFT_HOLD_MS = 160L

/** 標籤自適應字級的下限（sp）。再小就算塞得下也讀不清楚了。 */
private const val MIN_LABEL_SP = 10f
/**
 * 藥丸寬度佔一格的比例。
 *
 * 只有一顆藥丸（滑動的選中指示），所以不必替相鄰格留縫，儘量吃滿格寬。
 * 0.82 是兩三格時調的：格子寬、藥丸自然是橫的橢圓；但六格時格寬只剩約 1/6，
 * 0.82 之後寬高幾乎相等，藥丸退化成圓形、字擠在裡面（實機截圖抓到）。
 */
private const val PILL_WIDTH_RATIO = 0.94f

/**
 * 藥丸高度佔軌道的比例。留一點上下呼吸縫讓它是**橫的橢圓**而不是圓；
 * 0.84 被使用者嫌「明顯變矮」，0.92 是縫還在但不顯矮的平衡點。
 * 改這個要連 [PILL_LENS_SHADER] 的 uHalf.y 一起改，否則透鏡範圍會比藥丸高，
 * 藥丸外的字會被莫名放大。
 */
private const val PILL_HEIGHT_RATIO = 0.92f
private const val LIFT_SCALE = 0.18f

/**
 * 液態玻璃藥丸切換器（文字標籤版）——
 * 給模式切換條這類場合用。
 *
 * 互動狀態機（單一 pointerInput 全包，選項本身不掛 clickable）：
 * - 快速點放 → 一般切換（前緣快後緣慢的液態滑移）。
 * - 按住超過 [LIFT_HOLD_MS] 或滑動超過 touchSlop → 藥丸「抬起」：放大、陰影加重、
 *   透鏡色散全開，1:1 跟著手指滑；跨格時觸覺 tick。
 * - 放開 → 吸附最近選項並切換，藥丸以前硬後軟雙彈簧落回（果凍感）。
 *
 * 效能鐵則同導覽列：動畫值（leftEdge/rightEdge/lift）只准在 graphicsLayer 讀；
 * 拖曳跟手用逐幀迴圈＋snapTo，**禁止每事件重啟彈簧**（120Hz 實機會凍住，修過別改回去）。
 *
 * 搬動時若調參數（彈簧、LIFT 系列常數），導覽列那份也要一起看——兩邊要同手感。
 */
@Composable
fun LiquidGlassPillSwitcher(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current

        val barWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = barWidthPx / items.size
        val pillWidthPx = itemWidthPx * PILL_WIDTH_RATIO
        fun slotCenter(index: Int) = itemWidthPx * index + itemWidthPx / 2f
        fun indexAt(x: Float) = (x / itemWidthPx).toInt().coerceIn(0, items.size - 1)
        val minCenter = pillWidthPx / 2f
        val maxCenter = barWidthPx - pillWidthPx / 2f

        // 標籤字級自適應：格子等寬，最寬的標籤塞不下就整排一起縮，永遠單行。
        // 折行是錯的方向——實機截圖抓到 "95%" 被折成 "95%" ＋ 孤立的 "%"，
        // 中途斷字比字小難看得多；整排同字級也比每格大小不一整齊。
        // 【雷】別用 BasicText(autoSize = …)：那是 Compose 1.8 才有的 API，
        // 本專案在 BOM 2024.12.01（1.7.x）上會 NoSuchMethodError（FlowRow 踩過同一種雷）。
        val textMeasurer = rememberTextMeasurer()
        val baseStyle = MaterialTheme.typography.titleSmall
        val labelStyle = remember(items, itemWidthPx, baseStyle, density) {
            val avail = itemWidthPx - with(density) { 10.dp.toPx() }
            // 用粗體量：選中那格是粗體，最寬的情況要先算進去，不然選中就爆版
            val probe = baseStyle.copy(fontWeight = FontWeight.Bold)
            var size = baseStyle.fontSize
            while (size.value > MIN_LABEL_SP) {
                val widest = items.maxOfOrNull { label ->
                    textMeasurer.measure(
                        AnnotatedString(label),
                        probe.copy(fontSize = size),
                    ).size.width
                } ?: 0
                if (widest <= avail) break
                size = (size.value - 1f).sp
            }
            baseStyle.copy(fontSize = size)
        }

        val leftEdge = remember { Animatable(slotCenter(selectedIndex) - pillWidthPx / 2f) }
        val rightEdge = remember { Animatable(slotCenter(selectedIndex) + pillWidthPx / 2f) }
        var dragging by remember { mutableStateOf(false) }
        var pressedIndex by remember { mutableIntStateOf(-1) }
        // 手指目標中心：手勢層只寫這個值，實際移動由下面的逐幀追蹤迴圈消化
        var dragCenter by remember { mutableFloatStateOf(slotCenter(selectedIndex)) }
        val lift = animateFloatAsState(
            targetValue = if (dragging) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 480f),
            label = "switcher-pill-lift",
        )

        // 非拖曳：跟隨 selectedIndex 液態滑移（前緣硬彈簧先衝、後緣軟彈簧拖行 → 拉伸回彈）。
        // dragging 也是 key：放開手指（含落回原選項）由這裡統一收攏，別再另外開 settle 動畫互搶。
        LaunchedEffect(selectedIndex, dragging, itemWidthPx) {
            if (dragging) return@LaunchedEffect
            val targetLeft = slotCenter(selectedIndex) - pillWidthPx / 2f
            val targetRight = targetLeft + pillWidthPx
            val movingRight = targetLeft > leftEdge.value
            val lead = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 340f)
            launch { leftEdge.animateTo(targetLeft, if (movingRight) trail else lead) }
            launch { rightEdge.animateTo(targetRight, if (movingRight) lead else trail) }
        }

        // 拖曳中：單一逐幀追蹤迴圈——前緣快速率、後緣慢速率指數逼近手指，
        // 快滑自然拉長、停手自然收攏（果凍感），且每幀只 snapTo 一次。
        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            var lastNanos = withFrameNanos { it }
            while (dragging) {
                var newLeft = leftEdge.value
                var newRight = rightEdge.value
                withFrameNanos { now ->
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    lastNanos = now
                    val targetLeft = dragCenter - pillWidthPx / 2f
                    val targetRight = dragCenter + pillWidthPx / 2f
                    val movingRight = targetLeft + targetRight > leftEdge.value + rightEdge.value
                    val fast = 1f - exp(-dt * 42f)
                    val slow = 1f - exp(-dt * 16f)
                    newLeft = leftEdge.value +
                        (targetLeft - leftEdge.value) * (if (movingRight) slow else fast)
                    newRight = rightEdge.value +
                        (targetRight - rightEdge.value) * (if (movingRight) fast else slow)
                }
                leftEdge.snapTo(newLeft)
                rightEdge.snapTo(newRight)
            }
        }

        // ---- 藥丸（半透明主色漸層＋白高光邊；靜止不投影，抬起才有浮起影）----
        val primary = MaterialTheme.colorScheme.primary
        val pillShape = RoundedCornerShape(percent = 50)
        val pillBrush = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.10f))
                } else {
                    listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.13f))
                },
            )
        }
        // 描邊仍是上亮下淡的高光語彙，但下緣不能淡到消失——底邊看不見時
        // 眼睛會把藥丸讀成「偏上、沒置中」（使用者抓過；幾何其實是置中的）
        val pillBorder = remember(isDark, primary) {
            Brush.verticalGradient(
                colors = if (isDark) {
                    listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.14f))
                } else {
                    listOf(Color.White.copy(alpha = 0.90f), primary.copy(alpha = 0.32f))
                },
            )
        }
        val liftGlowColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.28f)
        val pillWidthDp = with(density) { pillWidthPx.toDp() }
        val shadowSpot = Color.Black.copy(alpha = 0.30f)
        val shadowAmbient = Color.Black.copy(alpha = 0.10f)

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(pillWidthDp)
                .fillMaxHeight(PILL_HEIGHT_RATIO)
                .graphicsLayer {
                    val l = leftEdge.value
                    val r = rightEdge.value
                    val liftValue = lift.value
                    translationX = (l + r) / 2f - pillWidthPx / 2f
                    val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                    val liftScale = 1f + LIFT_SCALE * liftValue
                    scaleX = stretch * liftScale
                    scaleY = liftScale
                    shadowElevation = 14.dp.toPx() * liftValue
                    spotShadowColor = shadowSpot
                    ambientShadowColor = shadowAmbient
                    shape = pillShape
                    clip = true
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pillBrush)
                    .border(1.dp, pillBorder, pillShape),
            )
            // lift 疊層：透過 layer 的 alpha 淡入，唯一的動畫讀值都在繪製階段
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = lift.value }
                    .background(liftGlowColor),
            )
        }

        // ---- 標籤層（Android 13+ 掛藥丸透鏡：文字經過玻璃被放大、邊緣拉伸＋真色散）----
        val lensShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            remember { RuntimeShader(PILL_LENS_SHADER) }
        } else {
            null
        }
        // SDK_INT 條件必須直接寫在這裡：lint 的 NewApi 看不懂「lensShader 非空蘊含 API 33」
        val lensModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && lensShader != null) {
            Modifier.graphicsLayer {
                val l = leftEdge.value
                val r = rightEdge.value
                val liftValue = lift.value
                val liftScale = 1f + LIFT_SCALE * liftValue
                val stretch = ((r - l) / pillWidthPx).coerceIn(0.75f, 1.5f)
                lensShader.setFloatUniform("uCenter", (l + r) / 2f, size.height / 2f)
                lensShader.setFloatUniform(
                    "uHalf",
                    pillWidthPx / 2f * stretch * liftScale,
                    // 跟著藥丸的實際高度，不是整條軌道的高度
                    size.height / 2f * PILL_HEIGHT_RATIO * liftScale,
                )
                // 靜止微放大、抬起放大＋色散全開
                lensShader.setFloatUniform("uZoom", 0.10f + 0.22f * liftValue)
                lensShader.setFloatUniform("uChroma", liftValue)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(lensShader, "content")
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .then(lensModifier),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, label ->
                SwitcherItem(
                    label = label,
                    style = labelStyle,
                    selected = index == selectedIndex,
                    pressed = index == pressedIndex && !dragging,
                    onSelect = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 手勢層（蓋最上面，選項不各自 clickable）----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f)
                .pointerInput(items.size, itemWidthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        val slop = viewConfiguration.touchSlop
                        pressedIndex = indexAt(down.position.x)
                        var lifted = false
                        var lastHovered = -1
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val x = change.position.x
                                if (!lifted &&
                                    (change.uptimeMillis - down.uptimeMillis >= LIFT_HOLD_MS ||
                                        abs(x - down.position.x) > slop)
                                ) {
                                    lifted = true
                                    lastHovered = indexAt(x)
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    dragging = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                if (lifted && change.positionChanged()) {
                                    // 手勢層只寫目標值，移動交給逐幀追蹤迴圈（事件再密也不積工作）
                                    dragCenter = x.coerceIn(minCenter, maxCenter)
                                    val hovered = indexAt(dragCenter)
                                    if (hovered != lastHovered) {
                                        lastHovered = hovered
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    change.consume()
                                }
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (lifted) {
                                        val target = indexAt(dragCenter)
                                        dragging = false
                                        if (target != selectedIndex) onSelect(target)
                                        // 落回原選項：dragging 變 false 就會讓滑移 effect 重跑收攏
                                    } else {
                                        onSelect(indexAt(down.position.x))
                                    }
                                    break
                                }
                            }
                        } finally {
                            pressedIndex = -1
                            // 手勢被取消（例如系統攔走）：不切換，dragging 復位讓 effect 收回原位
                            if (dragging) dragging = false
                        }
                    }
                },
        )
    }
}

@Composable
private fun SwitcherItem(
    label: String,
    style: TextStyle,
    selected: Boolean,
    pressed: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "switcher-item-press-scale",
    )

    // 觸控由上層手勢層獨佔，TalkBack 靠這組語意動作切換
    val isSelected = selected
    Box(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = isSelected
                onClick {
                    onSelect()
                    true
                }
            }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = style,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            // 一律單行：字級已由上層量測縮到塞得下，這裡再 soft wrap 只會中途斷字。
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
