package app.morphe.patches.facebook.marketplace

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/facebook/marketplace/MarketplaceOnly;"

internal val hideTabBarOption = booleanOption(
    key = "hideTabBar",
    default = true,
    title = "Hide bottom tab bar",
    description = "Hides the Facebook tab bar (Feed / Reels / Watch / Notifications / Menu) so " +
        "they aren't one tap away. Turn off to only change which tab opens at launch."
)

internal val lockNavigationOption = booleanOption(
    key = "lockNavigation",
    default = true,
    title = "Lock navigation to Marketplace",
    description = "Disables swiping between tabs and the left-hand Facebook menu (hamburger button " +
        "and edge swipe), so Reels, Groups, Dating etc. can't be reached from the main screen."
)

/**
 * Universal-style declaration (no compatibleWith): the compatibility object needs the app's
 * signing-certificate SHA-256, which isn't available from the dex files. The fingerprint
 * itself is what protects against the wrong app/version: it fails to match instead of
 * patching something unintended. Researched and checked against Facebook 582.0.0.50.54.
 */
@Suppress("unused")
val marketplaceOnlyPatch = bytecodePatch(
    name = "Marketplace only",
    description = "Opens Facebook directly on Marketplace and (optionally) hides the bottom tab bar. " +
        "Built for Facebook 582.0.0.50.54 (com.facebook.katana).",
    // Morphe requires universal patches (no compatibleWith) to be default = false; enable by name.
    default = false
) {
    extendWith("extensions/facebook.mpe")

    hideTabBarOption()
    lockNavigationOption()

    execute {
        val hideTabBar by hideTabBarOption
        val lockNavigation by lockNavigationOption

        val entryPoint =
            if (hideTabBar == true) "onStartingTabDeterminedHideBar" else "onStartingTabDetermined"

        DetermineStartingTabFingerprint.method.apply {
            // Do NOT read p0 at the return-void: Facebook reuses v27 (p0) as scratch, so there it
            // has verifier type "Conflict" and the class fails to load (VerifyError, app crashes
            // on launch). The return hooks therefore take no registers at all.
            val returnIndices = implementation!!.instructions
                .withIndex()
                .filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }
                .reversed()

            check(returnIndices.isNotEmpty()) { "No return-void found in the tab controller method" }

            // Reverse order keeps earlier indices valid.
            returnIndices.forEach { index ->
                addInstructions(index, "invoke-static {}, $EXTENSION_CLASS->$entryPoint()V")
            }

            // At method entry p0 ("this") is guaranteed to hold the controller. /range because
            // p0 is v27 here, which doesn't fit the 4-bit register field of plain invoke-static.
            // Added last so it doesn't shift the indices above.
            addInstructions(
                0,
                "invoke-static/range { p0 .. p0 }, $EXTENSION_CLASS->capture(Ljava/lang/Object;)V"
            )
        }

        if (lockNavigation == true) {
            // Boolean touch handlers: never intercept / never handle. v0 is a free local in every
            // one of these methods (checked in the dex), and no branches are needed.
            val returnFalse = "const/4 v0, 0x0\nreturn v0"

            // 1. Swiping between tabs: the main tab ViewPager (X.25n) must neither intercept
            //    horizontal drags, nor take them as a touch target, nor accept nested-scroll
            //    hand-offs from child lists.
            TabPagerInterceptTouchFingerprint.method.addInstructions(0, returnFalse)
            TabPagerStartNestedScrollFingerprint.method.addInstructions(0, returnFalse)
            // X.25n has no onTouchEvent of its own (it inherits ViewPager's), so add an override.
            TabPagerInterceptTouchFingerprint.classDef.methods.add(
                ImmutableMethod(
                    TabPagerInterceptTouchFingerprint.classDef.type,
                    "onTouchEvent",
                    listOf(ImmutableMethodParameter("Landroid/view/MotionEvent;", null, null)),
                    "Z",
                    AccessFlags.PUBLIC.value,
                    null,
                    null,
                    MutableMethodImplementation(3) // v0 + p0 (this) + p1 (event)
                ).toMutable().apply {
                    addInstructions(0, returnFalse)
                }
            )

            // 2. The left-hand Facebook menu (SlidingPanelScrollView, X.24g): no edge swipe...
            SlidingPanelInterceptTouchFingerprint.method.addInstructions(0, returnFalse)
            SlidingPanelTouchFingerprint.method.addInstructions(0, returnFalse)
            // ...and no programmatic "open panel" (hamburger tap, RN module, etc.).
            SlidingPanelOpenFingerprint.method.addInstructions(0, "return-void")
        }
    }
}
