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
| Blocks Reels / fullscreen video viewer | `blockReels` (on) | The launch chain `X.7rP.A01` -> `X.4Ok.A0C` -> `X.4Ok.A0B` -> `X.4Ok.A03` -> `ImmersiveActivity` (traced from a real logcat of tapping a profile reel) is cut: `7rP.A01`/`4Ok.A0A`/`4Ok.A03` return immediately, `4Ok.A0B` claims "handled" so callers don't fall back to the legacy player. `ImmersiveActivity` itself is NOT blocked, it hosts every full-screen surface incl. Marketplace item pages. |
| Reclaims the tab bar's space | part of `hideTabBar` | After hiding the bar the extension keeps it 0 high, hides full-width filler views on the bottom edge and clears bottom padding/margin equal to the bar's old height (a few passes over the first 3 s). Logs the view tree under tag `MarketplaceOnly`. |
| Back leaves the app | `marketplaceIsHome` (on) | In `onBackPressedInternal` the "home tab id" read from `X.1Hz.A02()` is replaced with Marketplace's id, so with nothing left to pop, back exits instead of switching to the Feed. |

If Marketplace isn't in the account's tab list, or a lookup fails, the extension does nothing and Facebook opens normally.

See [TODO.md](TODO.md) for what's next.

## Status

| Piece | State |
|---|---|
| Opens on Marketplace, tab bar hidden | Works on device |
| Launch crash (`VerifyError`, register `v27` type `Conflict`) | Fixed. `A06` reuses its `this` register as scratch, so the patch captures `this` at method entry and the return hooks take no registers |
| External links | **Works on device** (links open in the default browser) |
| Block Reels | **Works on device** (reels don't start, no audio) |
| Back leaves the app | Written from the dex (exactly one `1Hz.A02()` call in the method, same-size swap), **not yet tested on device** |
| Blank strip where the tab bar was | Reclaim logic written, unit-tested on a JVM with fake views, **not yet tested on device** |
| Navigation lock | **Works on device**: app launches, swipe and the side menu are blocked. The hamburger icon is still drawn (see TODO) |
| Extension logic | Unit-tested on a plain JVM (`tests/`) |

Known limits:
- The hamburger icon is still drawn; tapping it does nothing (planned: turn it into a refresh button). The blank strip fix is untested; if a strip remains, send `adb logcat -s MarketplaceOnly`.
- Occasionally returning to the app lands on the Facebook Home feed instead of Marketplace (not investigated yet, see TODO).
- Notification taps and deep links can still open other screens (a `fb://reel/...` link may land on a blank loading screen instead of a reel).
- Blocking the Reels viewer blocks Facebook's fullscreen video viewer generally, so a Marketplace listing video can't be opened full-screen either (inline playback is untouched).
- No `compatibleWith` (the signing-cert hash isn't in the dex), so Morphe treats it as a universal patch; the fingerprints stop it patching the wrong thing.

## Build

Needs Java 21+ (Eclipse Temurin; Oracle JDK breaks Morphe's signing step), git, internet, and a GitHub **classic** token with only `read:packages` (the Morphe Gradle plugin lives on GitHub Packages).

Windows (cmd):
```
set GITHUB_ACTOR=your-username
set GITHUB_TOKEN=ghp_...
powershell -ExecutionPolicy Bypass -File .\build.ps1 -Apk "C:\path\to\com.facebook.katana_582.0.0.50.54-....apk" -JavaExe "C:\Program Files\Eclipse Adoptium\jdk-21...\bin\java.exe"
```
Flags: `-KeepTabBar` (leave the bottom bar), `-KeepNavigation` (don't lock swiping / left menu), `-KeepInAppBrowser` (keep the in-app WebView), `-KeepReels` (don't block Reels / fullscreen video), `-KeepHomeBack` (back goes to Facebook Home).

Linux/macOS:
```
export GITHUB_ACTOR=your-username GITHUB_TOKEN=ghp_...
./build.sh /path/to/com.facebook.katana_582.0.0.50.54-....apk      # KEEP_TAB_BAR=1 / KEEP_NAVIGATION=1 / KEEP_IN_APP_BROWSER=1 / KEEP_REELS=1 / KEEP_HOME_BACK=1 to opt out
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
agent-tools/ Dex.java (dexlib2-based class/disassembly/strref/callers) + arsc.py (resource ids); see agent-tools/README.md
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
