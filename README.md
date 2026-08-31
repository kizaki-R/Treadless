# Treadless（踏跡）

把設定好的步數，定時或按一下寫進 **Health Connect** 的 Android 工具。

Kotlin + Jetpack Compose + Material 3，介面用自製的 Liquid Glass（液態玻璃）元件。
**完全沒有任何定位權限**，和模擬 GPS 無關。

| 首次啟動導覽 | 自動模式 |
|---|---|
| <img src="docs/screenshots/onboarding.png" width="280"> | <img src="docs/screenshots/auto-mode.png" width="280"> |

---

## 這是什麼 / 不是什麼

**是**：一支把步數（與可選的距離）寫進 Health Connect 的紀錄產生器。你決定
每分鐘幾步、多久寫一次，它就照做；或是在手動模式按一下寫入指定的數量。

**不是**：

- **不是模擬 GPS**。這支 App 的 `AndroidManifest` 裡沒有任何定位權限，也不碰
  mock location。它只寫 Health Connect 的 `StepsRecord` / `DistanceRecord`。
- **不讀取你的健康資料**。只申請寫入權限，沒有讀取權限。
- **不上架 Google Play**，側載（side-load）分發。

## ⚠️ 使用前請讀

寫進 Health Connect 的是**真實的健康記錄**，其他讀取 Health Connect 的 App
（健身、保險、遊戲）都會看到，而且**本 App 無法刪除已寫入的資料**——要刪得去
Health Connect 自己的資料管理頁。

用它去餵任何以步數計算獎勵的服務，**可能違反該服務的使用條款**，後果（包含帳號
處置）由你自己承擔。數值設得越離譜風險越高。這支工具不對此提供任何保證。

---

## 功能

- **自動模式** — 設定「每分鐘步數」與「寫入間隔」，前景服務按時寫入。常駐通知
  顯示本次累計與下次寫入倒數，可直接從通知停止。
- **手動模式** — 快捷步數**分組制**：最多 6 組（自訂 4 半形位的短名、可排序），
  每組最多 5 個數值（儲存時自動去重並由小到大，顯示方向可切換）。按一下寫入。
- **寫入前確認** — 預設開啟。寫的是真實健康記錄，誤觸代價大於多按一下。
- **寫完自動開啟指定 App** — 可選，延遲 0.5–3.0 秒，內建含圖示的 App 選擇器。
- **今日累計** — 「今天寫了多少」，午夜自動歸零（比對日期，不用鬧鐘，App 沒開
  也不會漏），可手動重置。
- **同步寫入距離** — 可選。用步長（0.30–1.50 m）換算成 `DistanceRecord`。
- **首次啟動導覽** — 五頁：歡迎（含語言選擇）／模式介紹／Health Connect 授權／
  通知／電池。權限頁用會動的示意卡演示該怎麼操作，並輪詢偵測是否完成。
- **雙語** — 繁體中文與 English，App 內即時切換（不跟隨系統語言），
  前景服務通知也一起切。

## 需求

| 項目 | 版本 |
|---|---|
| Android | 8.0（API 26）以上 |
| [Health Connect](https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata) | 需安裝（Android 14 以上為系統內建） |
| 液態玻璃的折射與透鏡效果 | Android 13（API 33）以上；以下版本自動降級為半透明玻璃 |

## 安裝

1. 從 [Releases](../../releases) 下載 `app-release.apk`。
2. 允許你的瀏覽器或檔案管理員「安裝未知來源的應用程式」。
3. 安裝後開啟，照導覽把 Health Connect 的**步數**與**距離**寫入權限打開。

> **更新時**：只要是同一把金鑰簽的版本就能直接覆蓋安裝。若你自行從原始碼建置過
> 又換了金鑰，必須先解除安裝（**App 內的設定會全部消失**）。

---

## 使用說明

### 首次啟動

導覽第一頁就能選介面語言（繁體中文／English），選了會立刻套用。後面三頁分別引導
你開啟 Health Connect 寫入權限、通知權限、以及把電池用量設為「不受限制」。

三項都可以「稍後再說」跳過，主畫面的「尚待設定」卡片會持續提醒，點一下就跳到對應
的設定頁。

### 自動模式

1. **每分鐘步數** — 一分鐘寫入幾步。一般走路約 100、快走約 130。
2. **寫入間隔** — 多久寫一次（10–3600 秒）。間隔不影響總量，只影響「分幾次寫」。
3. **同步寫入距離** — 開啟後同時寫 `DistanceRecord`，齒輪可調步長。
4. 按 **開始自動寫入**。狀態列會顯示下次寫入倒數，常駐通知同步顯示並提供停止鈕。

> 長時間掛機請把電池用量設為「不受限制」，否則系統可能中斷背景寫入。

### 手動模式

上方是**分組切換鍵**，下方是該組的快捷數值。

- 點任一數值 → （預設會先跳確認）→ 寫入 Health Connect。
- **✎ 編輯鍵** — 改組名（限 4 個半形位＝2 個中文字或 4 個英數）、改數值、
  調整分組順序、刪除分組。空白的數值欄位會被忽略。
- **↑↓ 鍵** — 切換數值由小到大或由大到小顯示。
- **＋** — 新增分組（最多 6 組）。

寫入頻率上限為每秒一次，連點請間隔一秒。

### 設定

| 項目 | 說明 |
|---|---|
| 寫入前確認 | 顯示即將寫入的步數並要求確認。預設開啟。 |
| 寫完自動開啟 App | 寫入成功後延遲指定秒數開啟指定 App。 |
| 語言 | 主畫面左上角的「文A」鍵。切換後 App 會重建以套用。 |
| 今日累計重置 | 主畫面數字右側的 ↻。只影響本 App 的計數，**不會刪除已寫入 Health Connect 的資料**。 |

### 要刪掉寫進去的資料？

本 App 沒有刪除功能（也沒有讀取權限）。請開啟 Health Connect →
資料和存取權 → 活動 → 步數 → 刪除。

---

## 建置

需要 JDK 17（Android Studio 內建的 JBR 即可）與 Android SDK 36。

```bash
# Debug
./gradlew :app:assembleDebug

# 單元測試（Health Connect 寫入分批邏輯，21 條）
./gradlew :stepcore:testDebugUnitTest
```

### Release 建置與簽章

1. 產一把簽章金鑰：

   ```bash
   keytool -genkeypair -v -keystore <你的路徑>/treadless-release.jks \
     -alias treadless -keyalg RSA -keysize 4096 -validity 10000
   ```

2. 把 `keystore.properties.example` 複製成 `keystore.properties`（專案根目錄）
   並填入路徑與密碼。該檔已在 `.gitignore`，不會進版控。

3. ```bash
   ./gradlew :app:assembleRelease
   ```

   產出 `app/build/outputs/apk/release/app-release.apk`（開啟 R8，約 1.8 MB）。

沒有 `keystore.properties` 也建得起來，只是會產出 `app-release-unsigned.apk`。

> 發版時請保留 `app/build/outputs/mapping/release/mapping.txt` 並與 versionCode
> 對應存檔，否則使用者回報的 crash 堆疊無法還原。

---

## 專案結構

| 模組 | 內容 |
|---|---|
| `:app` | 主畫面與首次啟動導覽（唯一的 Activity），UI 全部 Compose |
| `:stepcore` | 步數引擎：Health Connect 寫入與分批、前景服務、偏好設定、語言切換。不依賴 Compose |
| `:glassui` | Liquid Glass 共用元件：背景模糊玻璃面板、邊緣折射與 Fresnel 高光、藥丸滑動切換、內容透鏡 |

主要依賴：Jetpack Compose（BOM 2024.12.01）、Material 3、
[Haze](https://github.com/chrisbanes/haze) 1.7.2（即時背景模糊）、
Health Connect client 1.1.0-rc02。

### 幾個設計決策

- **前景服務型別用 `specialUse`**：`dataSync` 在 Android 15 以上有 6 小時上限。
- **今日累計不用 AlarmManager**：每次讀寫時比對 `epochDay` 順手翻頁，比排鬧鐘
  可靠也省電，App 沒開也不會漏掉換日。
- **寫入間隔下限 10 秒**：實測讀取端的輪詢週期，再短也沒有意義。
- **語言不跟隨系統**：存在偏好設定裡，`attachBaseContext` 包裝 locale；前景服務
  另外用同一份設定取通知字串。
- **液態玻璃的 shader 是選配**：AGSL `RuntimeShader` 需要 API 33，以下版本走
  降級路徑（裁切 + 高光層），仍然是好看的半透明玻璃。

---

## 已知限制

- 分組的預設名稱（例如「預設」）存在使用者資料裡，**不會隨介面語言切換**，
  英文使用者需自行改名。
- 螢幕關閉且未接電源時，系統的 Doze 可能延後背景寫入。長時間掛機請設定
  電池用量為「不受限制」。
- 本 App 不提供刪除已寫入資料的功能。

---

## License

尚未指定授權條款，因此預設保留所有權利。若你想在自己的專案裡使用這裡的程式碼
（特別是 `:glassui` 的液態玻璃元件），請先開 issue 詢問。

---

## English

**Treadless** writes a chosen number of steps into Android **Health Connect** —
either on a schedule (Auto mode) or with a single tap (Manual mode). Built with
Kotlin, Jetpack Compose and Material 3, with a custom Liquid Glass UI kit.

**It requests no location permissions of any kind** and has nothing to do with
GPS spoofing. It only writes `StepsRecord` / `DistanceRecord`; it never reads
your health data.

**Please read this first.** What it writes are real health records, visible to
every other app that reads Health Connect, and Treadless cannot delete them —
use Health Connect's own data management screen for that. Using this to feed any
service that rewards step counts may violate that service's terms of use; the
consequences, account action included, are yours to carry.

Requires Android 8.0+ and the Health Connect app (built in on Android 14+).
Refraction and lens effects need Android 13+; older versions fall back to a
plain translucent glass look. Grab the APK from
[Releases](../../releases), or build it yourself — see the build section above
(the commands are the same; `keystore.properties.example` explains signing).

The UI ships in Traditional Chinese and English, switchable inside the app.
