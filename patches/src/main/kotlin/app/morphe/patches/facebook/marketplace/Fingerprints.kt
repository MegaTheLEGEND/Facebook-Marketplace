package app.morphe.patches.facebook.marketplace

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

/**
 * Facebook 582.0.0.50.54: `LX/2GO;->A06(Z)V` (jadx shows the class as `C2GO`), the tab bar
 * controller's "determine starting tab" step.
 *
 * Checked against the real classes2.dex of this build:
 *  - `private final A06(Z)V` in `LX/2GO;`, 29 registers (p0 = v27, p1 = v28), exactly one `return-void`
 *  - the const-string below occurs in exactly one method across all 19 dex files
 *
 * Anchored on the method's own tracing string rather than the obfuscated names, so a renamed
 * class in a later build still matches as long as the string and signature are unchanged.
 */
internal object DetermineStartingTabFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PRIVATE, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        string("TabBarController.determineStartingTabPosition")
    )
)

// ---------------------------------------------------------------------------------------------
// Navigation lock. All of these are class+name anchored (the class names are Redex-obfuscated and
// specific to 582.0.0.50.54). Each target has at least one free local register (v0) at entry,
// verified in the dex, so `const/4 v0, 0x0` + `return v0` is legal at index 0.
// ---------------------------------------------------------------------------------------------

/** `X.25n` = the main tab ViewPager (extends X.fw3 -> X.fw2 -> androidx ViewPager). 9 locals. */
internal object TabPagerInterceptTouchFingerprint : Fingerprint(
    definingClass = "LX/25n;",
    name = "onInterceptTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)

/** Nested-scroll hand-off that lets a child list drag the pager sideways. */
internal object TabPagerStartNestedScrollFingerprint : Fingerprint(
    definingClass = "LX/25n;",
    name = "onStartNestedScroll",
    returnType = "Z",
    parameters = listOf("Landroid/view/View;", "Landroid/view/View;", "I")
)

/** `X.24g` = SlidingPanelScrollView, the HorizontalScrollView that hosts the left Facebook menu. */
internal object SlidingPanelInterceptTouchFingerprint : Fingerprint(
    definingClass = "LX/24g;",
    name = "onInterceptTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)

internal object SlidingPanelTouchFingerprint : Fingerprint(
    definingClass = "LX/24g;",
    name = "onTouchEvent",
    returnType = "Z",
    parameters = listOf("Landroid/view/MotionEvent;")
)

/**
 * `X.24g.A0E(panelId, reason, animate)` = "snap to panel by id". The hamburger button's click
 * runnable (X.2Eb) calls it with "bookmarks_panel"; back-press, the React Native
 * openSlidingPanel module and X.23K.A0R call it too. 5 locals, void.
 */
internal object SlidingPanelOpenFingerprint : Fingerprint(
    definingClass = "LX/24g;",
    name = "A0E",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Z")
)

// ---------------------------------------------------------------------------------------------
// External links (no in-app WebView browser)
// ---------------------------------------------------------------------------------------------

/**
 * `X.dBh.A00(Intent, FbUserSession, dBh, ArrayList)` returns `Pair<dqt, 5vy>`: how to open a link
 * (`dqt`: A03 = WEBVIEW, A02 = EXTERNAL, A01 = CUSTOM_TABS) plus the reason (`5vy`; A06 =
 * BROWSER_IPC_FORCE_EXTERNAL_BROWSER). dBh.A04 (called from every A07/A08/A0A link entry point)
 * branches on it. Returning (EXTERNAL, FORCE_EXTERNAL) is exactly what Facebook's own
 * "links open externally" setting produces. Static, 3 locals (v0..v2 free), no try blocks.
 */
internal object LinkLaunchModeFingerprint : Fingerprint(
    definingClass = "LX/dBh;",
    name = "A00",
    returnType = "Landroid/util/Pair;",
    parameters = listOf(
        "Landroid/content/Intent;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/dBh;",
        "Ljava/util/ArrayList;"
    )
)

// ---------------------------------------------------------------------------------------------
// Reels / fullscreen video viewer ("Warion" watch entry point)
// ---------------------------------------------------------------------------------------------
// Traced from a real logcat (tapping a reel on a profile) and the dex:
//   fb4a.WarionEntryPointHelper (X.7rP.A01) -> X.4Ok.A0C -> X.4Ok.A0B -> X.4Ok.A03
//   -> startActivity(com.facebook.katana.immersiveactivity.ImmersiveActivity) hosting ReelsVddLayout.
// Feed/profile reel taps reach 7rP.A01 via AFP.A03 -> AFV.A01 -> AFc.DGA; deep links, notifications and
// the legacy fullscreen player reach 4Ok.A0B/A0C directly. ImmersiveActivity itself is the generic host
// for ALL full-screen Facebook surfaces (Marketplace item pages included), so it must NOT be blocked.

/** "Start the video/reel viewer": also pre-starts the player (audio) before launching, so block it too. */
internal object WarionEntryPointFingerprint : Fingerprint(
    definingClass = "LX/7rP;",
    name = "A01",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/93e;",
        "I"
    )
)

/** Decision + launch, returns "handled". 28 locals. The fallback inside it starts the legacy FullscreenVideoPlayerActivity. */
internal object WatchEntryPointLaunchFingerprint : Fingerprint(
    definingClass = "LX/4Ok;",
    name = "A0B",
    returnType = "Z",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/9nG;",
        "LX/93e;",
        "I",
        "Z"
    )
)

internal object WatchEntryPointLaunchAltFingerprint : Fingerprint(
    definingClass = "LX/4Ok;",
    name = "A0A",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/Vgf;",
        "LX/93e;",
        "Z",
        "Z"
    )
)

/** Static: the code that actually builds the intent and starts the immersive Warion activity. */
internal object WatchEntryPointStartFingerprint : Fingerprint(
    definingClass = "LX/4Ok;",
    name = "A03",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/facebook/auth/usersession/FbUserSession;",
        "LX/93e;",
        "Z",
        "Z",
        "Z"
    )
)

// ---------------------------------------------------------------------------------------------
// Back button
// ---------------------------------------------------------------------------------------------

/**
 * `FbMainTabActivityDelegate.onBackPressedInternal(J)Z`. After the tab's own stack declines the press and
 * the fragment backstack is empty, it switches to the "home tab" (target id = `X.1Hz.A02()J`, 2392950137 or
 * 4748854339) unless the current tab already has that id, and only then tries `moveTaskToBack` (log tag
 * `back_press_pop_tab_backstack` for the switch). With Marketplace as the only tab that put you on Feed.
 */
internal object BackPressedFingerprint : Fingerprint(
    definingClass = "Lcom/facebook/katana/activity/FbMainTabActivityDelegate;",
    name = "onBackPressedInternal",
    returnType = "Z",
    parameters = listOf("J")
)

// ---------------------------------------------------------------------------------------------
// Returning to the app after a while ("reset to feed in background")
// ---------------------------------------------------------------------------------------------

/**
 * `X.2FU.A06(Intent, Bundle, X.2Vz, String, int index)`: the single choke point every tab switch goes
 * through (verified callers: `X.23K.A0T`, `X.2GO.A05/A0I`, `FbMainTabActivityDelegate.setSelectedTab` /
 * `onBackPressedInternal`, and two runnables). Facebook's return-to-feed routine
 * (`X.2Nb.A03` -> `X.23K.A0T(feedTabId, ...)`, log key `reset_to_feed_in_background`, pref
 * `r2f_force_feed_destination`) ends up here with the Feed tab's index when you come back after being
 * away. 22 registers: p0 = v16 ... p5 (index) = v21, all valid at entry. Unique in 2FU.
 */
internal object TabPagerSelectFingerprint : Fingerprint(
    definingClass = "LX/2FU;",
    name = "A06",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Intent;",
        "Landroid/os/Bundle;",
        "LX/2Vz;",
        "Ljava/lang/String;",
        "I"
    )
)

// ---------------------------------------------------------------------------------------------
// Dark mode (Facebook's own "Dark mode" setting)
// ---------------------------------------------------------------------------------------------
// `X.1Mi` is the theme manager. Its field `A00` is a `com.facebook.prefs.theme.ThemePreferences$State`
// enum: `A03` = ON (state int 1), `A02` = OFF (2), `A04` = SYSTEM (3). The setting is stored in two
// prefs keys (`dark_mode_v3` / `app_level_dark_mode_setting`), and ONLY `X.1Mi` reads or writes them
// (checked: the only `sget` of `X.1Mz.A02` / `X.1Mz.A1w` are in `1Mi.<init>` and `1Mi.A04`).
// Every activity decides light vs dark through `X.1Mi.A05()Z` (e.g. `onApplyThemeResource` calls it and
// applies style 0x7f.. accordingly). For the main app (`X.02z.A03` = FB4A) `A05` returns
// `A00 == ON` (or follows the OS theme when `A00 == SYSTEM`), so pinning `A00` to ON is the same as
// the user picking "On" in Facebook's own settings; nothing else needs touching, and with `A00` no
// longer SYSTEM the OS-theme-change recreate logic in `1Mi.A03` / `1PB` stays idle.

/**
 * `X.1Mi.<init>(FbUserSession)`: reads the saved setting and stores the matching State enum into
 * `A00`. There are three `iput-object A00` in it (one inline, two out-of-line arms), each storing a
 * State constant that is already in a register; the patch loads `State.ON` into that same register
 * just before each store.
 */
internal object ThemeManagerInitFingerprint : Fingerprint(
    definingClass = "LX/1Mi;",
    name = "<init>",
    parameters = listOf("Lcom/facebook/auth/usersession/FbUserSession;")
)

/**
 * `X.1Mi.A04(State)V`: the setter the settings screen calls. Saves the state int to both prefs keys,
 * stores it in `A00`, then fires a change callback. 8 registers: p0 = v6, p1 (the State) = v7.
 */
internal object ThemeManagerSetStateFingerprint : Fingerprint(
    definingClass = "LX/1Mi;",
    name = "A04",
    returnType = "V",
    parameters = listOf("Lcom/facebook/prefs/theme/ThemePreferences\$State;")
)

// Making the decision unconditional (first attempt, setting pinned to ON only, was not enough on device).
// Reading `X.1Mi.A05()` with real jump targets: for FB4A the ON arm still has to pass `A02` (the
// "can use dark mode" flag) and is skipped entirely by the end-to-end-test branch, and the per-activity
// `getThemeMode()` (`X.1Of`: DEFAULT / FORCE_DARK / FORCE_LIGHT, ordinal 0/1/2) can force light in
// `X.fvr.onApplyThemeResource`, which then calls `X.1Mi.A03(theme, isDark)` with `false`.
// `A03` applies style 0x7f1... `2132804140` (dark overlay) when `isDark`, else `2132804145` (light).
// FDS / Litho components decide separately through `X.25R.A02(Context)`, which reads a boolean attribute
// of the context's theme (cached per Theme), or the explicit flag of an `X.2F0` context wrapper.

/** `X.1Mi.A05()Z` = "is dark mode?". 3 registers: v0/v1 free locals, p0 = v2. */
internal object ThemeIsDarkFingerprint : Fingerprint(
    definingClass = "LX/1Mi;",
    name = "A05",
    returnType = "Z",
    parameters = listOf()
)

/** `X.1Mi.A03(Theme, boolean isDark)V` applies the dark or light style. 6 registers: p0 = v3, p1 = v4, p2 = v5. */
internal object ThemeApplyFingerprint : Fingerprint(
    definingClass = "LX/1Mi;",
    name = "A03",
    returnType = "V",
    parameters = listOf("Landroid/content/res/Resources\$Theme;", "Z")
)

/** `X.25R.A02(Context)Z` (static) = the FDS theme resolver's "is dark?". 7 registers: v0..v5 free, p0 = v6. */
internal object FdsIsDarkFingerprint : Fingerprint(
    definingClass = "LX/25R;",
    name = "A02",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;")
)


// ---------------------------------------------------------------------------------------------
// Group suggestions ("Recommended groups" on Marketplace search, "Related groups" on a listing page).
//
// Both screens are React Native (routes MarketplaceSearchRoute / MarketplaceSearchHubRoute and
// MarketplaceProductDetailsNonIPadRoute in assets/react_native_routes.json); the sections are drawn by JS in the
// Hermes bundle (assets/Fb4aBundle.js.hbc.spo), which is not patchable. Facebook calls the unit GYSJ ("Groups You
// Should Join"). The JS decides what to render from MobileConfig flags, read through the Java RN module
// `com.facebook.catalyst.modules.mobileconfignative.MobileConfigNativeModule` (`X.gjH` subclass, dex 6). Flags that
// matter, all in assets/ReactMobileConfigMetadata.json (specifiers in assets/rn_params.txt / rn_params_u4.txt):
//   mp_pathing_lth:disable_mp_search_nullstate_gysj   Facebook's own kill switch for the search null state block
//   groups_yakima_gysj:enable_gysj_unit_rendering     GYSJ units in Marketplace's Yakima feed framework (default TRUE)
//   mp_serp_h1_2026:*groups_insights*                 groups module on search results
//   group_discussion_mp_pdp:* / group_discussion_mp_feed:*   group discussion modules on listing page / feed
// The patch filters the boolean result of the six getBool* methods. Read paths (all checked in the dex):
//   by name:  getBool(String) / getBoolWithoutLogging(String)  ->  `X.82q.A0I(1Ed, name)Z`, then `move-result v0;
//             return v0`. v2 (= p1, the name) is never written, so it is still the name at the return.
//   by spec:  getBool{,WithoutLogging}ForStableSpec(DD) and ...WithContextualId(DDString): `MobileConfigNativeModule.A00(DD)J`
//             packs `(long)lo | (long)hi << 32` into a wide register pair (v2:v3, or v3:v4 for the contextual
//             ones), which is only read afterwards, so it is still the spec at the final `return`.
// A spec is the hex number in rn_params*.txt; those of default-true flags carry a 0x20 marker in the top byte.
// ---------------------------------------------------------------------------------------------

private const val MOBILE_CONFIG_MODULE =
    "Lcom/facebook/catalyst/modules/mobileconfignative/MobileConfigNativeModule;"

/** `getBool(String)Z`: 3 registers, v0 = result, v1 = scratch (was `this`), p1 = v2 = the flag name. */
internal object MobileConfigGetBoolFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBool",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

/** `getBoolWithoutLogging(String)Z`: same shape as [MobileConfigGetBoolFingerprint]. */
internal object MobileConfigGetBoolWithoutLoggingFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBoolWithoutLogging",
    returnType = "Z",
    parameters = listOf("Ljava/lang/String;")
)

/** `getBoolForStableSpec(DD)Z`: 5 registers, no locals; `move-result-wide v2` = spec in v2:v3, result in v0. */
internal object MobileConfigGetBoolForStableSpecFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBoolForStableSpec",
    returnType = "Z",
    parameters = listOf("D", "D")
)

/** `getBoolWithoutLoggingForStableSpec(DD)Z`: same shape as [MobileConfigGetBoolForStableSpecFingerprint]. */
internal object MobileConfigGetBoolWithoutLoggingForStableSpecFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBoolWithoutLoggingForStableSpec",
    returnType = "Z",
    parameters = listOf("D", "D")
)

/**
 * `getBoolWithContextualId(DDString)Z`: 8 registers; `move-result-wide v3` = spec in v3:v4 (never written again);
 * two `return`s, the early one (`return v1`, spec <= 0) is left alone, the filter goes before the last one.
 */
internal object MobileConfigGetBoolWithContextualIdFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBoolWithContextualId",
    returnType = "Z",
    parameters = listOf("D", "D", "Ljava/lang/String;")
)

/** `getBoolWithoutLoggingWithContextualId(DDString)Z`: same shape as [MobileConfigGetBoolWithContextualIdFingerprint]. */
internal object MobileConfigGetBoolWithoutLoggingWithContextualIdFingerprint : Fingerprint(
    definingClass = MOBILE_CONFIG_MODULE,
    name = "getBoolWithoutLoggingWithContextualId",
    returnType = "Z",
    parameters = listOf("D", "D", "Ljava/lang/String;")
)
