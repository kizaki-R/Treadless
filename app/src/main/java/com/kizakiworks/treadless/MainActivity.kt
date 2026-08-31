package com.kizakiworks.treadless

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kizakiworks.stepcore.AppLocale
import com.kizakiworks.stepcore.StepTestStore
import com.kizakiworks.treadless.ui.OnboardingScreen
import com.kizakiworks.treadless.ui.StepHomeScreen
import com.kizakiworks.treadless.ui.theme.TreadlessTheme

class MainActivity : ComponentActivity() {
    /** 介面語言與系統語言脫鉤：以 App 內設定包裝 context（語言彈窗切換後 recreate 生效）。 */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 導航列 scrim 全關，否則底部會多一條半透明對比保護膜（enableEdgeToEdge 預設會墊）
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            TreadlessTheme {
                // 【雷】整棵樹要包在 Surface 裡：裸 Box 不提供 LocalContentColor，
                // 沒指定 color 的 Text 會退回預設黑字——淺色看不出來，
                // 深色下標題整片變黑（實機截圖抓過）
                Surface(color = MaterialTheme.colorScheme.background) {
                // 首次啟動先走導覽（看完或略過後寫入旗標，之後直進主畫面）
                var onboarded by remember { mutableStateOf(StepTestStore.isOnboarded(this)) }
                Crossfade(targetState = onboarded, label = "rootSwitch") { done ->
                    if (done) {
                        StepHomeScreen()
                    } else {
                        OnboardingScreen(
                            onFinish = {
                                StepTestStore.setOnboarded(this, true)
                                onboarded = true
                            },
                        )
                    }
                }
                }
            }
        }
    }
}
