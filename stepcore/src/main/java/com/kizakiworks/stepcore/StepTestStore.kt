package com.kizakiworks.stepcore

import android.content.Context
import java.time.LocalDate

/**
 * 步數工具的設定與狀態（SharedPreferences）。
 *
 * 【雷】檔名與 key 前綴是使用者資料的定址，改了＝既有使用者設定歸零；
 * 要改只能在「還沒發佈」或「換 applicationId」的時機。
 */
object StepTestStore {
    private const val PREFS = "treadless"
    private const val KEY_ENABLED = "step_test_enabled"
    private const val KEY_RATE = "step_test_rate"          // 每分鐘步數
    private const val KEY_INTERVAL = "step_test_interval"  // 寫入間隔（秒）
    private const val KEY_SESSION = "step_test_session"    // 今日累計已寫步數
    private const val KEY_SESSION_DAY = "step_test_session_day" // 上面那筆屬於哪一天（epochDay）
    private const val KEY_LAST_WRITE = "step_test_last_write" // 上次寫入 epoch millis
    private const val KEY_STRIDE = "step_test_stride"      // 步長（公尺）
    private const val KEY_RETURN_PKG = "step_test_return_pkg" // 手動寫完切回的 App
    private const val KEY_MODE = "step_test_mode"             // 自動 / 手動
    private const val KEY_MANUAL_STEPS = "step_test_manual_steps"   // 手動每次寫入的步數
    private const val KEY_MANUAL_RETURN = "step_test_manual_return" // 手動寫完是否跳回遊戲
    private const val KEY_MANUAL_CONFIRM = "step_test_manual_confirm" // 點快捷鍵是否先跳確認
    private const val KEY_RETURN_DELAY = "step_test_return_delay_ms"  // 自動跳轉前停留毫秒
    private const val KEY_WRITE_DISTANCE = "step_test_write_distance" // 是否一併寫距離記錄
    private const val KEY_ONBOARDED = "onboarding_done"               // 首次導覽是否看完

    const val DEFAULT_RATE = 100       // 每分鐘 100 步
    const val DEFAULT_INTERVAL = 60    // 每 60 秒寫一次

    /**
     * 寫入間隔下限（秒）＝實測遊戲在前景時的 HC 讀取週期。
     * 寫得比這更密不會更快入帳，只是讓記錄筆數成倍增加。
     */
    const val INTERVAL_MIN = 10
    const val DEFAULT_STRIDE = 0.72f   // 平均步長 0.72m
    const val RATE_MIN = 1

    /**
     * 每分鐘步數不設產品上限（1000 → 10000 → 取消，2026-08-24，使用者要測極限）。
     *
     * `Int.MAX_VALUE` 只是型別邊界。真正會擋人的是 Health Connect 的**單筆記錄**上限
     * （步數 100 萬、距離 100 萬公尺），那個由 [HealthConnectManager.writeSteps] 自動分批處理。
     */
    const val RATE_MAX = Int.MAX_VALUE

    const val DEFAULT_RETURN_PKG = "com.nianticlabs.pikmin" // Pikmin Bloom

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun getRate(context: Context): Int =
        prefs(context).getInt(KEY_RATE, DEFAULT_RATE).coerceIn(RATE_MIN, RATE_MAX)

    fun setRate(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_RATE, value.coerceIn(RATE_MIN, RATE_MAX)).apply()
    }

    fun getIntervalSec(context: Context): Int =
        prefs(context).getInt(KEY_INTERVAL, DEFAULT_INTERVAL).coerceIn(INTERVAL_MIN, 3600)

    fun setIntervalSec(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_INTERVAL, value.coerceIn(INTERVAL_MIN, 3600)).apply()
    }

    fun getStride(context: Context): Float =
        prefs(context).getFloat(KEY_STRIDE, DEFAULT_STRIDE).coerceIn(0.3f, 1.5f)

    fun setStride(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_STRIDE, value.coerceIn(0.3f, 1.5f)).apply()
    }

    fun getSessionSteps(context: Context): Long {
        rolloverIfNewDay(context)
        return prefs(context).getLong(KEY_SESSION, 0L)
    }

    fun addSessionSteps(context: Context, delta: Long) {
        rolloverIfNewDay(context)
        val now = prefs(context).getLong(KEY_SESSION, 0L)
        prefs(context).edit().putLong(KEY_SESSION, now + delta).apply()
    }

    /**
     * 跨日就把「今日累計」歸零，以本地時區的午夜為界。
     *
     * 沒有排程也沒有鬧鐘——讀寫累計時順手比對日期即可。掛機整夜的服務會在
     * 午夜後的第一次寫入自動翻頁，UI 的兩秒對帳迴圈也會讀到新的一天。
     * 這比排 AlarmManager 省事，而且 App 沒開、服務沒跑的時候也不會漏掉。
     */
    private fun rolloverIfNewDay(context: Context) {
        val today = LocalDate.now().toEpochDay()
        val p = prefs(context)
        if (p.getLong(KEY_SESSION_DAY, Long.MIN_VALUE) != today) {
            p.edit().putLong(KEY_SESSION, 0L).putLong(KEY_SESSION_DAY, today).apply()
        }
    }

    /**
     * 自動模式啟動時：只丟掉上次寫入時間（新一輪的時間軸從現在開始），
     * **不動今日累計**——累計現在是「今天寫了多少」，不是「這一輪寫了多少」，
     * 一天內開開關關不該把數字洗掉。
     */
    fun resetSession(context: Context) {
        rolloverIfNewDay(context)
        prefs(context).edit().remove(KEY_LAST_WRITE).apply()
    }

    /**
     * 手動把今日累計歸零（重置鈕）。
     *
     * **不能動 [KEY_LAST_WRITE]**——那是手動寫入用來接續時間區間、避免 HC 擋下重疊記錄的基準，
     * 清掉之後下一筆會退回「now 往前 60 秒」，剛好蓋到才寫進去的那一筆。
     */
    fun clearSessionSteps(context: Context) {
        val today = LocalDate.now().toEpochDay()
        prefs(context).edit().putLong(KEY_SESSION, 0L).putLong(KEY_SESSION_DAY, today).apply()
    }

    fun getLastWrite(context: Context): Long =
        prefs(context).getLong(KEY_LAST_WRITE, 0L)

    fun setLastWrite(context: Context, epochMillis: Long) {
        prefs(context).edit().putLong(KEY_LAST_WRITE, epochMillis).apply()
    }

    // --- 手動寫完切回的 App ---
    // Fit 同步已全面移除（2026-08-26，遊戲直接讀 HC，不再需要快閃 Fit）；
    // 舊 prefs key（step_test_fit_sync 等）不清理，留在儲存裡無害。

    fun getReturnPackage(context: Context): String =
        prefs(context).getString(KEY_RETURN_PKG, DEFAULT_RETURN_PKG) ?: DEFAULT_RETURN_PKG

    fun setReturnPackage(context: Context, value: String) {
        prefs(context).edit().putString(KEY_RETURN_PKG, value).apply()
    }

    // --- 自動 / 手動模式 ---

    /** 自動：前景服務定時寫入。手動：按一下寫一次，不需要服務。 */
    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"
    const val DEFAULT_MANUAL_STEPS = 1000

    fun getMode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_AUTO) ?: MODE_AUTO

    fun setMode(context: Context, value: String) {
        prefs(context).edit().putString(KEY_MODE, value).apply()
    }

    fun getManualSteps(context: Context): Int =
        prefs(context).getInt(KEY_MANUAL_STEPS, DEFAULT_MANUAL_STEPS).coerceAtLeast(1)

    fun setManualSteps(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_MANUAL_STEPS, value.coerceAtLeast(1)).apply()
    }

    // --- 距離記錄 ---

    /**
     * 是否一併寫入 `DistanceRecord`。預設開（維持既有行為）。
     *
     * 距離對步數**不是必需品**：`StepsRecord` 自己就是完整有效的寫入，步長只用來換算距離。
     * 關掉的好處：HC 記錄筆數減半、單筆分批不必再受距離上限（100 萬公尺）拘束、
     * 也不必要求距離寫入權限。
     */
    fun isWriteDistanceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_WRITE_DISTANCE, true)

    fun setWriteDistanceEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_WRITE_DISTANCE, value).apply()
    }

    /**
     * 點快捷鍵後是否先跳確認。預設**開**——這支 App 寫的是真實健康記錄，
     * 誤觸的代價是把假步數塞進使用者的 HC，比多按一下麻煩得多。
     * 想要一鍵直寫的人可以在畫面上一鍵關掉。
     */
    fun isManualConfirmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MANUAL_CONFIRM, true)

    fun setManualConfirmEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MANUAL_CONFIRM, value).apply()
    }

    /** 自動跳轉前的停留毫秒（0.5–3.0 秒）。停留是為了讓使用者看得到寫入結果。 */
    fun getReturnDelayMs(context: Context): Int =
        prefs(context).getInt(KEY_RETURN_DELAY, 500).coerceIn(500, 3000)

    fun setReturnDelayMs(context: Context, value: Int) {
        prefs(context).edit().putInt(KEY_RETURN_DELAY, value.coerceIn(500, 3000)).apply()
    }

    /** 首次導覽是否已看完（看完或按略過都算）。 */
    fun isOnboarded(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONBOARDED, false)

    fun setOnboarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONBOARDED, value).apply()
    }

    fun isManualReturnEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MANUAL_RETURN, false)

    fun setManualReturnEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_MANUAL_RETURN, value).apply()
    }
}
