plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.kizakiworks.stepcore"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 刻意不依賴 Compose：這層只有引擎（Health Connect 寫入 + 前景服務 + 設定存取），
// 介面由 :app 與 :stepapp 各自實作（兩者的 UI 形態差太多，硬共用只會綁死彼此）。
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    api(libs.androidx.health.connect)

    testImplementation(libs.junit)
}
