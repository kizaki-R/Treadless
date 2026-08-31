package com.kizakiworks.stepcore

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 介面語言（繁中／英文），與系統語言無關的 App 內設定。
 *
 * 放在 :stepcore 是因為**前景服務的通知也要跟著切**——Service 拿不到
 * Activity 的 attachBaseContext 包裝，得自己用 [wrap] 取字串。
 */
object AppLocale {
    const val ZH = "zh"
    const val EN = "en"

    private const val PREFS = "treadless"
    private const val KEY = "app_language"

    fun get(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ZH) ?: ZH

    fun set(context: Context, lang: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, if (lang == EN) EN else ZH).apply()
    }

    private fun locale(context: Context): Locale =
        if (get(context) == EN) Locale.ENGLISH else Locale.TRADITIONAL_CHINESE

    /**
     * 依使用者選的語言包裝 context。
     * Activity 在 attachBaseContext 用；Service 取通知字串時用。
     */
    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale(base))
        return base.createConfigurationContext(config)
    }
}
