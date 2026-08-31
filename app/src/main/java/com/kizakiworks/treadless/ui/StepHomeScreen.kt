package com.kizakiworks.treadless.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.createBitmap
import androidx.health.connect.client.HealthConnectClient
import com.kizakiworks.treadless.R
import com.kizakiworks.glassui.LiquidGlassContainer
import com.kizakiworks.glassui.LiquidSegmentAnimation
import com.kizakiworks.glassui.liquidSegmentIndicator
import com.kizakiworks.glassui.liquidSegmentItem
import com.kizakiworks.glassui.rememberLiquidSegmentState
import com.kizakiworks.glassui.LiquidGlassPillSwitcher
import com.kizakiworks.stepcore.AppLocale
import com.kizakiworks.stepcore.HealthConnectManager
import com.kizakiworks.stepcore.ManualStepPresets
import com.kizakiworks.stepcore.ManualStepWriter
import com.kizakiworks.stepcore.StepTestService
import com.kizakiworks.stepcore.StepTestStore
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/**
 * Treadless 的唯一畫面。
 *
 * 兩種模式共用同一套引擎（`:stepcore`）與同一條時間軸，**互斥使用**：
 * - **自動**：前景服務定時寫入，適合掛機。要通知／電池無限制才活得久。
 * - **手動**：按一下寫一次，不需要服務、不需要那些權限。適合「開遊戲前補一筆」。
 *
 * 引擎全在 :stepcore；本畫面把所有參數（寫入間隔、步長、寫完切回的 App）都開放調整。
 */
@Composable
fun StepHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sdkStatus by remember { mutableIntStateOf(HealthConnectManager.sdkStatus(context)) }
    var hasPermission by remember { mutableStateOf(false) }
    // 初始就以「prefs 旗標 ∧ 服務真實活著」為準，殭屍旗標連一幀都不顯示
    var enabled by remember {
        mutableStateOf(StepTestStore.isEnabled(context) && StepTestService.isRunning)
    }
    var sessionSteps by remember { mutableLongStateOf(StepTestStore.getSessionSteps(context)) }
    var lastWrite by remember { mutableLongStateOf(StepTestStore.getLastWrite(context)) }

    var mode by remember { mutableStateOf(StepTestStore.getMode(context)) }
    val manual = mode == StepTestStore.MODE_MANUAL

    var rate by remember { mutableIntStateOf(StepTestStore.getRate(context)) }
    var intervalSec by remember { mutableIntStateOf(StepTestStore.getIntervalSec(context)) }
    var stride by remember { mutableFloatStateOf(StepTestStore.getStride(context)) }

    var manualSteps by remember { mutableIntStateOf(StepTestStore.getManualSteps(context)) }
    var manualReturn by remember { mutableStateOf(StepTestStore.isManualReturnEnabled(context)) }
    var manualBusy by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf(ManualStepPresets.getGroups(context)) }
    var groupIndex by remember {
        mutableIntStateOf(
            ManualStepPresets.getActiveIndex(context)
                .coerceIn(0, ManualStepPresets.getGroups(context).size - 1),
        )
    }
    var editingGroup by remember { mutableStateOf(false) }
    var sortDesc by remember { mutableStateOf(ManualStepPresets.isSortDescending(context)) }
    var confirmWrite by remember { mutableStateOf(StepTestStore.isManualConfirmEnabled(context)) }
    // 等待確認的步數；null＝沒有待確認的寫入
    var pendingSteps by remember { mutableStateOf<Int?>(null) }
    var manualStatus by remember { mutableStateOf<String?>(null) }

    var returnPkg by remember { mutableStateOf(StepTestStore.getReturnPackage(context)) }
    var returnDelayMs by remember { mutableIntStateOf(StepTestStore.getReturnDelayMs(context)) }

    var hasDistancePerm by remember { mutableStateOf(false) }
    var writeDistance by remember { mutableStateOf(StepTestStore.isWriteDistanceEnabled(context)) }

    var notifOk by remember { mutableStateOf(hasNotificationPermission(context)) }
    var batteryOk by remember { mutableStateOf(isBatteryUnrestricted(context)) }

    var dialog by remember { mutableStateOf<StepDialog?>(null) }
    val scrollState = rememberScrollState()

    // 每秒推進的「現在」。倒數與「N 分前」都讀它才會自己走，不然畫面不動就凍住。
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notifOk = granted }

    // 每 2 秒對帳一次真實狀態。開關以服務的記憶體旗標為準：程序被殺（閃退/系統回收/
    // 超時）時 prefs 會殘留 enabled=true，回到畫面必須照真實生死顯示，死了就連動關 Fit。
    // 這個輪詢同時負責在使用者從系統設定頁返回後刷新權限狀態。
    LaunchedEffect(Unit) {
        while (true) {
            sdkStatus = HealthConnectManager.sdkStatus(context)
            hasPermission = HealthConnectManager.hasStepsPermission(context)
            hasDistancePerm = HealthConnectManager.hasDistancePermission(context)
            sessionSteps = StepTestStore.getSessionSteps(context)
            lastWrite = StepTestStore.getLastWrite(context)
            notifOk = hasNotificationPermission(context)
            batteryOk = isBatteryUnrestricted(context)
            if (enabled && !StepTestService.isRunning) {
                enabled = false
                StepTestStore.setEnabled(context, false)
            }
            delay(2000)
        }
    }

    fun stopAuto() {
        StepTestStore.setEnabled(context, false)
        StepTestService.stop(context)
        enabled = false
    }

    fun onToggle(want: Boolean) {
        if (!want) {
            stopAuto()
            return
        }
        when {
            sdkStatus != HealthConnectClient.SDK_AVAILABLE -> HealthConnectManager.openInstall(context)
            !hasPermission -> HealthConnectManager.openSettings(context)
            else -> {
                StepTestStore.setEnabled(context, true)
                StepTestService.start(context)
                enabled = true
            }
        }
    }

    fun onModeChange(index: Int) {
        val next = if (index == 0) StepTestStore.MODE_AUTO else StepTestStore.MODE_MANUAL
        if (next == mode) return
        // 兩種模式共用同一條時間軸，不能並存：切走就把自動停掉
        if (next == StepTestStore.MODE_MANUAL && enabled) stopAuto()
        mode = next
        StepTestStore.setMode(context, next)
        manualStatus = null
        // 兩種模式的內容長度差很多，留在原捲動位置會停在半路，切換要回到頂部
        scope.launch { scrollState.animateScrollTo(0) }
    }

    fun performManualWrite(steps: Int) {
        // 記住最後一次用的量：hero 卡的「上次寫入」與膠囊選中狀態都看它
        manualSteps = steps
        StepTestStore.setManualSteps(context, steps)
        manualBusy = true
        scope.launch {
            val result = ManualStepWriter.writeNow(context, steps.toLong())
            manualStatus = when (result) {
                is ManualStepWriter.Result.Success ->
                    context.getString(R.string.msg_written, result.steps, result.windowSeconds)

                ManualStepWriter.Result.TooSoon -> context.getString(R.string.msg_too_soon)
                ManualStepWriter.Result.NotAvailable -> context.getString(R.string.msg_hc_unavailable)
                ManualStepWriter.Result.NoPermission -> context.getString(R.string.msg_no_permission)
                is ManualStepWriter.Result.Failed -> context.getString(
                    R.string.msg_failed,
                    result.cause.message ?: context.getString(R.string.msg_unknown_error),
                )
            }
            sessionSteps = StepTestStore.getSessionSteps(context)
            lastWrite = StepTestStore.getLastWrite(context)
            manualBusy = false
            if (result is ManualStepWriter.Result.Success && manualReturn) {
                // 停留一下讓使用者看到「已寫入」結果再切走，秒數可在設定裡選
                delay(returnDelayMs.toLong())
                ManualStepWriter.returnToGame(context)
            }
        }
    }

    fun onManualWrite(steps: Int) {
        if (manualBusy) return
        // HC 不可用／沒授權就先處理那個，沒必要為一筆寫不出去的資料跳確認
        when {
            sdkStatus != HealthConnectClient.SDK_AVAILABLE -> {
                HealthConnectManager.openInstall(context)
                return
            }
            !hasPermission -> {
                HealthConnectManager.openSettings(context)
                return
            }
        }
        if (confirmWrite) pendingSteps = steps else performManualWrite(steps)
    }

    val hazeState = rememberHazeState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
    // 背景本身也要當模糊來源（z0），否則卡片之間的空隙在玻璃條裡會糊成透明。
    // 【雷】hazeSource 必須在 background **之前**（外層）：hazeSource 只捕捉鏈上比它
    // 內層的繪製，寫成 .background().hazeSource() 會捕到空 Box → 玻璃拿到透明 →
    // AGSL shader 輸出變成不透明深灰髒塊（實機影片抓到過）。
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(MaterialTheme.colorScheme.background),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 鏈位鐵則：hazeSource 緊跟 fillMaxSize、在 scroll 與 padding 之前
            .hazeSource(hazeState, zIndex = 1f)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroCard(
            enabled = enabled,
            sessionSteps = sessionSteps,
            lastWrite = lastWrite,
            rate = rate,
            manual = manual,
            manualSteps = manualSteps,
            sdkStatus = sdkStatus,
            hasPermission = hasPermission,
            onHcClick = {
                if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
                    HealthConnectManager.openInstall(context)
                } else if (!hasPermission) {
                    HealthConnectManager.openSettings(context)
                }
            },
            onLanguageClick = { dialog = StepDialog.Language },
            onResetSession = {
                StepTestStore.clearSessionSteps(context)
                sessionSteps = 0
                manualStatus = null
            },
            nowMs = nowMs,
            // 沒在跑就沒有下次寫入，這時右欄退回顯示「上次寫入多久前」
            nextWriteAtMs = if (!manual && enabled) StepTestService.nextWriteAtMs else 0L,
        )

        if (manual) {
            // 使用者選的是「點一下直接寫入」，所以膠囊本身就是主要動作區，不再另放大按鈕
            ManualPresetPanel(
                groups = groups,
                groupIndex = groupIndex,
                selected = manualSteps,
                enabled = !manualBusy,
                sortDescending = sortDesc,
                onWrite = { onManualWrite(it) },
                onToggleSort = {
                    sortDesc = !sortDesc
                    ManualStepPresets.setSortDescending(context, sortDesc)
                },
                onSwitchGroup = { i ->
                    groupIndex = i
                    ManualStepPresets.setActiveIndex(context, i)
                },
                onAddGroup = {
                    if (groups.size < ManualStepPresets.MAX_GROUPS) {
                        val updated = groups +
                            ManualStepPresets.Group(
                                // 名稱限兩字，預設就給「組N」讓使用者直接改
                                context.getString(R.string.group_default_new, groups.size + 1),
                                listOf(StepTestStore.DEFAULT_MANUAL_STEPS),
                            )
                        ManualStepPresets.setGroups(context, updated)
                        groups = ManualStepPresets.getGroups(context)
                        groupIndex = groups.size - 1
                        ManualStepPresets.setActiveIndex(context, groupIndex)
                        // 新分組直接進編輯，讓使用者馬上取名、填數值
                        editingGroup = true
                    }
                },
                onEditGroup = { editingGroup = true },
            )
        } else {
            Button(
                onClick = { onToggle(!enabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = if (enabled) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    text = when {
                        sdkStatus != HealthConnectClient.SDK_AVAILABLE ->
                            stringResource(R.string.btn_install_hc)
                        !hasPermission -> stringResource(R.string.btn_grant)
                        enabled -> stringResource(R.string.btn_stop)
                        else -> stringResource(R.string.btn_start_auto)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (manual) {
            // 永遠佔一行高（沒訊息就是空白行）：寫入結果出現/消失時
            // 下方設定卡不會整塊跳動（使用者抓過）
            Text(
                text = manualStatus ?: "",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                minLines = 1,
                maxLines = 1,
            )
        }

        ReadinessSection(
            sdkStatus = sdkStatus,
            hasPermission = hasPermission,
            hasDistancePerm = hasDistancePerm,
            writeDistance = writeDistance,
            // 手動模式沒有前景服務，通知／電池／懸浮這三項都用不到，不要拿去煩使用者
            manual = manual,
            notifOk = notifOk,
            batteryOk = batteryOk,
            onInstallHc = { HealthConnectManager.openInstall(context) },
            onGrantHc = { HealthConnectManager.openSettings(context) },
            onGrantNotif = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    openAppNotificationSettings(context)
                }
            },
            onGrantBattery = { requestBatteryExemption(context) },
        )

        if (manual) {
            CollapsibleCard(title = stringResource(R.string.card_other)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.row_confirm),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.row_confirm_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = confirmWrite,
                        onCheckedChange = {
                            confirmWrite = it
                            StepTestStore.setManualConfirmEnabled(context, it)
                        },
                    )
                }
                DistanceSettings(
                    writeDistance = writeDistance,
                    onToggle = {
                        writeDistance = it
                        StepTestStore.setWriteDistanceEnabled(context, it)
                    },
                    onStrideClick = { dialog = StepDialog.Stride },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.row_autojump),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.row_autojump_sub,
                                "%.1f".format(returnDelayMs / 1000.0),
                                appLabelOf(context, returnPkg),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    Switch(
                        checked = manualReturn,
                        onCheckedChange = {
                            manualReturn = it
                            StepTestStore.setManualReturnEnabled(context, it)
                        },
                    )
                }
                if (manualReturn) {
                    ValueRow(
                        label = stringResource(R.string.row_jump_target),
                        value = appLabelOf(context, returnPkg),
                        onClick = { dialog = StepDialog.ReturnPkg },
                    )
                    ValueRow(
                        label = stringResource(R.string.row_jump_delay),
                        value = stringResource(
                            R.string.fmt_delay_sec,
                            "%.1f".format(returnDelayMs / 1000.0),
                        ),
                        onClick = { dialog = StepDialog.ReturnDelay },
                    )
                }
                Hint(stringResource(R.string.hint_write_rate_limit))
            }
        } else {
            SectionCard(title = stringResource(R.string.card_write_settings)) {
                ValueRow(
                    label = stringResource(R.string.row_rate),
                    value = stringResource(R.string.fmt_rate, rate),
                    onClick = { dialog = StepDialog.Rate },
                )
                ValueRow(
                    label = stringResource(R.string.row_interval),
                    value = stringResource(R.string.fmt_seconds_short, intervalSec),
                    onClick = { dialog = StepDialog.Interval },
                )
                DistanceSettings(
                    writeDistance = writeDistance,
                    onToggle = {
                        writeDistance = it
                        StepTestStore.setWriteDistanceEnabled(context, it)
                    },
                    onStrideClick = { dialog = StepDialog.Stride },
                )
                Hint(
                    if (writeDistance) {
                        stringResource(
                            R.string.hint_hourly_with_km,
                            rate.toLong() * 60,
                            rate.toLong() * 60 * stride / 1000.0,
                        )
                    } else {
                        stringResource(R.string.hint_hourly, rate.toLong() * 60)
                    },
                )
            }

            if (!batteryOk) {
                Hint(stringResource(R.string.hint_battery_home))
            }
        }

        // 讓最後一張卡不要被底部懸浮模式條蓋住
        Spacer(modifier = Modifier.height(96.dp))
    }

        // 不加漸層 scrim：分隔交給玻璃條的背景模糊，蓋一層不透明漸層會讓模糊沒東西可糊
        ModeFloatingBar(
            hazeState = hazeState,
            manual = manual,
            onModeChange = { onModeChange(it) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp),
        )
    }

    // --- 各種數值編輯彈窗 ---
    when (dialog) {
        StepDialog.Rate -> NumberDialog(
            title = stringResource(R.string.row_rate),
            description = stringResource(R.string.dlg_rate_desc),
            label = stringResource(R.string.unit_rate),
            initial = rate.toString(),
            onDismiss = { dialog = null },
            onSave = { text ->
                text.toIntOrNull()?.let {
                    val v = it.coerceIn(StepTestStore.RATE_MIN, StepTestStore.RATE_MAX)
                    rate = v
                    StepTestStore.setRate(context, v)
                }
                dialog = null
            },
        )

        StepDialog.Interval -> NumberDialog(
            title = stringResource(R.string.row_interval),
            description = stringResource(R.string.dlg_interval_desc, StepTestStore.INTERVAL_MIN),
            label = stringResource(R.string.unit_seconds),
            initial = intervalSec.toString(),
            onDismiss = { dialog = null },
            onSave = { text ->
                text.toIntOrNull()?.let {
                    val v = it.coerceIn(StepTestStore.INTERVAL_MIN, 3600)
                    intervalSec = v
                    StepTestStore.setIntervalSec(context, v)
                }
                dialog = null
            },
        )

        StepDialog.Stride -> NumberDialog(
            title = stringResource(R.string.row_stride),
            description = stringResource(R.string.dlg_stride_desc),
            label = stringResource(R.string.unit_meters),
            initial = "%.2f".format(stride),
            onDismiss = { dialog = null },
            onSave = { text ->
                text.toFloatOrNull()?.let {
                    val v = it.coerceIn(0.3f, 1.5f)
                    stride = v
                    StepTestStore.setStride(context, v)
                }
                dialog = null
            },
        )

        StepDialog.Language -> AppDialog(
            title = stringResource(R.string.dlg_language),
            onDismiss = { dialog = null },
            buttons = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_close)) }
            },
        ) {
            val currentLang = AppLocale.get(context)
            listOf(
                AppLocale.ZH to stringResource(R.string.lang_zh),
                AppLocale.EN to stringResource(R.string.lang_en),
            ).forEach { (code, label) ->
                val chosen = code == currentLang
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            dialog = null
                            if (!chosen) {
                                AppLocale.set(context, code)
                                // 語言存進 prefs 後重建 Activity，attachBaseContext 重新包裝 locale
                                (context as? Activity)?.recreate()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        color = if (chosen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (chosen) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        StepDialog.ReturnDelay -> AppDialog(
            title = stringResource(R.string.dlg_jump_delay),
            onDismiss = { dialog = null },
            buttons = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            Text(
                text = stringResource(R.string.dlg_delay_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            listOf(500, 1000, 1500, 2000, 2500, 3000).chunked(3).forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { ms ->
                        val chosen = ms == returnDelayMs
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    if (chosen) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    },
                                )
                                .clickable {
                                    returnDelayMs = ms
                                    StepTestStore.setReturnDelayMs(context, ms)
                                    dialog = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.fmt_delay_sec,
                                    "%.1f".format(ms / 1000.0),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (chosen) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                },
                            )
                        }
                    }
                }
            }
        }

        StepDialog.ReturnPkg -> AppPickerDialog(
            initial = returnPkg,
            onDismiss = { dialog = null },
            onSave = { pkg ->
                returnPkg = pkg
                StepTestStore.setReturnPackage(context, pkg)
                dialog = null
            },
        )

        null -> Unit
    }

    pendingSteps?.let { steps ->
        AppDialog(
            title = stringResource(R.string.confirm_title),
            onDismiss = { pendingSteps = null },
            buttons = {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { pendingSteps = null }) { Text(stringResource(R.string.action_cancel)) }
                TextButton(
                    onClick = {
                        pendingSteps = null
                        performManualWrite(steps)
                    },
                ) { Text(stringResource(R.string.action_write)) }
            },
        ) {
            Text(
                text = stringResource(R.string.fmt_steps, steps),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.confirm_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }

    if (editingGroup) {
        GroupEditDialog(
            group = groups[groupIndex],
            canDelete = groups.size > 1,
            canMoveLeft = groupIndex > 0,
            canMoveRight = groupIndex < groups.size - 1,
            sortDescending = sortDesc,
            onToggleSort = {
                sortDesc = !sortDesc
                ManualStepPresets.setSortDescending(context, sortDesc)
            },
            onMove = { delta, edited ->
                // 帶著當下編輯中的內容一起搬，否則按了排序會把還沒存的改動吃掉
                val j = groupIndex + delta
                val m = groups.toMutableList()
                m[groupIndex] = m[j]   // 鄰組退到原本的位置
                m[j] = edited          // 編輯中的這組前進
                ManualStepPresets.setGroups(context, m)
                groups = ManualStepPresets.getGroups(context)
                // 切換鍵跟著移過去，可以連按一路把某組推到定位
                groupIndex = j
                ManualStepPresets.setActiveIndex(context, j)
            },
            onDismiss = { editingGroup = false },
            onDelete = {
                val updated = groups.filterIndexed { i, _ -> i != groupIndex }
                ManualStepPresets.setGroups(context, updated)
                groups = ManualStepPresets.getGroups(context)
                groupIndex = groupIndex.coerceIn(0, groups.size - 1)
                ManualStepPresets.setActiveIndex(context, groupIndex)
                editingGroup = false
            },
            onSave = { edited ->
                val updated = groups.mapIndexed { i, old -> if (i == groupIndex) edited else old }
                ManualStepPresets.setGroups(context, updated)
                // setGroups 會去重＋排序，重讀一次讓 UI 跟儲存一致
                groups = ManualStepPresets.getGroups(context)
                editingGroup = false
            },
        )
    }
}

private enum class StepDialog { Rate, Interval, Stride, ReturnPkg, ReturnDelay, Language }

/** 快捷步數格線一列最多幾格（再多字就擠到看不清數字）。 */
private const val MAX_CELLS_PER_ROW = 3

// --- 版面元件 ---

/**
 * 底部中央的懸浮模式條。
 *
 * 原本是頂部滿版一排，但兩個選項佔一整排太浪費，而 App 名稱只是內部代號、
 * 放在畫面最上方沒有資訊價值（啟動器與工作管理員都已經顯示了）。
 * 移到底部中央：拇指構得到，符合懸浮玻璃 bar 的慣用語彙。
 *
 * 外殼用 [LiquidGlassContainer]——Haze 即時背景模糊 ＋ Android 13+
 * 的 AGSL 邊緣折射／Fresnel ＋ 1px 銳利高光描邊。不畫陰影（玻璃 bar 本來就近乎無影）。
 */
@Composable
private fun ModeFloatingBar(
    hazeState: HazeState,
    manual: Boolean,
    onModeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassContainer(
        hazeState = hazeState,
        isDark = isSystemInDarkTheme(),
        modifier = modifier.height(46.dp),
        contentPadding = PaddingValues(4.dp),
    ) {
        LiquidGlassPillSwitcher(
            items = listOf(stringResource(R.string.mode_auto), stringResource(R.string.mode_manual)),
            selectedIndex = if (manual) 1 else 0,
            onSelect = onModeChange,
            modifier = Modifier
                .width(156.dp)
                .height(38.dp),
        )
    }
}

/** hero 卡右上角的 Health Connect 連線狀態。沒接上時變紅並可點，接上了就安靜待著。 */
@Composable
private fun HealthConnectChip(
    sdkStatus: Int,
    hasPermission: Boolean,
    onClick: () -> Unit,
) {
    val ok = sdkStatus == HealthConnectClient.SDK_AVAILABLE && hasPermission
    val label = when {
        ok -> "Health Connect"
        sdkStatus != HealthConnectClient.SDK_AVAILABLE -> stringResource(R.string.hc_not_installed)
        else -> stringResource(R.string.hc_not_granted)
    }
    val tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.12f))
            .then(if (ok) Modifier else Modifier.clickable { onClick() })
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = if (ok) {
                Icons.Outlined.CheckCircle
            } else {
                Icons.Outlined.ErrorOutline
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun HeroCard(
    enabled: Boolean,
    sessionSteps: Long,
    lastWrite: Long,
    rate: Int,
    manual: Boolean,
    manualSteps: Int,
    sdkStatus: Int,
    hasPermission: Boolean,
    onHcClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onResetSession: () -> Unit,
    nowMs: Long,
    nextWriteAtMs: Long,
) {
    val context = LocalContext.current
    Card {
        // 頂排：左＝語言切換（翻譯軟體通用的「文A」符號），右＝HC 連線狀態
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(
                icon = Icons.Outlined.Translate,
                contentDescription = stringResource(R.string.cd_language),
                onClick = onLanguageClick,
                size = 30.dp,
                iconSize = 16.dp,
            )
            Spacer(modifier = Modifier.weight(1f))
            HealthConnectChip(
                sdkStatus = sdkStatus,
                hasPermission = hasPermission,
                onClick = onHcClick,
            )
        }

        // 狀態資訊與重置鈕同排（左狀態、右重置），**疊**在分隔線正上方，
        // 不佔版面高度——獨立成列會把數字與標籤整段往上推（使用者抓過）
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "%,d".format(sessionSteps),
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    // 兩種模式都是「今天寫了多少」，午夜自動翻頁歸零
                    text = stringResource(R.string.today_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            val live = !manual && enabled
            Row(
                // 靠下貼分隔線：不給高度框，讓文字自然底對齊（與重設鈕同底線）
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (live) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                            },
                        ),
                )
                Text(
                    text = when {
                        manual -> stringResource(R.string.status_manual)
                        enabled -> stringResource(R.string.status_running)
                        else -> stringResource(R.string.status_stopped)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (live) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    },
                )
            }
            RoundIconButton(
                icon = Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.cd_reset_today),
                onClick = onResetSession,
                modifier = Modifier.align(Alignment.BottomEnd),
                size = 32.dp,
                iconSize = 15.dp,
            )
        }

        Divider()

        Row(modifier = Modifier.fillMaxWidth()) {
            MiniStat(
                modifier = Modifier.weight(1f),
                label = if (manual) stringResource(R.string.stat_last_amount) else stringResource(R.string.stat_rate),
                value = if (manual) {
                    stringResource(R.string.fmt_steps, manualSteps)
                } else {
                    stringResource(R.string.fmt_rate, rate)
                },
            )
            // 寫入中＝倒數下次寫入（動態），其餘＝上次寫入距今多久
            val counting = nextWriteAtMs > 0L
            val remainSec = (nextWriteAtMs - nowMs + 999) / 1000
            MiniStat(
                modifier = Modifier.weight(1f),
                label = if (counting) stringResource(R.string.stat_next_write) else stringResource(R.string.stat_last_write),
                value = when {
                    counting && remainSec > 0 -> stringResource(R.string.fmt_seconds_short, remainSec)
                    counting -> stringResource(R.string.status_running)
                    lastWrite > 0 -> relativeTime(context, lastWrite, nowMs)
                    else -> "—"
                },
            )
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier = Modifier, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        )
    }
}

/**
 * 準備清單：只列出「還沒好」的項目，全部就緒時整區消失，不佔版面。
 * 手動模式沒有前景服務，通知／電池兩項都不需要，所以整組跳過。
 */
@Composable
private fun ReadinessSection(
    sdkStatus: Int,
    hasPermission: Boolean,
    hasDistancePerm: Boolean,
    writeDistance: Boolean,
    manual: Boolean,
    notifOk: Boolean,
    batteryOk: Boolean,
    onInstallHc: () -> Unit,
    onGrantHc: () -> Unit,
    onGrantNotif: () -> Unit,
    onGrantBattery: () -> Unit,
) {
    val items = buildList {
        when {
            sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                add(stringResource(R.string.setup_hc_update) to onInstallHc)

            sdkStatus != HealthConnectClient.SDK_AVAILABLE ->
                add(stringResource(R.string.setup_hc_install) to onInstallHc)

            !hasPermission ->
                add(stringResource(R.string.setup_hc_grant) to onGrantHc)
        }
        if (writeDistance && sdkStatus == HealthConnectClient.SDK_AVAILABLE &&
            hasPermission && !hasDistancePerm
        ) {
            // 只寫步數仍然完全可用，這裡是說明不是阻擋
            add(stringResource(R.string.setup_distance) to onGrantHc)
        }
        if (!manual) {
            if (!notifOk) add(stringResource(R.string.setup_notif) to onGrantNotif)
            if (!batteryOk) add(stringResource(R.string.setup_battery) to onGrantBattery)
        }
    }
    if (items.isEmpty()) return

    SectionCard(title = stringResource(R.string.setup_title)) {
        items.forEach { (text, action) ->
            GuideRow(text = text, onClick = action)
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

/**
 * 可收合卡片：平時只剩標題列，點擊向下展開。
 *
 * **收合時整張卡就是標題物件**——卡面直接是標題＋chevron，不在白卡裡
 * 再墊一層灰底（卡中卡看起來廉價，使用者退過件）。標題身分靠字重
 * （titleSmall Bold vs 內容 bodyMedium）、可展開靠主題色 chevron 表達。
 * 不用 [Card] 包是因為它的 spacedBy(10dp) 在收合時仍替高度為 0 的內容
 * 留間距，卡底會多一截空白。
 */
@Composable
private fun CollapsibleCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "collapsibleChevron",
    )
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
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 關掉 ripple：整卡白底上那塊灰色按壓回饋很突兀（使用者退件）；
                // 按下去的回饋交給 chevron 旋轉與展開動畫
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 標題做成 HC 晶片同款的主題色膠囊（淡主色底＋主色字）
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.cd_collapse) else stringResource(R.string.cd_expand),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevron),
            )
        }
        // 高度用彈簧展開（帶一點回彈）、內容淡入慢半拍；收合走快速 tween 乾脆收掉
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            ) + fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 60)),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 200),
            ) + fadeOut(animationSpec = tween(durationMillis = 120)),
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/**
 * 距離記錄設定：一列搞定——開關＋（開著才顯示的）板手鈕開步長設定。
 *
 * 距離對步數不是必需品——`StepsRecord` 自己就完整。關掉的實際好處是 HC 記錄筆數減半，
 * 而且不必要求距離寫入權限。
 */
@Composable
private fun DistanceSettings(
    writeDistance: Boolean,
    onToggle: (Boolean) -> Unit,
    onStrideClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.row_distance),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (writeDistance) {
            RoundIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_stride),
                onClick = onStrideClick,
                size = 28.dp,
                iconSize = 14.dp,
            )
        }
        Switch(checked = writeDistance, onCheckedChange = onToggle)
    }
}

/**
 * 手動模式的快捷步數面板：上方分組切換鍵（最多 5 組、可自訂名稱、＋新增），
 * 下方一組 5 個數值膠囊（自動由小到大）＋ 1 個編輯鍵，排成 3+3 兩列。
 *
 * **點一下＝直接寫入該步數**（不是先選再按），編輯鍵開彈窗改分組名稱與數值。
 * 液態滑動指示與模式條共用 :glassui 的同一套（bounds 實測制，跨列滑移也成立）。
 * 多列用手排 Row 而不是 FlowRow——FlowRow 的 @Composable 簽章跨 foundation 版本
 * 不穩（實機踩過 NoSuchMethodError 閃退），手排 Row 沒這個險。
 */
@Composable
private fun ManualPresetPanel(
    groups: List<ManualStepPresets.Group>,
    groupIndex: Int,
    selected: Int,
    enabled: Boolean,
    sortDescending: Boolean,
    onWrite: (Int) -> Unit,
    onSwitchGroup: (Int) -> Unit,
    onAddGroup: () -> Unit,
    onEditGroup: () -> Unit,
    onToggleSort: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val group = groups.getOrNull(groupIndex) ?: return
    // 儲存永遠由小到大，顯示順序由排序鈕決定
    val steps = if (sortDescending) group.steps.reversed() else group.steps

    // 軌道高度固定：標籤字級由切換器自己量測縮放（永遠單行），
    // 版面不再隨系統字體倍率抖動——這才是各種字體大小下都不走鐘的關鍵。
    val switcherHeight = 44.dp

    // --- 分組切換鍵（液態藥丸切換器＋淡色軌道）＋ 新增分組 ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(switcherHeight)
                .clip(shape)
                // 淺色 0.06：onSurface 是藍板岩近黑（Ink #0F172A），疊到薄荷背景上會把綠壓掉
                // 變中性灰，0.10 時比背景暗 22 階、在一片薄荷裡最搶戲（實機截圖量過）。
                // 深色維持 0.10：那裡是白疊近黑、不會發灰，再調淡會讓軌道糊進背景。
                .background(
                    MaterialTheme.colorScheme.onSurface
                        .copy(alpha = if (isSystemInDarkTheme()) 0.10f else 0.06f),
                ),
        ) {
            LiquidGlassPillSwitcher(
                items = groups.map { it.name },
                selectedIndex = groupIndex,
                onSelect = onSwitchGroup,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (groups.size < ManualStepPresets.MAX_GROUPS) {
            RoundIconButton(
                icon = Icons.Outlined.Add,
                contentDescription = stringResource(R.string.cd_add_group),
                onClick = onAddGroup,
            )
        }
    }

    // --- 數值膠囊格線：本組數值＋1 個編輯／排序鍵 ---
    val seg = rememberLiquidSegmentState()
    // 指示藥丸停在「上次寫入的那一格」；上次用的是別組/自訂值就不在任何一格上（-1 → 淡出）
    val selectedIndex = steps.indexOfFirst { it == selected }
    LiquidSegmentAnimation(seg, selectedIndex)

    // 版面平衡：每列最多 3 格，但實際格數由總量回推，讓兩列差不超過 1。
    // 寫死 3 的話只填 3 個數值時會排成 3+1，第二排剩一顆孤零零的編輯鍵，很空。
    // 1 值→[值][鍵]｜2→[值][值][鍵]｜3→2+2｜4→3+2｜5→3+3
    val totalCells = steps.size + 1
    val rowCount = (totalCells + MAX_CELLS_PER_ROW - 1) / MAX_CELLS_PER_ROW
    val cellsPerRow = (totalCells + rowCount - 1) / rowCount

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidSegmentIndicator(seg, fill = MaterialTheme.colorScheme.primary),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (row in 0 until rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (col in 0 until cellsPerRow) {
                    val index = row * cellsPerRow + col
                    when {
                        index < steps.size -> {
                            val value = steps[index]
                            val isSelected = index == selectedIndex
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    Color.Transparent
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                },
                                animationSpec = tween(durationMillis = 220),
                                label = "presetBorder",
                            )
                            val textColor by animateColorAsState(
                                targetValue = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                },
                                animationSpec = tween(durationMillis = 220),
                                label = "presetText",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidSegmentItem(seg, index)
                                    .clip(shape)
                                    .border(BorderStroke(1.dp, borderColor), shape)
                                    .clickable(enabled = enabled) { onWrite(value) }
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "%,d".format(value),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                )
                            }
                        }

                        index == steps.size -> Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 只放「動作」：編輯與排序。「寫入前確認」是狀態設定，
                            // 圖示鈕表達不了狀態＋後果，移去「寫入選項」卡當 Switch。
                            RoundIconButton(
                                icon = Icons.Outlined.Edit,
                                contentDescription = stringResource(R.string.cd_edit_group),
                                onClick = onEditGroup,
                                size = 44.dp,
                                iconSize = 20.dp,
                            )
                            RoundIconButton(
                                icon = if (sortDescending) {
                                    Icons.Outlined.ArrowDownward
                                } else {
                                    Icons.Outlined.ArrowUpward
                                },
                                contentDescription = stringResource(R.string.cd_sort_order),
                                onClick = onToggleSort,
                                size = 44.dp,
                                iconSize = 20.dp,
                            )
                        }

                        else -> Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 分組編輯彈窗：名稱＋最多 5 個數值。留空的數值欄忽略；儲存後由
 * [ManualStepPresets.setGroups] 統一去重＋由小到大排列。
 */
@Composable
private fun GroupEditDialog(
    group: ManualStepPresets.Group,
    canDelete: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    sortDescending: Boolean,
    onMove: (Int, ManualStepPresets.Group) -> Unit,
    onToggleSort: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (ManualStepPresets.Group) -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    // 欄位順序照使用者選的方向呈現，跟主畫面膠囊看到的一致（儲存永遠由小到大）
    val values = remember {
        val shown = if (sortDescending) group.steps.reversed() else group.steps
        mutableStateListOf<String>().apply {
            repeat(ManualStepPresets.MAX_STEPS_PER_GROUP) {
                add(shown.getOrNull(it)?.toString() ?: "")
            }
        }
    }

    /** 把已填的數值依方向重排，空欄一律沉到最後——切換方向時所見即所得。 */
    fun reorder(desc: Boolean) {
        val nums = values.mapNotNull { it.toIntOrNull() }.distinct().sorted()
        val ordered = if (desc) nums.reversed() else nums
        for (i in 0 until ManualStepPresets.MAX_STEPS_PER_GROUP) {
            values[i] = ordered.getOrNull(i)?.toString() ?: ""
        }
    }
    val parsedSteps = values.mapNotNull { it.toIntOrNull()?.coerceAtLeast(ManualStepPresets.MIN_VALUE) }
    AppDialog(
        title = stringResource(R.string.dlg_group_title),
        onDismiss = onDismiss,
        buttons = {
            if (canDelete) {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.group_delete)) }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(
                onClick = { onSave(ManualStepPresets.Group(name, parsedSteps)) },
                // 至少要留一個數值，不然這組按不出任何東西
                enabled = parsedSteps.isNotEmpty(),
            ) { Text(stringResource(R.string.action_save)) }
        },
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppTextField(
                label = stringResource(R.string.group_name_label),
                value = name,
                // 序列化分隔符直接擋在輸入端，空白留給使用者打字，儲存時才 trim
                onValueChange = { input ->
                    name = ManualStepPresets.truncateToWidth(input.filter { c -> c !in "|:," })
                },
                placeholder = stringResource(R.string.group_name_hint),
            )
            if (canMoveLeft || canMoveRight) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.group_order),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { onMove(-1, ManualStepPresets.Group(name, parsedSteps)) },
                        enabled = canMoveLeft && parsedSteps.isNotEmpty(),
                    ) { Text(stringResource(R.string.group_move_left)) }
                    TextButton(
                        onClick = { onMove(1, ManualStepPresets.Group(name, parsedSteps)) },
                        enabled = canMoveRight && parsedSteps.isNotEmpty(),
                    ) { Text(stringResource(R.string.group_move_right)) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.group_steps_label),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        reorder(!sortDescending)
                        onToggleSort()
                    },
                ) { Text(if (sortDescending) stringResource(R.string.sort_desc) else stringResource(R.string.sort_asc)) }
            }
            Text(
                text = stringResource(
                    R.string.group_fill_hint,
                    ManualStepPresets.MAX_STEPS_PER_GROUP,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            // 上到下的膠囊欄位，長得跟外面的快捷鍵同一套語彙；沒有編號，
            // 因為儲存後會依方向重排，「第幾格」對使用者不成立。
            values.forEachIndexed { i, value ->
                AppTextField(
                    label = "",
                    value = value,
                    onValueChange = { input -> values[i] = input.filter { c -> c.isDigit() } },
                    numeric = true,
                    placeholder = stringResource(R.string.value_empty_hint),
                    shape = RoundedCornerShape(percent = 50),
                    align = TextAlign.Center,
                    verticalPadding = 14.dp,
                )
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun GuideRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

// --- 共用小元件 ---

/** 主題色圓底小圖示鈕：板手、✎、排序、＋ 都用這顆，全 App 同一語彙。 */
@Composable
private fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize),
        )
    }
}

// --- 彈窗 ---

/**
 * App 風格彈窗容器：22dp 圓角白卡＋細白描邊＋軟陰影，跟一級頁面的卡片同語彙。
 * 預設 M3 AlertDialog 的紫灰容器與方框輸入欄跟主畫面格格不入（實機截圖確認過），
 * 所以所有彈窗一律走這個。
 */
@Composable
private fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(22.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(14.dp, shape, spotColor = Color.Black.copy(alpha = 0.16f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = if (isDark) 0.16f else 0.85f)),
                    shape,
                )
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                content = buttons,
            )
        }
    }
}

/** 跟 ValueRow 同語彙的輸入列：軟填色圓角、左標籤、右側主色粗體輸入。 */
@Composable
private fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    placeholder: String = "",
    shape: Shape = RoundedCornerShape(14.dp),
    align: TextAlign = TextAlign.End,
    verticalPadding: Dp = 12.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // label 留空＝這一列本身就是內容（步數膠囊），不需要前綴文字
        if (label.isNotEmpty()) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    textAlign = align,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = align,
                ),
                keyboardOptions = if (numeric) {
                    KeyboardOptions(keyboardType = KeyboardType.Decimal)
                } else {
                    KeyboardOptions.Default
                },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NumberDialog(
    title: String,
    description: String,
    label: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AppDialog(
        title = title,
        onDismiss = onDismiss,
        buttons = {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save)) }
        },
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        AppTextField(
            label = label,
            value = text,
            onValueChange = { input -> text = input.filter { c -> c.isDigit() || c == '.' } },
            numeric = true,
        )
    }
}

/**
 * 「寫完切回哪個 App」選擇器：列出所有可啟動的 App，附搜尋（名稱或套件名皆可）。
 * 依賴 :stepcore manifest 的 LAUNCHER intent query（Android 11+ 套件可見性），
 * 沒有它 queryIntentActivities 只看得到自己。
 */
@Composable
private fun AppPickerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    // 圖示快取活在這個彈窗的生命週期裡：捲來捲去不重解，關掉就整包釋放
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) {
        // 只掃名稱（快）；圖示交給各列自己非同步載，清單才能秒開
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }
    // 只比對顯示名稱：使用者想的是「Pikmin Bloom」，不是 com.nianticlabs.pikmin
    val filtered = apps?.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
    AppDialog(
        // 彈窗標題與設定列標籤分開：列在開關底下（脈絡夠）用短標籤，彈窗要能獨立看懂
        title = stringResource(R.string.dlg_jump_target),
        onDismiss = onDismiss,
        buttons = {
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.picker_none)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        AppTextField(
            label = stringResource(R.string.picker_search),
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.picker_search_hint),
        )
        when {
            filtered == null -> Hint(stringResource(R.string.picker_loading))
            filtered.isEmpty() -> Hint(stringResource(R.string.picker_empty))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(filtered, key = { it.pkg }) { entry ->
                    val chosen = entry.pkg == initial
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSave(entry.pkg) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AppIcon(pkg = entry.pkg, sizePx = iconPx, cache = iconCache)
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                            color = if (chosen) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (chosen) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 選擇器裡的一支 App。圖示不放這裡——見 [AppIcon]。 */
private data class AppEntry(val label: String, val pkg: String)

/**
 * 所有可啟動的 App，排除自己，照**顯示名稱**排序。
 *
 * 用 Collator 而不是 `sortedBy { lowercase() }`——後者是拿 Unicode 碼位排，
 * 中文名稱會排成沒人看得懂的順序；Collator 依語系規則排。
 * 會碰 PackageManager 並解出圖示，呼叫端務必丟到 IO 執行緒。
 */
private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val collator = Collator.getInstance(Locale.getDefault())
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, 0)
        .mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            AppEntry(label = info.loadLabel(pm).toString(), pkg = pkg)
        }
        .distinctBy { it.pkg }
        .sortedWith { a, b -> collator.compare(a.label, b.label) }
}

/**
 * 一列的 App 圖示：**捲到才解**，解完存進呼叫端給的快取。
 *
 * 【雷】原本是開清單時把上百支 App 的圖示一次解完才顯示——adaptive icon 要真的
 * 畫進 bitmap，一支幾毫秒、一百支就是明顯的開啟延遲（使用者回報「卡」）。
 * LazyColumn 只組可見的那幾列，改成逐列載之後清單是秒開的。
 */
@Composable
private fun AppIcon(pkg: String, sizePx: Int, cache: MutableMap<String, ImageBitmap>) {
    val context = LocalContext.current
    val icon by produceState(initialValue = cache[pkg], pkg, sizePx) {
        if (value != null) return@produceState
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(pkg).toImageBitmap(sizePx)
            }.getOrNull()
        }
        if (loaded != null) cache[pkg] = loaded
        value = loaded
    }
    if (icon != null) {
        Image(bitmap = icon!!, contentDescription = null, modifier = Modifier.size(28.dp))
    } else {
        // 佔位：載入中或解不出圖示，版面不要跳動
        Spacer(modifier = Modifier.size(28.dp))
    }
}

/** Drawable → ImageBitmap。adaptive icon 沒有現成點陣，得自己畫進 bitmap。 */
private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    val bitmap = createBitmap(sizePx, sizePx)
    setBounds(0, 0, sizePx, sizePx)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}

// --- 系統狀態與跳轉 ---

internal fun hasNotificationPermission(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

/** 背景電池是否設為「不受限制」（省電最佳化豁免）。長時間掛機的生死線。 */
internal fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(intent) }.isSuccess
    if (!ok) {
        // 部分機型（MIUI 等）擋掉直接請求，退回省電最佳化總表讓使用者自己找
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

internal fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 把套件名稱換成可讀的 App 名稱；沒裝就原樣顯示，留空表示不返回。 */
private fun appLabelOf(context: Context, pkg: String): String {
    if (pkg.isBlank()) return context.getString(R.string.picker_none)
    val pm = context.packageManager
    return runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrElse { pkg }
}

private fun relativeTime(context: Context, epochMillis: Long, nowMs: Long): String {
    val diff = (nowMs - epochMillis) / 1000
    return when {
        diff < 60 -> context.getString(R.string.time_sec_ago, diff)
        diff < 3600 -> context.getString(R.string.time_min_ago, diff / 60)
        else -> context.getString(R.string.time_hour_ago, diff / 3600)
    }
}
