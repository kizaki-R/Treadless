package com.kizakiworks.stepcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 守住「出廠一定有六組」這件事。
 *
 * 存在的理由：`allowBackup` 讓 Android 會把舊版 prefs 從雲端還原到全新安裝上，
 * 於是遷移分支變成**一般使用者第一次開 App 就會走到的路徑**。這條分支曾經
 * 只回傳單一「預設」組，實機上的症狀是「下載安裝打開只有預設一組」，
 * 而且完全不會報錯——只有人工比對才看得出來，所以用測試釘住。
 */
class ManualStepPresetsLegacyTest {

    private val defaultNames = ManualStepPresets.DEFAULT.map { it.name }

    @Test
    fun `舊版扁平格式遷移後仍然是六組`() {
        val groups = ManualStepPresets.fromLegacy("500,1500,8000")
        assertEquals(ManualStepPresets.MAX_GROUPS, groups.size)
        assertEquals(defaultNames, groups.map { it.name })
    }

    @Test
    fun `舊數值進預設組，其餘五組維持出廠值`() {
        val groups = ManualStepPresets.fromLegacy("500,1500,8000")
        assertEquals(listOf(500, 1_500, 8_000), groups.first().steps)
        assertEquals(ManualStepPresets.DEFAULT.drop(1), groups.drop(1))
    }

    @Test
    fun `舊數值會去重並由小到大排列`() {
        val groups = ManualStepPresets.fromLegacy("8000, 500,1500, 500")
        assertEquals(listOf(500, 1_500, 8_000), groups.first().steps)
    }

    @Test
    fun `舊數值超過上限只留最小的五個`() {
        val groups = ManualStepPresets.fromLegacy("10,20,30,40,50,60,70")
        assertEquals(ManualStepPresets.MAX_STEPS_PER_GROUP, groups.first().steps.size)
        assertEquals(listOf(10, 20, 30, 40, 50), groups.first().steps)
    }

    @Test
    fun `空的或全是垃圾字元的舊值直接回出廠六組`() {
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.fromLegacy(""))
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.fromLegacy("abc,,  ,-"))
    }

    // --- 「第一次開 App 會看到什麼」的每一條分支 ---

    @Test
    fun `全新安裝（沒有任何存檔）就是出廠六組`() {
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.resolveGroups(null, null))
    }

    @Test
    fun `只有舊格式存檔時遷移成六組`() {
        val groups = ManualStepPresets.resolveGroups(null, "500,1500")
        assertEquals(ManualStepPresets.MAX_GROUPS, groups.size)
        assertEquals(listOf(500, 1_500), groups.first().steps)
    }

    @Test
    fun `新格式存檔優先於舊格式`() {
        val groups = ManualStepPresets.resolveGroups("我的:7,8", "500,1500")
        assertEquals(1, groups.size)
        assertEquals("我的", groups.first().name)
        assertEquals(listOf(7, 8), groups.first().steps)
    }

    @Test
    fun `使用者自己刪到剩幾組就尊重幾組，不要硬補回六組`() {
        val groups = ManualStepPresets.resolveGroups("預設:100,200|10%:91", null)
        assertEquals(2, groups.size)
        assertEquals(listOf("預設", "10%"), groups.map { it.name })
    }

    @Test
    fun `新格式存檔壞掉解不出東西時回出廠六組，不要給空畫面`() {
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.resolveGroups("", null))
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.resolveGroups("亂碼沒有分隔符", null))
        assertEquals(ManualStepPresets.DEFAULT, ManualStepPresets.resolveGroups("空的:|也空:", null))
    }

    @Test
    fun `新格式存檔超過六組時只取前六組`() {
        val raw = (1..9).joinToString("|") { "G$it:${it * 100}" }
        assertEquals(ManualStepPresets.MAX_GROUPS, ManualStepPresets.resolveGroups(raw, null).size)
    }

    @Test
    fun `出廠預設本身就是滿六組、每組都有值`() {
        assertEquals(ManualStepPresets.MAX_GROUPS, ManualStepPresets.DEFAULT.size)
        assertTrue(ManualStepPresets.DEFAULT.all { it.steps.isNotEmpty() })
        assertTrue(
            ManualStepPresets.DEFAULT.all {
                it.steps.size <= ManualStepPresets.MAX_STEPS_PER_GROUP
            },
        )
    }
}
