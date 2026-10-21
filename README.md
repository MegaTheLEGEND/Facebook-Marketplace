# Facebook "Marketplace only" (Morphe patch)

Turns the Facebook Android app into a Marketplace-only app. It is a [Morphe](https://github.com/MorpheApp) patch: you don't decompile or recompile anything, Morphe patches the APK directly.

**Target:** `com.facebook.katana` **582.0.0.50.54** (plain APK, arm64). The patch is anchored on Redex-obfuscated class names, so it is tied to this exact version.

Not affiliated with Meta. For personal use; don't commit or redistribute Facebook's APK.

## What it does

| Feature | Option (default) | How |
|---|---|---|
| Opens on Marketplace | always on | Hooks the end of `X.2GO.A06(Z)V` (the tab bar controller's "determine starting tab"), finds the Marketplace tab by ID (`1606854132932955`, not list position) and selects it. |
| Hides the bottom tab bar | `hideTabBar` (on) | Same hook sets the tab bar view to `GONE`. |
| Locks navigation | `lockNavigation` (on) | No swiping between tabs (`X.25n`, the main tab ViewPager, never intercepts or handles touches); no left-hand Facebook menu (`X.24g` SlidingPanelScrollView: no edge swipe, "snap to panel" is a no-op, so the hamburger does nothing). |

| Links open in the system browser | `externalLinks` (on) | `X.dBh.A00` (the link launcher's WEBVIEW/EXTERNAL/CUSTOM_TABS decision) always returns EXTERNAL, same as Facebook's own "links open externally" setting. |

If Marketplace isn't in the account's tab list, or a lookup fails, the extension does nothing and Facebook opens normally.

See [TODO.md](TODO.md) for what's next.

## Status

| Piece | State |
|---|---|
| Opens on Marketplace, tab bar hidden | Works on device |
| Launch crash (`VerifyError`, register `v27` type `Conflict`) | Fixed. `A06` reuses its `this` register as scratch, so the patch captures `this` at method entry and the return hooks take no registers |
| External links | Written against the real dex (register counts, enum fields and signature checked), **not yet tested on device** |
| Navigation lock | Written against the real dex (register counts, signatures and callers checked), **not yet tested on device** |
| Extension logic | Unit-tested on a plain JVM (`tests/`) |

Known limits:
- The hamburger icon is still drawn; tapping it does nothing.
- Notification taps and deep links can still open other screens.
- No `compatibleWith` (the signing-cert hash isn't in the dex), so Morphe treats it as a universal patch; the fingerprints stop it patching the wrong thing.

## Build

Needs Java 21+ (Eclipse Temurin; Oracle JDK breaks Morphe's signing step), git, internet, and a GitHub **classic** token with only `read:packages` (the Morphe Gradle plugin lives on GitHub Packages).

Windows (cmd):
```
set GITHUB_ACTOR=your-username
set GITHUB_TOKEN=ghp_...
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Apk "C:\path\to\com.facebook.katana_582.0.0.50.54-....apk" -JavaExe "C:\Program Files\Eclipse Adoptium\jdk-21...\bin\java.exe"
```
Flags: `-KeepTabBar` (leave the bottom bar), `-KeepNavigation` (don't lock swiping / left menu), `-KeepInAppBrowser` (keep the in-app WebView).

Linux/macOS:
```
export GITHUB_ACTOR=your-username GITHUB_TOKEN=ghp_...
./build.sh /path/to/com.facebook.katana_582.0.0.50.54-....apk      # KEEP_TAB_BAR=1 / KEEP_NAVIGATION=1 / KEEP_IN_APP_BROWSER=1 to opt out
```

The scripts clone `MorpheApp/morphe-patches` into `work/`, copy `extensions/` and `patches/` in, build the bundle, download Morphe Desktop's CLI and patch a **copy** of your APK. Result: `work/facebook-patched.apk`. Uninstall the Play Store version first (it's re-signed) and sign in again.

Test it:
```
adb shell am force-stop com.facebook.katana & adb logcat -c
adb shell monkey -p com.facebook.katana -c android.intent.category.LAUNCHER 1
adb logcat | findstr /i "AndroidRuntime VerifyError katana"
```

## Layout

```
patches/     Kotlin: fingerprints + the "Marketplace only" patch
extensions/  Java: runtime logic injected into the app (MarketplaceOnly)
tests/       Plain-JVM unit tests for the extension (mock class shapes)
tools/       dexscan.py - find strings, callers, classes in the app's dex files
build.ps1 / build.sh
```

### Unit tests
```
cd tests && javac -d out $(find src -name '*.java') ../extensions/facebook/src/main/java/app/morphe/extension/facebook/marketplace/MarketplaceOnly.java && java -cp out Test
```

### Finding things in a new build (`tools/dexscan.py`)
Unzip the APK's `classes*.dex` into a folder, then:
```
python tools/dexscan.py dexdir strings "(?i)reels"          # regex over all strings
python tools/dexscan.py dexdir strref SomeExactString        # methods that reference it
python tools/dexscan.py dexdir callers "LX/24g;" A0E         # who calls a method
python tools/dexscan.py dexdir class "LX/25n;"               # fields + methods
python tools/dexscan.py dexdir dis "LX/24g;" A0E             # registers, refs; check free registers BEFORE injecting smali
python tools/dexscan.py dexdir subclasses "Landroidx/viewpager/widget/ViewPager;"
```
Rule learned the hard way: before injecting smali, check the method's register count and never read `p0` at a `return-void`; compilers reuse parameter registers.
