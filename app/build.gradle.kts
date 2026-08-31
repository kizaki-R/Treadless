import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 簽章金鑰絕不進版控：路徑與密碼放 keystore.properties（已 gitignore）。
// 檔案不存在時 release 仍然建得起來，只是產出 unsigned APK——
// 這樣別人 clone 下來不用先弄金鑰也能編譯，而漏簽章會很明顯（檔名帶 unsigned）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.kizakiworks.treadless"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.kizakiworks.treadless"
        minSdk = 26
        targetSdk = 36
        // 【雷】側載分發沒有商店把關版本，versionCode 只能往上加、不可重用，
        // 否則裝置會以「同版本」為由拒絕更新。
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v1 不需要（minSdk 26 > API 24）；v3 要開——它是側載唯一的
                // 金鑰輪替後路，沒有它換金鑰就得叫所有人解除安裝重裝（資料全消）
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 沒有 keystore.properties 就留 null → 產出 app-release-unsigned.apk
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 刻意精簡：沒有地圖、沒有 GPX，每多一個依賴都是白背的體積。
// Haze 由 :glassui 以 api 帶入（玻璃元件簽章需要 HazeState）。
dependencies {
    implementation(project(":stepcore"))
    implementation(project(":glassui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
