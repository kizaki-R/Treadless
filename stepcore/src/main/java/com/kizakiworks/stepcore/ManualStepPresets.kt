package com.kizakiworks.stepcore

import android.content.Context

/**
 * 手動模式的快捷步數**分組**（最多 [MAX_GROUPS] 組、每組最多 [MAX_STEPS_PER_GROUP] 個數值）。
 *
 * 一次只顯示一組的快捷鍵；組內數值儲存時自動去重＋由小到大排列（順序不可自訂，
 * 這是刻意的——5 盆栽的原始/加成步數本來就該從小排到大）。分組名稱可自訂。
 *
 * 序列化：組之間用 '|'，組內「名稱:數值,數值,...」；名稱過濾掉分隔符（|:,）。
 * 沿用同一份 prefs；舊版扁平格式（純逗號數字串）讀取時自動轉成單一「預設」組。
 */
object ManualStepPresets {
    private const val PREFS = "treadless"
    private const val KEY_LEGACY = "step_test_manual_presets"
    private const val KEY_GROUPS = "step_test_manual_preset_groups"
    private const val KEY_ACTIVE = "step_test_manual_preset_group_active"

    const val MAX_GROUPS = 6
    const val MAX_STEPS_PER_GROUP = 5
    const val MIN_VALUE = 1
    const val DEFAULT_GROUP_NAME = "預設"

    /**
     * 分組名稱的**顯示寬度**上限（單位：半形字元）：全形／中文算 2、英數算 1，
     * 也就是「2 個中文字」或「4 個英數字」，混搭亦可（例：`30%` ＝ 3）。
     *
     * 用顯示寬度而不是字數，純粹是為了版面美觀——切換鍵要在**同一列**塞下最多
     * [MAX_GROUPS] 顆按鈕，每格只有約 1/6 寬。若改用「2 字」會把 `30%` 這種
     * 三字元的短標籤誤砍成 `30`。超出時由 [sanitizeName] 依寬度截斷。
     */
    const val MAX_NAME_WIDTH = 4

    data class Group(val name: String, val steps: List<Int>)

    /**
     * 出廠預載：一組基準＋五組加成係數。
     *
     * 數值＝基準步數 ÷ (1 + 加成)，也就是「有加成時實際只要寫這麼多步」。
     * 基準取 100 / 1,000 / 3,000 / 5,000 / 10,000。
     */
    val DEFAULT = listOf(
        Group(DEFAULT_GROUP_NAME, listOf(100, 1_000, 3_000, 5_000, 10_000)),
        Group("10%", listOf(91, 920, 2_730, 4_550, 9_095)),
        Group("30%", listOf(77, 777, 2_310, 3_850, 7_700)),
        Group("50%", listOf(67, 667, 2_000, 3_333, 6_667)),
        Group("65%", listOf(61, 610, 1_820, 3_050, 6_070)),
        Group("95%", listOf(52, 520, 1_550, 2_575, 5_150)),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 名稱不能含序列化分隔符；空白修掉，再依顯示寬度截斷。 */
    fun sanitizeName(raw: String): String =
        truncateToWidth(raw.replace(Regex("[|:,]"), "").trim())

    /**
     * 依顯示寬度截斷到 [MAX_NAME_WIDTH]：全形字佔 2、其餘佔 1。
     *
     * 【雷】用 code point 走訪而不是逐 Char——emoji 這類增補平面字元是代理對，
     * 逐 Char 截斷會切出半個代理字元，存進 prefs 就是壞字。
     */
    fun truncateToWidth(raw: String): String {
        val out = StringBuilder()
        var used = 0
        var i = 0
        while (i < raw.length) {
            val cp = raw.codePointAt(i)
            val w = if (isWideCodePoint(cp)) 2 else 1
            if (used + w > MAX_NAME_WIDTH) break
            val n = Character.charCount(cp)
            out.appendRange(raw, i, i + n)
            used += w
            i += n
        }
        return out.toString()
    }

    /** 佔兩個半形位的字：CJK、假名、諺文、全形符號，以及增補平面（emoji）。 */
    private fun isWideCodePoint(cp: Int): Boolean = when {
        cp in 0x1100..0x115F -> true   // 諺文字母
        cp in 0x2E80..0x303E -> true   // CJK 部首與標點
        cp in 0x3041..0x33FF -> true   // 假名、注音、CJK 相容
        cp in 0x3400..0x4DBF -> true   // CJK 擴充 A
        cp in 0x4E00..0x9FFF -> true   // CJK 統一表意文字
        cp in 0xA000..0xA4CF -> true   // 彝文
        cp in 0xAC00..0xD7A3 -> true   // 諺文音節
        cp in 0xF900..0xFAFF -> true   // CJK 相容表意文字
        cp in 0xFE30..0xFE6F -> true   // CJK 相容形式
        cp in 0xFF00..0xFF60 -> true   // 全形 ASCII
        cp in 0xFFE0..0xFFE6 -> true   // 全形符號
        cp >= 0x1F000 -> true           // emoji 等增補平面
        else -> false
    }

    private fun cleanSteps(steps: List<Int>): List<Int> =
        steps.map { it.coerceAtLeast(MIN_VALUE) }.distinct().sorted().take(MAX_STEPS_PER_GROUP)

    fun getGroups(context: Context): List<Group> {
        val p = prefs(context)
        val raw = p.getString(KEY_GROUPS, null)
        if (raw != null) {
            val parsed = raw.split('|').mapNotNull { seg ->
                val sep = seg.indexOf(':')
                if (sep < 0) return@mapNotNull null
                val name = sanitizeName(seg.substring(0, sep)).ifBlank { DEFAULT_GROUP_NAME }
                val steps = cleanSteps(
                    seg.substring(sep + 1).split(',').mapNotNull { it.trim().toIntOrNull() },
                )
                if (steps.isEmpty()) null else Group(name, steps)
            }.take(MAX_GROUPS)
            return parsed.ifEmpty { DEFAULT }
        }
        // 舊版扁平格式 → 單一「預設」組（不改寫儲存，下次 setGroups 自然轉新格式）
        val legacy = p.getString(KEY_LEGACY, null) ?: return DEFAULT
        val steps = cleanSteps(legacy.split(',').mapNotNull { it.trim().toIntOrNull() })
        return if (steps.isEmpty()) DEFAULT else listOf(Group(DEFAULT_GROUP_NAME, steps))
    }

    fun setGroups(context: Context, groups: List<Group>) {
        val clean = groups.map {
            Group(sanitizeName(it.name).ifBlank { DEFAULT_GROUP_NAME }, cleanSteps(it.steps))
        }.filter { it.steps.isNotEmpty() }.take(MAX_GROUPS).ifEmpty { DEFAULT }
        val raw = clean.joinToString("|") { "${it.name}:${it.steps.joinToString(",")}" }
        prefs(context).edit().putString(KEY_GROUPS, raw).apply()
    }

    /** 目前顯示中的分組。讀取端自己 coerce 到合法範圍（分組可能剛被刪掉）。 */
    fun getActiveIndex(context: Context): Int =
        prefs(context).getInt(KEY_ACTIVE, 0)

    fun setActiveIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_ACTIVE, index).apply()
    }

    /** 顯示排序方向：false＝由小到大（預設）。只影響顯示，儲存永遠由小到大。 */
    fun isSortDescending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SORT_DESC, false)

    fun setSortDescending(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SORT_DESC, value).apply()
    }

    private const val KEY_SORT_DESC = "step_test_manual_preset_sort_desc"
}
