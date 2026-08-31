package com.kizakiworks.stepcore

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToLong

/**
 * 步行測試工具的獨立前景服務。
 *
 * 啟動後依「每分鐘步數」設定，每隔 [StepTestStore.getIntervalSec] 秒把一段區間的步數
 * 寫入 Health Connect。完全不碰 GPS / 速度 / 路線，與模擬定位互不干擾。
 *
 * 宿主 App 只要依賴 :stepcore 就會併入服務宣告（見 stepcore 的 AndroidManifest.xml），
 * 通知小圖示可在自家 res/drawable/ 放同名 ic_stat_step.xml 覆寫。
 */
class StepTestService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private var tickerJob: Job? = null

    /** 上一次寫入是否失敗，用來在通知上示警（否則失敗是完全無聲的）。 */
    @Volatile
    private var lastWriteFailed: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(StepTestStore.getSessionSteps(this)),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        isRunning = true
        startLoop()
        startNotificationTicker()
        return START_STICKY
    }

    /**
     * 系統對前景服務判定超時（保險絲：specialUse 目前無時限，此處防未來政策收緊）。
     * 優雅收尾＋告知使用者，別讓系統硬殺留下「開關亮著但沒在跑」的殭屍狀態。
     */
    override fun onTimeout(startId: Int) {
        notifyStopped(AppLocale.wrap(this).getString(R.string.notif_stopped_timeout))
        stopSelf()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        StepTestStore.resetSession(this)
        var windowStart = Instant.now()
        loopJob = scope.launch {
            while (isActive) {
                val intervalSec = StepTestStore.getIntervalSec(this@StepTestService)
                // 公開下次寫入時刻給通知倒數與 UI 倒數用（一個週期設一次，秒數由讀的人自己算）
                nextWriteAtMs = System.currentTimeMillis() + intervalSec * 1000L
                delay(intervalSec * 1000L)
                if (!isActive) break

                val now = Instant.now()
                val rate = StepTestStore.getRate(this@StepTestService) // 每分鐘步數
                val elapsedSec = (now.epochSecond - windowStart.epochSecond).coerceAtLeast(1)
                val steps = (rate * elapsedSec / 60.0).roundToLong()
                if (steps > 0) {
                    runCatching {
                        HealthConnectManager.writeSteps(
                            context = this@StepTestService,
                            steps = steps,
                            start = windowStart,
                            end = now,
                            // 設定關掉或沒有距離權限就只寫步數
                            distanceMeters = HealthConnectManager
                                .distanceMetersFor(this@StepTestService, steps),
                        )
                    }.onSuccess {
                        lastWriteFailed = false
                        StepTestStore.addSessionSteps(this@StepTestService, steps)
                        StepTestStore.setLastWrite(this@StepTestService, now.toEpochMilli())
                        updateNotification(StepTestStore.getSessionSteps(this@StepTestService))
                    }.onFailure {
                        // 別再靜默失敗：權限被撤、HC 被停用、參數超標都會走到這裡，
                        // 以前的症狀是「通知照樣倒數但步數永遠是 0」，查半天才知道沒寫進去。
                        lastWriteFailed = true
                        updateNotification(StepTestStore.getSessionSteps(this@StepTestService))
                    }
                    windowStart = now
                }
            }
        }
    }

    /**
     * 每秒刷新通知，讓正文顯示「X 秒後寫入」。
     *
     * 原本只在開同步時才刷，現在寫入倒數是常駐資訊，所以整段執行期間都要刷。
     * 代價是每秒一次 notify——通知更新很輕，但別再往 ticker 裡加重活。
     */
    private fun startNotificationTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                updateNotification(StepTestStore.getSessionSteps(this@StepTestService))
            }
        }
    }

    override fun onDestroy() {
        isRunning = false
        nextWriteAtMs = 0L
        loopJob?.cancel()
        tickerJob?.cancel()
        scope.cancel()
        StepTestStore.setEnabled(this, false)
        super.onDestroy()
    }

    /** 服務停止時的一次性通知（非常駐），讓使用者知道掛機為何中斷。 */
    private fun notifyStopped(message: String) {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_stat_step)
            .setContentTitle(AppLocale.wrap(this).getString(R.string.notif_title))
            .setContentText(message)
            .setContentIntent(openAppPendingIntent())
            .setAutoCancel(true)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_STOPPED, builder.build())
    }

    /** 點通知本體 → 開 App 主畫面（已在前景就帶回既有 task，不重疊開新頁）。 */
    private fun openAppPendingIntent(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            1,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun updateNotification(sessionSteps: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(sessionSteps))
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val res = AppLocale.wrap(this)
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = res.getString(R.string.notif_channel_desc) }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(sessionSteps: Long): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopIntent = Intent(this, StepTestService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val res = AppLocale.wrap(this)
        builder
            .setSmallIcon(R.drawable.ic_stat_step)
            .setContentTitle(res.getString(R.string.notif_title_running))
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                Notification.Action.Builder(
                    null,
                    res.getString(R.string.notif_action_stop),
                    stopPending,
                ).build(),
            )

        // 正文：步數 · 下次寫入倒數
        val now = System.currentTimeMillis()
        val parts = mutableListOf(res.getString(R.string.notif_part_session, sessionSteps))
        if (lastWriteFailed) parts += res.getString(R.string.notif_part_failed)
        if (nextWriteAtMs > 0L) {
            // 已到點但還沒跑完（HC 寫入耗時）顯示進行中字樣，不要出現 0 秒
            val remainSec = (nextWriteAtMs - now + 999) / 1000
            parts += if (remainSec > 0) {
                res.getString(R.string.notif_countdown_write, remainSec)
            } else {
                res.getString(R.string.notif_writing)
            }
        }
        builder.setContentText(parts.joinToString(" · "))
        return builder.build()
    }

    companion object {
        // 【雷】頻道 id 是使用者通知設定的定址，發佈後不可再改
        private const val CHANNEL_ID = "treadless_step"
        private const val NOTIFICATION_ID = 1002
        private const val NOTIFICATION_ID_STOPPED = 1003
        const val ACTION_STOP = "com.kizakiworks.stepcore.action.STOP_STEP_TEST"

        /**
         * 服務真實生死（同程序記憶體旗標）。UI 開關以此為準，不能只信 prefs——
         * 程序被殺（閃退/系統回收）時 prefs 旗標會殘留 true，變成殭屍開關。
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        /**
         * 下次寫入的時刻（epoch millis），0 表示沒在跑。
         * 給 UI 做倒數用——跟 [isRunning] 一樣是同程序記憶體值，服務死了自然歸零。
         */
        @Volatile
        var nextWriteAtMs: Long = 0L
            private set

        fun start(context: Context) {
            val intent = Intent(context, StepTestService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepTestService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }
}
