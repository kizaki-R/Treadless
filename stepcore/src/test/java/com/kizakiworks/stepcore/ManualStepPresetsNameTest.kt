package com.kizakiworks.stepcore

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 守住分組名稱的顯示寬度規則：全形算 2、英數算 1，額度 [ManualStepPresets.MAX_NAME_WIDTH]。
 *
 * 存在的理由：這條規則純為版面美觀（6 顆切換鍵要同列），但截斷寫錯的症狀很隱蔽
 * ——名稱被多砍一字只是「看起來怪」，不會報錯，很容易一直沒人發現。
 */
class ManualStepPresetsNameTest {

    private fun cut(raw: String) = ManualStepPresets.truncateToWidth(raw)

    @Test
    fun `兩個中文字剛好用滿額度`() {
        assertEquals("陶器", cut("陶器"))
        assertEquals("青銅", cut("青銅"))
    }

    @Test
    fun `第三個中文字被截掉`() {
        assertEquals("預設", cut("預設值"))
    }

    @Test
    fun `四個英數字剛好用滿額度`() {
        assertEquals("1000", cut("1000"))
        assertEquals("Bonu", cut("Bonus"))
    }

    @Test
    fun `中英混搭依寬度計算`() {
        // 30% ＝ 3 個半形位，還沒滿額度，不該被砍成 30
        assertEquals("30%", cut("30%"))
        assertEquals("50%", cut("50%"))
        // 中文 2 ＋ 英數 2 ＝ 4，剛好
        assertEquals("花15", cut("花15"))
        // 中文 2 ＋ 英數 3 ＝ 5，超出後截掉最後一碼
        assertEquals("花15", cut("花150"))
    }

    @Test
    fun `emoji 算兩位且不會被切成半個代理字元`() {
        // 單一 emoji ＝ 2 位，後面再接兩個英數剛好滿
        assertEquals("\uD83C\uDF31ab", cut("\uD83C\uDF31abc"))
        // 額度只剩 1 位時整個 emoji 放不下，要整顆捨棄而不是留半個
        assertEquals("abc", cut("abc\uD83C\uDF31"))
    }

    @Test
    fun `空字串與純空白`() {
        assertEquals("", cut(""))
        assertEquals("", ManualStepPresets.sanitizeName("   "))
    }

    @Test
    fun `序列化分隔符會被濾掉才計算寬度`() {
        assertEquals("陶器", ManualStepPresets.sanitizeName("陶|器:"))
    }
}
