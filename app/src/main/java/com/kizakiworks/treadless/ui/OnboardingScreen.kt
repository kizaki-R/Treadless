package com.kizakiworks.treadless.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import com.kizakiworks.glassui.LiquidGlassPillSwitcher
import com.kizakiworks.treadless.R
import com.kizakiworks.stepcore.AppLocale
import com.kizakiworks.stepcore.HealthConnectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首次啟動導覽：五頁左右滑動——歡迎、模式介紹、Health Connect 授權、通知、電池設定。
 *
 * 權限頁不是「文字叫你去同意」：各放一張**會動的示意卡**——HC 頁演示開關被撥開、
 * 電池頁演示選取從「最佳化」移到「不受限制」——使用者到了系統頁面照著做就好。
 * 兩頁的完成狀態都以 2 秒輪詢真實偵測（授權完回來自動變綠勾），不用自己回報。
 *
 * 視覺沿用主畫面語彙：湖水綠主色、白卡 22dp 圓角、膠囊、晶片。
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    var sdkStatus by remember { mutableIntStateOf(HealthConnectManager.sdkStatus(context)) }
    var hasHc by remember { mutableStateOf(false) }
    var notifOk by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryOk by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifOk = granted }
    LaunchedEffect(Unit) {
        while (true) {
            sdkStatus = HealthConnectManager.sdkStatus(context)
            hasHc = HealthConnectManager.hasStepsPermission(context)
            notifOk = hasNotificationPermission(context)
            batteryOk = isBatteryUnrestricted(context)
            delay(2000)
        }
    }

    fun next() = scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        // 略過：最後一頁不顯示（那裡的完成鈕就是出口）
        if (pagerState.currentPage < 4) {
            TextButton(
                onClick = onFinish,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text(stringResource(R.string.action_skip), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { page ->
                // 滑動視差：離開中的頁面淡出並輕微縮小，滑起來比硬切高級
                val pageOffset =
                    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = 1f - 0.4f * pageOffset
                            val s = 1f - 0.04f * pageOffset
                            scaleX = s
                            scaleY = s
                        }
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    when (page) {
                        0 -> WelcomePage()
                        1 -> ModesPage()
                        2 -> HealthConnectPage(granted = hasHc)
                        3 -> NotificationPage(granted = notifOk)
                        4 -> BatteryPage(done = batteryOk)
                    }
                    Spacer(modifier = Modifier.weight(1.3f))
                }
            }

            PagerDots(
                count = 5,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            // 底部動作區固定高度：主按鈕＋次要文字鈕，換頁時版面不跳
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 16.dp)
                    .height(112.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val page = pagerState.currentPage
                val primaryLabel: String
                val primaryAction: () -> Unit
                var secondaryLabel: String? = null
                var secondaryAction: () -> Unit = {}
                when {
                    page <= 1 -> {
                        primaryLabel = stringResource(R.string.action_continue)
                        primaryAction = { next() }
                    }
                    page == 2 && sdkStatus != HealthConnectClient.SDK_AVAILABLE -> {
                        primaryLabel = stringResource(R.string.btn_install_hc)
                        primaryAction = { HealthConnectManager.openInstall(context) }
                        secondaryLabel = stringResource(R.string.action_later)
                        secondaryAction = { next() }
                    }
                    page == 2 && !hasHc -> {
                        primaryLabel = stringResource(R.string.ob_hc_grant_btn)
                        primaryAction = { HealthConnectManager.openSettings(context) }
                        secondaryLabel = stringResource(R.string.action_later)
                        secondaryAction = { next() }
                    }
                    page == 2 -> {
                        primaryLabel = stringResource(R.string.action_continue)
                        primaryAction = { next() }
                    }
                    page == 3 && !notifOk -> {
                        primaryLabel = stringResource(R.string.ob_notif_allow)
                        primaryAction = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings(context)
                            }
                        }
                        secondaryLabel = stringResource(R.string.action_later)
                        secondaryAction = { next() }
                    }
                    page == 3 -> {
                        primaryLabel = stringResource(R.string.action_continue)
                        primaryAction = { next() }
                    }
                    !batteryOk -> {
                        primaryLabel = stringResource(R.string.ob_battery_btn)
                        primaryAction = { requestBatteryExemption(context) }
                        secondaryLabel = stringResource(R.string.ob_start_anyway)
                        secondaryAction = onFinish
                    }
                    else -> {
                        primaryLabel = stringResource(R.string.ob_start)
                        primaryAction = onFinish
                    }
                }
                Button(
                    onClick = primaryAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                secondaryLabel?.let { label ->
                    TextButton(onClick = secondaryAction) {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

// --- 第 1 頁：歡迎 ---

@Composable
private fun WelcomePage() {
    BrandRing()
    Spacer(modifier = Modifier.height(36.dp))
    Text(
        text = "Treadless",
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.ob_welcome_sub),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        lineHeight = 26.sp,
    )
    Spacer(modifier = Modifier.height(30.dp))
    LanguagePicker()
}

/**
 * 第一頁就能選介面語言——使用者第一眼看到的字就該是他讀得懂的，
 * 不該先滑完五頁導覽、進主畫面才找得到「文A」鍵。
 *
 * 兩個選項各用自己的語言標示（繁體中文／English），所以兩份資源同值、
 * 不隨目前語言變動；選了就存 prefs 並 recreate，導覽還沒走完
 * （onboarding_done 仍是 false），重建後照樣回到這一頁，只是換了語言。
 */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val codes = listOf(AppLocale.ZH, AppLocale.EN)
    val current = AppLocale.get(context)
    var index by remember { mutableIntStateOf(codes.indexOf(current).coerceAtLeast(0)) }
    var pending by remember { mutableStateOf<String?>(null) }
    // 【雷】recreate() 不能在 onSelect 當場呼叫：整棵樹會立刻被拆掉，
    // 藥丸的彈簧一幀都跑不到，看起來就是硬切。先讓它滑完再重建。
    // 400ms＝後緣彈簧（damping 0.85 / stiffness 340）落定時間再留點餘裕。
    LaunchedEffect(pending) {
        val lang = pending ?: return@LaunchedEffect
        delay(400)
        AppLocale.set(context, lang)
        (context as? Activity)?.recreate()
    }
    Box(
        modifier = Modifier
            .width(236.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(percent = 50))
            // 軌道底同分組切換鍵：淺色 0.06（onSurface 是藍板岩近黑，濃了會發灰搶戲）
            .background(
                MaterialTheme.colorScheme.onSurface
                    .copy(alpha = if (isSystemInDarkTheme()) 0.10f else 0.06f),
            ),
    ) {
        LiquidGlassPillSwitcher(
            items = listOf(stringResource(R.string.lang_zh), stringResource(R.string.lang_en)),
            selectedIndex = index,
            onSelect = { picked ->
                // 藥丸先滑過去（本地 index），語言寫入與 recreate 交給上面的 LaunchedEffect
                if (pending == null && codes[picked] != current) {
                    index = picked
                    pending = codes[picked]
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 品牌圓環（同 App 圖示的 O）：白環＋一橘三黃的點，點依序輕輕脈動。 */
@Composable
private fun BrandRing() {
    val t = rememberInfiniteTransition(label = "brandRing")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2800)),
        label = "dotPhase",
    )
    val primary = MaterialTheme.colorScheme.primary
    val dotColors = listOf(
        Color(0xFFF07850), // 橘
        Color(0xFFF5B840),
        Color(0xFFF5B840),
        Color(0xFFF5B840),
    )
    Canvas(modifier = Modifier.size(170.dp)) {
        val c = center
        val outer = size.minDimension / 2f
        // 淡主色底盤，讓白環在淺色背景上浮起來
        drawCircle(color = primary.copy(alpha = 0.15f), radius = outer, center = c)
        val ringR = outer * 0.62f
        drawCircle(
            color = Color.White,
            radius = ringR,
            center = c,
            style = Stroke(width = outer * 0.30f),
        )
        // 四顆點沿環右上弧排列（同圖示構圖），依序脈動放大
        val angles = listOf(-55f, -25f, 5f, 35f)
        angles.forEachIndexed { i, deg ->
            val rad = Math.toRadians(deg.toDouble())
            val pos = androidx.compose.ui.geometry.Offset(
                x = c.x + ringR * cos(rad).toFloat(),
                y = c.y + ringR * sin(rad).toFloat(),
            )
            // 每顆點輪流被 phase 掃過時放大 25%
            val d = (phase - i).let { if (it < 0) it + 4f else it }
            val pulse = if (d < 1f) 1f + 0.25f * sin(Math.PI * d).toFloat() else 1f
            drawCircle(color = dotColors[i], radius = outer * 0.085f * pulse, center = pos)
        }
    }
}

// --- 第 2 頁：兩種模式 ---

@Composable
private fun ModesPage() {
    MiniModeBar()
    Spacer(modifier = Modifier.height(30.dp))
    Text(
        text = stringResource(R.string.ob_modes_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(16.dp))
    OnboardCard {
        Text(
            text = stringResource(R.string.ob_mode_auto),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.ob_mode_auto_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    OnboardCard {
        Text(
            text = stringResource(R.string.ob_mode_manual),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.ob_mode_manual_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

/** 迷你模式條：藥丸在「自動／手動」之間來回滑動，預告主畫面的核心操作。 */
@Composable
private fun MiniModeBar() {
    val t = rememberInfiniteTransition(label = "miniBar")
    val sel by t.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3600
                0f at 0
                0f at 1200
                1f at 1700 using FastOutSlowInEasing
                1f at 2900
                0f at 3400 using FastOutSlowInEasing
            },
        ),
        label = "miniSel",
    )
    val trackW = 180.dp
    val cellW = 90.dp
    Box(
        modifier = Modifier
            .width(trackW)
            .height(46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
    ) {
        val density = LocalDensity.current
        val cellPx = with(density) { cellW.toPx() }
        Box(
            modifier = Modifier
                .width(cellW)
                .fillMaxSize()
                .padding(4.dp)
                .graphicsLayer { translationX = sel * (cellPx - 8.dp.toPx()) + 0f }
                .offset(x = 4.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                    RoundedCornerShape(percent = 50),
                ),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            listOf(stringResource(R.string.mode_auto), stringResource(R.string.mode_manual)).forEach { label ->
                Box(
                    modifier = Modifier.width(cellW).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

// --- 第 3 頁：Health Connect 授權 ---

@Composable
private fun HealthConnectPage(granted: Boolean) {
    MockPermissionCard(granted = granted)
    Spacer(modifier = Modifier.height(30.dp))
    Text(
        text = stringResource(R.string.ob_hc_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (granted) {
            stringResource(R.string.ob_hc_granted)
        } else {
            stringResource(R.string.ob_hc_sub)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
    )
}

/** 模擬 HC 授權頁的兩列開關：循環演示「把開關撥開」；已授權後改顯示完成態。 */
@Composable
private fun MockPermissionCard(granted: Boolean) {
    OnboardCard {
        Text(
            text = stringResource(R.string.ob_hc_card_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        if (granted) {
            MockDoneRow(stringResource(R.string.ob_perm_steps))
            MockDoneRow(stringResource(R.string.ob_perm_distance))
        } else {
            val t = rememberInfiniteTransition(label = "hcMock")
            val steps by t.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 3200
                        0f at 0
                        0f at 500
                        1f at 950 using FastOutSlowInEasing
                        1f at 3200
                    },
                ),
                label = "mockSteps",
            )
            val dist by t.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 3200
                        0f at 0
                        0f at 1100
                        1f at 1550 using FastOutSlowInEasing
                        1f at 3200
                    },
                ),
                label = "mockDist",
            )
            MockToggleRow(stringResource(R.string.ob_perm_steps), steps)
            MockToggleRow(stringResource(R.string.ob_perm_distance), dist)
        }
    }
}

/** 一列「標籤＋模擬開關」。[on] 0=關、1=開，中間值是撥動過程。 */
@Composable
private fun MockToggleRow(label: String, on: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        val track = lerp(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.primary,
            on,
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(track),
        ) {
            val density = LocalDensity.current
            val travel = with(density) { 20.dp.toPx() }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .padding(3.dp)
                    .graphicsLayer { translationX = on * travel }
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun MockDoneRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.ob_perm_allowed),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// --- 第 4 頁：通知 ---

@Composable
private fun NotificationPage(granted: Boolean) {
    MockNotificationCard(granted = granted)
    Spacer(modifier = Modifier.height(30.dp))
    Text(
        text = stringResource(R.string.ob_notif_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (granted) {
            stringResource(R.string.ob_notif_granted)
        } else {
            stringResource(R.string.ob_notif_sub)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
    )
}

/** 模擬掛機時的常駐通知：循環從上方滑入，展示「允許之後你會看到什麼」。 */
@Composable
private fun MockNotificationCard(granted: Boolean) {
    // 出現進度 0→1：滑入＋淡入；停留後淡出重來。已允許就靜止顯示＋綠勾。
    val appear: Float
    if (granted) {
        appear = 1f
    } else {
        val t = rememberInfiniteTransition(label = "notifMock")
        val animated by t.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = 3600
                    0f at 0
                    0f at 300
                    1f at 850 using FastOutSlowInEasing
                    1f at 3100
                    0f at 3500
                },
            ),
            label = "notifAppear",
        )
        appear = animated
    }
    val density = LocalDensity.current
    OnboardCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear
                    translationY = (appear - 1f) * with(density) { 18.dp.toPx() }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App 圖示的迷你圓環
            Canvas(modifier = Modifier.size(34.dp)) {
                drawCircle(color = Color(0xFFA7E2D4))
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension * 0.26f,
                    style = Stroke(width = size.minDimension * 0.14f),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ob_notif_mock_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.ob_notif_mock_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            if (granted) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.ob_notif_stop),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// --- 第 5 頁：電池不受限制 ---

@Composable
private fun BatteryPage(done: Boolean) {
    MockBatteryCard(done = done)
    Spacer(modifier = Modifier.height(30.dp))
    Text(
        text = stringResource(R.string.ob_battery_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = if (done) {
            stringResource(R.string.ob_battery_done)
        } else {
            stringResource(R.string.ob_battery_sub)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
        lineHeight = 22.sp,
    )
}

/**
 * 模擬系統「電池用量」三選項：高亮膠囊從「最佳化」滑到「不受限制」循環演示，
 * 明確告訴使用者**從哪個改到哪個**；完成後顯示靜態勾選態。
 */
@Composable
private fun MockBatteryCard(done: Boolean) {
    OnboardCard {
        Text(
            text = stringResource(R.string.ob_battery_card),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        val rowH = 42.dp
        // sel：1＝停在「最佳化」（系統預設）、0＝停在「不受限制」（目標）
        val sel: Float
        if (done) {
            sel = 0f
        } else {
            val t = rememberInfiniteTransition(label = "batteryMock")
            val animated by t.animateFloat(
                initialValue = 1f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    keyframes {
                        durationMillis = 3800
                        1f at 0
                        1f at 900
                        0f at 1500 using FastOutSlowInEasing
                        0f at 3300
                        1f at 3800 using FastOutSlowInEasing
                    },
                ),
                label = "batterySel",
            )
            sel = animated
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val rowPx = with(density) { rowH.toPx() }
            // 移動的高亮膠囊：所在列＝目前被選的選項
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowH)
                    .graphicsLayer { translationY = sel * rowPx }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            )
            Column {
                MockRadioRow(stringResource(R.string.ob_battery_unrestricted), filled = 1f - sel, height = rowH, showCheck = true)
                MockRadioRow(stringResource(R.string.ob_battery_optimized), filled = sel, height = rowH, showCheck = false)
                MockRadioRow(stringResource(R.string.ob_battery_restricted), filled = 0f, height = rowH, showCheck = false)
            }
        }
    }
}

/** 一列「radio＋標籤」。[filled] 0–1 控制 radio 內點與文字的選中程度。 */
@Composable
private fun MockRadioRow(label: String, filled: Float, height: androidx.compose.ui.unit.Dp, showCheck: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val base = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(
                color = lerp(base.copy(alpha = 0.35f), primary, filled),
                style = Stroke(width = 2.dp.toPx()),
            )
            if (filled > 0.01f) {
                drawCircle(color = primary.copy(alpha = filled), radius = size.minDimension * 0.26f)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (filled > 0.5f) FontWeight.Bold else FontWeight.Normal,
            color = lerp(base.copy(alpha = 0.75f), primary, filled),
            modifier = Modifier.weight(1f),
        )
        if (showCheck) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = primary,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { alpha = filled },
            )
        }
    }
}

// --- 共用小件 ---

/** 導覽用白卡：同主畫面卡片語彙（22dp 圓角、軟陰影、細白邊）。 */
@Composable
private fun OnboardCard(content: @Composable ColumnScope.() -> Unit) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, spotColor = Color.Black.copy(alpha = 0.12f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.94f else 0.97f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.16f else 0.85f)),
                shape,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** 頁點指示：目前頁拉寬成膠囊，其餘是小圓點。 */
@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val selected = i == current
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(if (selected) 22.dp else 8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        },
                    ),
            )
        }
    }
}
