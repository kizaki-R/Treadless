package com.kizakiworks.stepcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * 守住 [HealthConnectManager.planChunks] 的分批規則。
 *
 * 存在的理由：Health Connect 的單筆上限是**建構子**在檢查的（超標丟例外），而寫入被
 * runCatching 包住，所以分批算錯的症狀是「工具照跑、通知照倒數、但一步都沒進 HC」——
 * 完全無聲。取消每分鐘步數上限後這條路一定會被走到，故用測試釘死。
 */
class HealthConnectChunkTest {

    private val start = Instant.parse("2026-08-24T00:00:00Z")
    private fun end(seconds: Long) = start.plusSeconds(seconds)

    /** 總步數不能因為分批而增減，區間必須首尾對齊且彼此不重疊。 */
    private fun assertWellFormed(
        chunks: List<HealthConnectManager.Chunk>,
        expectedSteps: Long,
        expectedEnd: Instant,
    ) {
        assertTrue("至少要有一筆", chunks.isNotEmpty())
        assertEquals("總步數必須守恆", expectedSteps, chunks.sumOf { it.steps })
        assertEquals("第一筆要對齊原區間起點", start, chunks.first().start)
        assertEquals("最後一筆要對齊原區間終點", expectedEnd, chunks.last().end)
        chunks.forEach {
            assertTrue("每筆的 start 必須早於 end", it.end.isAfter(it.start))
            assertTrue("每筆不得超過步數上限", it.steps <= HealthConnectManager.MAX_STEPS_PER_RECORD)
        }
        chunks.zipWithNext().forEach { (a, b) ->
            assertEquals("相鄰兩筆必須首尾相接、不重疊", a.end, b.start)
        }
    }

    @Test
    fun `未超過上限時只切一筆`() {
        val chunks = HealthConnectManager.planChunks(83, start, end(5), 0.72)
        assertEquals(1, chunks.size)
        assertWellFormed(chunks, 83, end(5))
    }

    @Test
    fun `剛好等於上限仍是一筆`() {
        val steps = HealthConnectManager.MAX_STEPS_PER_RECORD
        val chunks = HealthConnectManager.planChunks(steps, start, end(5), 0.0)
        assertEquals(1, chunks.size)
        assertWellFormed(chunks, steps, end(5))
    }

    @Test
    fun `超過上限一步就切成兩筆`() {
        val steps = HealthConnectManager.MAX_STEPS_PER_RECORD + 1
        val chunks = HealthConnectManager.planChunks(steps, start, end(5), 0.0)
        assertEquals(2, chunks.size)
        assertEquals(HealthConnectManager.MAX_STEPS_PER_RECORD, chunks[0].steps)
        assertEquals(1L, chunks[1].steps)
        assertWellFormed(chunks, steps, end(5))
    }

    @Test
    fun `大量步數切成多筆且守恆`() {
        val steps = 8_500_000L
        val chunks = HealthConnectManager.planChunks(steps, start, end(5), 0.72)
        assertEquals(9, chunks.size)
        assertWellFormed(chunks, steps, end(5))
    }

    /** 步長 1.5 時 100 萬步＝150 萬公尺會先爆距離上限，每筆只能塞 666,666 步。 */
    @Test
    fun `長步長時距離上限先生效`() {
        val chunks = HealthConnectManager.planChunks(1_000_000, start, end(5), 1.5)
        assertEquals(2, chunks.size)
        assertEquals(666_666L, chunks[0].steps)
        assertWellFormed(chunks, 1_000_000, end(5))
        chunks.forEach {
            assertTrue(
                "每筆距離不得超過上限",
                it.steps * 1.5 <= HealthConnectManager.MAX_DISTANCE_METERS_PER_RECORD,
            )
        }
    }

    /** 不寫距離時距離上限不該參與計算，否則會被無謂地切碎。 */
    @Test
    fun `步長為零時只看步數上限`() {
        val chunks = HealthConnectManager.planChunks(1_000_000, start, end(5), 0.0)
        assertEquals(1, chunks.size)
    }

    @Test
    fun `區間無效或步數非正時不產生任何記錄`() {
        assertTrue(HealthConnectManager.planChunks(0, start, end(5), 0.72).isEmpty())
        assertTrue(HealthConnectManager.planChunks(-5, start, end(5), 0.72).isEmpty())
        assertTrue(HealthConnectManager.planChunks(100, start, start, 0.72).isEmpty())
        assertTrue(HealthConnectManager.planChunks(100, end(5), start, 0.72).isEmpty())
    }
}
