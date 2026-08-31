package com.kizakiworks.stepcore

import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.Instant

/**
 * 手動寫入：按一下，立刻寫一筆步數進 Health Connect。
 *
 * 跟自動模式走**完全同一條路**（[HealthConnectManager.writeSteps]、同樣的
 * `StepsRecord` + `Metadata.manualEntry()`），所以讀取端（遊戲讀當日累計）分辨不出差別。
 *
 * 相對自動模式的好處：**不需要前景服務**。寫完就結束，沒有常駐通知、不吃電、
 * 不怕 Doze 凍結、不必設電池無限制。
 *
 * 【雷】同一個 App 寫的 `StepsRecord` **時間區間不能重疊**，HC 會擋。所以這裡的區間
 * 一律從「上次寫入的結束時間」接續，不能每次都用 `now - N 秒`——連按兩下就撞上了。
 * `StepTestStore.lastWrite` 存的正是上一次（自動或手動）的區間終點，兩種模式共用同一條
 * 時間軸，所以互斥使用就不會打架。
 */
object ManualStepWriter {

    /**
     * 回填視窗上限（秒）。上次寫入若是很久以前，只回填這麼多，
     * 不要生出橫跨數小時、還可能跨過午夜被拆成兩天的怪記錄。
     */
    const val MAX_BACKFILL_SEC = 60L

    sealed interface Result {
        /** 寫入成功。[windowSeconds] 是這筆記錄涵蓋的秒數，給 UI 顯示用。 */
        data class Success(val steps: Long, val windowSeconds: Long) : Result

        /** 距離上次寫入還不到 1 毫秒，再按會造成區間重疊。 */
        data object TooSoon : Result

        /** Health Connect 沒安裝或需更新。 */
        data object NotAvailable : Result

        /** 尚未取得寫入權限。 */
        data object NoPermission : Result

        data class Failed(val cause: Throwable) : Result
    }

    /**
     * 立即寫入 [steps] 步。回傳結果由 UI 決定怎麼呈現。
     *
     * 呼叫端要自己確保**自動模式沒在跑**——兩者共用同一條時間軸，同時用會互相插隊。
     */
    suspend fun writeNow(context: Context, steps: Long): Result {
        if (steps <= 0L) return Result.TooSoon
        if (!HealthConnectManager.isAvailable(context)) return Result.NotAvailable
        if (!HealthConnectManager.hasStepsPermission(context)) return Result.NoPermission

        val now = Instant.now()
        val lastEnd = StepTestStore.getLastWrite(context)
        val floor = now.minusSeconds(MAX_BACKFILL_SEC)
        val start = if (lastEnd > 0L) maxOf(Instant.ofEpochMilli(lastEnd), floor) else floor
        if (!now.isAfter(start)) return Result.TooSoon

        return runCatching {
            HealthConnectManager.writeSteps(
                context = context,
                steps = steps,
                start = start,
                end = now,
                // 設定關掉或沒有距離權限就只寫步數
                distanceMeters = HealthConnectManager.distanceMetersFor(context, steps),
            )
        }.fold(
            onSuccess = {
                StepTestStore.setLastWrite(context, now.toEpochMilli())
                StepTestStore.addSessionSteps(context, steps)
                Result.Success(steps, Duration.between(start, now).seconds)
            },
            onFailure = { Result.Failed(it) },
        )
    }

    /**
     * 寫完跳回指定的 App（預設 Pikmin Bloom），讓它自己去讀 HC。
     * 回傳是否真的跳成功（沒裝那支就跳不了）。
     */
    fun returnToGame(context: Context): Boolean {
        val pkg = StepTestStore.getReturnPackage(context)
        if (pkg.isBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
