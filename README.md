# Facebook "Marketplace only" – Morphe patch

Target: `com.facebook.katana` **582.0.0.50.54** (plain APK). Morphe patches the APK directly; you don't decompile or recompile anything.

## What it does

1. **Opens on Marketplace.** Hooks the end of the tab bar controller's "determine starting tab" method, finds the Marketplace tab by ID (`1606854132932955`, not list position) and sets the controller's selected position and tab ID to it. Covers both the setup call and the reset call.
2. **Hides the bottom tab bar** (option `hideTabBar`, default on): Feed / Reels / Watch / Notifications / Menu aren't one tap away.
3. **Fails safe.** If Marketplace isn't in the account's tab list, or any lookup fails, it does nothing and Facebook opens normally. It cannot crash startup.

Not touched on purpose: login/session, Marketplace routes, messaging, listing creation, notifications, deep links.

## Build + patch in one command

Morphe Desktop only *applies* a finished `.mpp`; it doesn't compile patches. `build.ps1` / `build.sh` clone `MorpheApp/morphe-patches`, copy these sources in, run `:patches:buildAndroid` (the same task morphe-patches' own CI uses), download Morphe Desktop's CLI jar and patch a copy of your APK.

1. Java 21+ and git installed.
2. A GitHub token with only `read:packages` (https://github.com/settings/tokens). The Morphe Gradle plugin lives on GitHub Packages, which needs a login even for public reads. morphe-patches' `settings.gradle.kts` reads it from `GITHUB_ACTOR`/`GITHUB_TOKEN` (or Gradle properties `gpr.user`/`gpr.key`).
3. Windows (PowerShell):
   ```
   $env:GITHUB_ACTOR = "your-username"; $env:GITHUB_TOKEN = "ghp_..."
   .\build.ps1 -Apk "C:\path\to\com.facebook.katana_582.0.0.50.54-....apk"
   ```
   Linux/macOS:
   ```
   export GITHUB_ACTOR=your-username GITHUB_TOKEN=ghp_...
   ./build.sh /path/to/com.facebook.katana_582.0.0.50.54-....apk
   ```
Result: `work/facebook-patched.apk`. The built bundle is `work/morphe-patches/patches/build/libs/patches-*.mpp` if you'd rather load it in the Desktop GUI (Expert mode).

Install on a test device with a test account first. Keep the original APK. The patched APK is re-signed, so it can't update over a Play Store install; uninstall first and sign in again.

## What is and isn't verified

Checked directly against your 19 `classes*.dex` files (androguard):

| Check | Result |
|---|---|
| Anchor string `TabBarController.determineStartingTabPosition` | Exactly one method in all 19 dex files |
| That method | `private final A06(Z)V` in `LX/2GO;` (jadx's `C2GO`); 29 registers, so `p0` = v27, which is why the patch uses `invoke-static/range`; exactly one `return-void` |
| Names the extension reflects on | `2GO.A00(2GO)` public static → `1Hz`; `1Hz.A05()` → `ImmutableList`; `2GO.A01` int, `A02` long, `A09` ViewGroup; `TabTag.A03` public final long (on the abstract base class) |
| Marketplace tab ID `1606854132932955` | Used by `FBMarketplaceNativeModule.isMarketplaceTabCurrentlySelected`, `MarketplaceTabUriMapHelper`, `MarketplaceTabVisitFeatureExtractor` and the tab-availability switch in `2GO.A09` |
| Extension logic | Compiled and unit-tested on a plain JVM against mocks with the real class shape (`tests/`, 7 cases pass) |
| Kotlin patch + fingerprint | Rewritten to match the real API patterns in morphe-patches (`Fingerprint(... filters = listOf(string(...)))`, `bytecodePatch`, `booleanOption`, `extendWith`). **Not compiled** – the sandbox can't download Morphe's dependencies |
| Injected smali | Single instruction, **not assembled** here (no smali assembler offline) |
| Runs on a device / tab bar stays hidden | **Not tested** |

Known limits:
- **No `compatibleWith`.** morphe-patches' `Compatibility` also wants the app's signing-certificate SHA-256, which isn't in the dex files. Morphe Manager therefore treats this as a universal patch. The fingerprint is what stops it patching the wrong thing. If the CLI applies no patches, check whether it needs the patch enabled by name. I couldn't confirm how Desktop handles universal patches.
- Notification taps and deep links to Feed, Reels, etc. aren't blocked; they just have no tab bar to return from.
- Names (`2GO`, `A00`, `A05`, ...) are specific to 582.0.0.50.54.

## If the build fails

Send the Gradle error. Likely spots: a token problem, or a small Kotlin/DSL mismatch in the two files under `patches/src/main/kotlin/.../marketplace/`. What they must express:
- **Fingerprint:** `private final void (boolean)` containing the string `TabBarController.determineStartingTabPosition`.
- **Patch body:** before every `return-void` in it, insert  
  `invoke-static/range {p0 .. p0}, Lapp/morphe/extension/facebook/marketplace/MarketplaceOnly;->onStartingTabDeterminedHideBar(Ljava/lang/Object;)V`  
  (`onStartingTabDetermined` to leave the tab bar alone).

## Run the extension tests

```
cd tests && javac -d out $(find src -name '*.java') ../extensions/facebook/src/main/java/app/morphe/extension/facebook/marketplace/MarketplaceOnly.java && java -cp out Test
```
