# 從原始碼建置

使用說明在 [README](../README.md)。這份是給要自己編譯或閱讀程式碼的人。

## 環境

| 項目 | 版本 |
|---|---|
| JDK | 17（Android Studio 內建的 JBR 即可） |
| Android SDK | compileSdk / targetSdk 36，minSdk 26 |
| Gradle | 由 wrapper 帶入，不需另外安裝 |

## 指令

```bash
# Debug APK
./gradlew :app:assembleDebug

# 單元測試（Health Connect 寫入分批與分組名稱截斷，21 條）
./gradlew :stepcore:testDebugUnitTest
```

改動 `HealthConnectManager` 的分批邏輯或 `ManualStepPresets` 的名稱處理後，
請務必跑一次單元測試。

## Release 建置與簽章

1. 產一把簽章金鑰（一次性，密碼自己設）：

   ```bash
   keytool -genkeypair -v -keystore <你的路徑>/treadless-release.jks \
     -alias treadless -keyalg RSA -keysize 4096 -validity 10000
   ```

2. 把 `keystore.properties.example` 複製成 `keystore.properties`（專案根目錄）
   並填入路徑與密碼。該檔已在 `.gitignore`，不會進版控。

3. ```bash
   ./gradlew :app:assembleRelease
   ```

   產出 `app/build/outputs/apk/release/app-release.apk`。開啟 R8，約 1.8 MB。
   簽章為 v2 + v3（v3 保留日後金鑰輪替的可能）。

沒有 `keystore.properties` 也建得起來，只是會產出 `app-release-unsigned.apk`。

> 發版時請保留 `app/build/outputs/mapping/release/mapping.txt` 並與 versionCode
> 對應存檔，否則使用者回報的 crash 堆疊無法還原成原始碼行號。
>
> R8 的問題全部是執行期才會發作，編譯通過不代表沒事。改依賴或改 proguard 規則
> 之後，請把五頁導覽、兩種模式、通知與所有彈窗都實機走一輪。

## 模組

| 模組 | 內容 |
|---|---|
| `:app` | 主畫面與首次啟動導覽（唯一的 Activity），UI 全部 Compose |
| `:stepcore` | 步數引擎：Health Connect 寫入與分批、前景服務、偏好設定、語言切換。不依賴 Compose |
| `:glassui` | 玻璃質感共用元件：背景模糊面板、邊緣折射與高光、藥丸滑動切換、內容透鏡 |

主要依賴：Jetpack Compose（BOM 2024.12.01）、Material 3、
[Haze](https://github.com/chrisbanes/haze) 1.7.2（即時背景模糊）、
Health Connect client 1.1.0-rc02。

> **【雷】Compose BOM 固定在 2024.12.01（1.7.x）**：`FlowRow`、
> `BasicText(autoSize)` 這些 1.8 才有的 API 會在執行期 `NoSuchMethodError` 閃退。
> 要升 BOM 請連 Haze 一起獨立驗證一輪。

## 幾個設計決策

- **前景服務型別用 `specialUse`**：`dataSync` 在 Android 15 以上有 6 小時上限。
- **今日累計不用 AlarmManager**：每次讀寫時比對 `epochDay` 順手翻頁，比排鬧鐘
  可靠也省電，App 沒開也不會漏掉換日。
- **寫入間隔下限 10 秒**：實測讀取端的輪詢週期，再短沒有意義。
  單一來源在 `StepTestStore.INTERVAL_MIN`。
- **語言不跟隨系統**：存在偏好設定裡，`attachBaseContext` 包裝 locale；前景服務
  拿不到 Activity 的包裝，另外用同一份設定取通知字串。
- **分組名稱限 4 個半形位**：依顯示寬度截斷（2 個中文字或 4 個英數），
  這是 6 顆切換鍵排在同一列的版面約束。
- **玻璃 shader 是選配**：AGSL `RuntimeShader` 需要 API 33，以下版本走降級路徑
  （裁切 + 高光層），仍然是半透明玻璃，只是少了折射與透鏡。

## 新增 UI 字串

字串必須**同時**加進 `app/src/main/res/values/strings.xml`（繁體中文）與
`values-en/strings.xml`（英文）。英文的語感規則寫在 `values-en/strings.xml`
的檔頭註解，請照那一套走。
