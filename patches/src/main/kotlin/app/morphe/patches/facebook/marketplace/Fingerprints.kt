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
