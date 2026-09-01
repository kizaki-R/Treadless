# Building from source

Usage lives in the [README](../README.md). This page is for people compiling
the app or reading the code.

## Toolchain

| | |
|---|---|
| JDK | 17 (the JBR bundled with Android Studio is fine) |
| Android SDK | compileSdk and targetSdk 36, minSdk 26 |
| Gradle | supplied by the wrapper, nothing to install |

## Tasks

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests: Health Connect write batching, group-name truncation,
# and migration from the old storage format. 26 of them.
./gradlew :stepcore:testDebugUnitTest
```

Run the tests after touching the batching logic in `HealthConnectManager` or
anything in `ManualStepPresets`.

## Release builds and signing

1. Create a signing key. You choose the password:

   ```bash
   keytool -genkeypair -v -keystore <your-path>/treadless-release.jks \
     -alias treadless -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy `keystore.properties.example` to `keystore.properties` in the project
   root and fill in the path and password. That file is gitignored.

3. ```bash
   ./gradlew :app:assembleRelease
   ```

   Produces `app/build/outputs/apk/release/app-release.apk`, around 1.8 MB with
   R8 enabled, signed with schemes v2 and v3. v3 is there to leave key rotation
   possible later.

Without `keystore.properties` the build still works; it just produces
`app-release-unsigned.apk`.

> Keep `app/build/outputs/mapping/release/mapping.txt` for every release you
> publish, filed against its versionCode. Without it a crash report from a user
> cannot be mapped back to source lines.
>
> Everything R8 breaks, it breaks at runtime — a clean compile proves nothing.
> After changing a dependency or a ProGuard rule, walk the whole app on a real
> device: the five walkthrough pages, both modes, the notification, every dialog.

## Modules

| Module | What it holds |
|---|---|
| `:app` | The main screen and the first-run walkthrough. One activity, all Compose |
| `:stepcore` | The step engine: batched Health Connect writes, the foreground service, preferences, language switching. No Compose dependency |
| `:glassui` | Shared glass components: blurred backdrop panel, edge refraction and highlights, sliding pill switcher, content lens |

Main dependencies: Jetpack Compose (BOM 2024.12.01), Material 3,
[Haze](https://github.com/chrisbanes/haze) 1.7.2 for live background blur, and
Health Connect client 1.1.0-rc02.

> **The Compose BOM is pinned to 2024.12.01 (1.7.x) on purpose.** APIs that only
> arrived in 1.8, `FlowRow` and `BasicText(autoSize)` among them, throw
> `NoSuchMethodError` at runtime here. Raising the BOM means verifying it
> together with Haze, as its own round of work.

## A few decisions worth knowing

- **The foreground service type is `specialUse`.** `dataSync` is capped at six
  hours from Android 15 onward.
- **Today's total does not use AlarmManager.** Every read and write compares
  `epochDay` and rolls the counter over in passing, which is both more reliable
  and cheaper than scheduling an alarm, and it survives the app never being
  opened across midnight.
- **The write interval bottoms out at 10 seconds**, measured against how often
  the reading side actually polls. Anything shorter buys nothing.
  `StepTestStore.INTERVAL_MIN` is the single source of that number.
- **Language does not follow the system.** It lives in preferences and
  `attachBaseContext` wraps the locale. The foreground service cannot reach that
  wrapper, so it reads the same preference itself when building notification
  text.
- **Group names are capped at 4 half-width units**, truncated by display width,
  so two CJK characters or four alphanumerics. That limit exists because six
  switcher buttons have to fit on one row.
- **The glass shaders are optional.** AGSL `RuntimeShader` needs API 33; below
  that the components fall back to clipping plus a highlight layer, which still
  looks like translucent glass, only without refraction or the lens.

## Adding UI strings

Every string goes into **both** `app/src/main/res/values/strings.xml`
(Traditional Chinese) and `values-en/strings.xml` (English). The house rules for
the English wording are in the header comment of `values-en/strings.xml`; follow
those.
