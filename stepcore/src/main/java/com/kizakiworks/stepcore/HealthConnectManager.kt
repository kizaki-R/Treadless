package com.kizakiworks.stepcore

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Length
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Health Connect 寫入步數的薄包裝層。
 *
 * 只負責把「步行測試工具」產生的步數寫進 Health Connect，不碰位置 / 速度 / 路線。
 * Google Fit 端的同步由系統處理（開 Fit 才刷新）。
 */
object HealthConnectManager {

    /**
     * 唯一**必要**的權限：步數。
     *
     * 距離是可選的——`StepsRecord` 自己就是完整有效的寫入。以前把距離也列為必要，
     * 結果是「使用者只授權步數就被判定未授權、整個工具拒跑」，那是不必要的耦合。
     */
    val REQUIRED_PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(StepsRecord::class),
    )

    /** 可選權限：距離。沒有就只寫步數，不影響主要功能。 */
    val DISTANCE_PERMISSION: String =
        HealthPermission.getWritePermission(DistanceRecord::class)

    /** manifest 宣告的完整集合（步數＋距離）。 */
    val ALL_PERMISSIONS: Set<String> = REQUIRED_PERMISSIONS + DISTANCE_PERMISSION

    /** Health Connect SDK 狀態（SDK_AVAILABLE / 需更新 / 不支援）。 */
    fun sdkStatus(context: Context): Int =
        HealthConnectClient.getSdkStatus(context.applicationContext)

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun needsProviderUpdate(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED

    fun clientOrNull(context: Context): HealthConnectClient? =
        if (isAvailable(context)) {
            HealthConnectClient.getOrCreate(context.applicationContext)
        } else {
            null
        }

    /** 是否已取得必要（步數）寫入權限。工具能不能跑，看這個。 */
    suspend fun hasStepsPermission(context: Context): Boolean {
        val client = clientOrNull(context) ?: return false
        return client.permissionController.getGrantedPermissions()
            .containsAll(REQUIRED_PERMISSIONS)
    }

    /** 是否已取得距離寫入權限（可選）。 */
    suspend fun hasDistancePermission(context: Context): Boolean {
        val client = clientOrNull(context) ?: return false
        return client.permissionController.getGrantedPermissions().contains(DISTANCE_PERMISSION)
    }

    /**
     * 這次要不要一併寫距離：設定有開**且**拿得到距離權限才寫，回傳 null 代表只寫步數。
     *
     * 兩個條件都要檢查——設定開著但權限沒給就硬塞距離，`insertRecords` 會整批失敗，
     * 連步數都寫不進去。
     */
    suspend fun distanceMetersFor(context: Context, steps: Long): Double? {
        if (!StepTestStore.isWriteDistanceEnabled(context)) return null
        if (!hasDistancePermission(context)) return null
        return steps * StepTestStore.getStride(context).toDouble()
    }

    /**
     * 開 Health Connect 設定首頁讓使用者手動授權。
     *
     * 【雷】不要改用 `PermissionController` 的 in-app 權限請求：繁體中文語系下
     * HC 自己的權限頁會崩（RequestPermissionHeaderPreference.convertTextViewIntoLink →
     * IndexOutOfBoundsException: setSpan(-1...)），是 HC 的本地化 bug，不是我們的錯。
     * 走設定頁已實測不崩。
     */
    fun openSettings(context: Context) {
        val opened = runCatching {
            context.startActivity(
                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) {
            runCatching {
                context.startActivity(
                    Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    /** 前往 Health Connect 的安裝 / 更新頁（Play 商店，附 onboarding deep link）。 */
    fun openInstall(context: Context) {
        val uri = Uri.parse(
            "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding",
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(
                        "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata",
                    ),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Health Connect **單筆記錄**的硬上限。
     *
     * 【雷】這兩個上限是 `StepsRecord` / `DistanceRecord` 的**建構子**在檢查的，超標直接丟
     * `IllegalArgumentException`——不是送出時才失敗。呼叫端（StepTestService）用 runCatching
     * 包住寫入，所以超標的症狀是「工具看起來在跑、通知在倒數，但一步都沒進 HC」。
     * 取消每分鐘步數上限後一定會踩到，故 [writeSteps] 自動分批。
     */
    const val MAX_STEPS_PER_RECORD = 1_000_000L
    const val MAX_DISTANCE_METERS_PER_RECORD = 1_000_000.0

    /** 一次 insertRecords 送出的記錄筆數上限，避免單次交易過大。 */
    private const val INSERT_BATCH = 500

    /**
     * 寫入一段時間區間的步數（與可選距離）。
     *
     * 超過單筆上限時自動切成多筆，時間區間依比例等分——總步數與總距離不變，
     * 對讀取端（遊戲讀當日總量）完全等價。
     *
     * @param steps 此區間的步數（>0 才寫）。
     * @param start 區間起點。
     * @param end 區間終點。
     * @param distanceMeters 此區間的距離（公尺），null 則不寫距離。
     */
    suspend fun writeSteps(
        context: Context,
        steps: Long,
        start: Instant,
        end: Instant,
        distanceMeters: Double? = null,
    ) {
        if (steps <= 0L || !end.isAfter(start)) return
        val client = clientOrNull(context) ?: return
        val zone = ZoneId.systemDefault().rules
        val strideM = if (distanceMeters != null && distanceMeters > 0.0) {
            distanceMeters / steps
        } else {
            0.0
        }

        val records = mutableListOf<Record>()
        for (chunk in planChunks(steps, start, end, strideM)) {
            records += StepsRecord(
                count = chunk.steps,
                startTime = chunk.start,
                startZoneOffset = zone.getOffset(chunk.start),
                endTime = chunk.end,
                endZoneOffset = zone.getOffset(chunk.end),
                metadata = Metadata.manualEntry(),
            )
            if (strideM > 0.0) {
                records += DistanceRecord(
                    distance = Length.meters(chunk.steps * strideM),
                    startTime = chunk.start,
                    startZoneOffset = zone.getOffset(chunk.start),
                    endTime = chunk.end,
                    endZoneOffset = zone.getOffset(chunk.end),
                    metadata = Metadata.manualEntry(),
                )
            }
        }

        records.chunked(INSERT_BATCH).forEach { client.insertRecords(it) }
    }

    /** 一段區間切出來的其中一筆記錄。 */
    internal data class Chunk(val steps: Long, val start: Instant, val end: Instant)

    /**
     * 把 [steps] 依單筆上限切成數筆，時間區間等分且首尾對齊原區間、彼此不重疊。
     *
     * 抽成純函式是為了單元測試——真的寫 100 萬步進 Health Connect 只為了驗分批，
     * 會在使用者的健康記錄留下一大坨垃圾。
     *
     * @param strideM 每步公尺數；0 表示不寫距離（此時只有步數上限會生效）。
     */
    internal fun planChunks(
        steps: Long,
        start: Instant,
        end: Instant,
        strideM: Double,
    ): List<Chunk> {
        if (steps <= 0L || !end.isAfter(start)) return emptyList()

        // 每筆塞得下幾步：步數與距離兩個上限取小的那個。
        // 步長 1.5 時 100 萬步＝150 萬公尺，會先爆距離上限，所以不能只看步數。
        val perRecord = if (strideM > 0.0) {
            minOf(MAX_STEPS_PER_RECORD, (MAX_DISTANCE_METERS_PER_RECORD / strideM).toLong())
        } else {
            MAX_STEPS_PER_RECORD
        }.coerceAtLeast(1L)

        // 每筆至少要佔 1 奈秒，否則區間退化成 start == end 會被 HC 擋下。
        // 實務上碰不到（5 秒窗＝50 億奈秒，遠多於任何合理筆數），純防呆。
        val totalNanos = Duration.between(start, end).toNanos()
        val count = minOf((steps + perRecord - 1) / perRecord, totalNanos).toInt().coerceAtLeast(1)

        val chunks = ArrayList<Chunk>(count)
        var written = 0L
        for (i in 0 until count) {
            val chunkSteps = minOf(perRecord, steps - written)
            if (chunkSteps <= 0L) break
            val chunkStart = if (i == 0) start else start.plusNanos(totalNanos * i / count)
            val chunkEnd = if (i == count - 1) end else start.plusNanos(totalNanos * (i + 1) / count)
            if (!chunkEnd.isAfter(chunkStart)) continue
            chunks += Chunk(chunkSteps, chunkStart, chunkEnd)
            written += chunkSteps
        }
        return chunks
    }
}
