package com.kizakiworks.treadless.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Treadless 的配色：Kizaki Works 家族的湖水綠（Lagoon）。
 */
val LagoonPrimary = Color(0xFF00A6A6)
val LagoonSecondary = Color(0xFF2DD4BF)
val LagoonTertiary = Color(0xFF38BDF8)
val Ink = Color(0xFF0F172A)
val Mist = Color(0xFFEFFAF8)
// 深色卡片底：帶湖水綠調的深青灰。原本的藍灰 0xFF111827 疊 0.72 透明度後
// 跟近黑背景幾乎同色，整頁層次全糊（深色實機截圖抓過）
val NightSurface = Color(0xFF152A2E)

private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = LagoonPrimary,
    onPrimary = Color.White,
    secondary = LagoonSecondary,
    tertiary = LagoonTertiary,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = LagoonSecondary,
    onPrimary = Ink,
    secondary = LagoonPrimary,
    tertiary = LagoonTertiary,
    background = Color(0xFF0B191C),
    onBackground = Color(0xFFE5FBF8),
    surface = NightSurface,
    onSurface = Color(0xFFE5FBF8),
)

@Composable
fun TreadlessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content,
    )
}
