package app.morphe.extension.facebook.marketplace;

import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Runtime half of the "Marketplace only" patch for Facebook 582.0.0.50.54.
 *
 * The patch calls {@link #capture(Object)} at the start of {@code X.C2GO.A06(boolean)} and
 * {@link #onStartingTabDetermined()} (or the {@code ...HideBar} variant) at its end,
 * i.e. right after Facebook's own "TabBarController.determineStartingTabPosition"
 * logic has picked the tab to open. We then re-point the selection at the
 * Marketplace tab, looked up by its tab ID rather than by list position.
 *
 * Member names below (A00, A05, A01, A02, A03, A09) are the real, obfuscated
 * names in this exact build and are NOT stable across Facebook versions.
 *
 * Everything is wrapped so a mismatch can never crash the app: on any failure the
 * original tab selection is left untouched (Facebook opens normally).
 *
 * Deliberately has no Android compile-time dependency (View is touched via
 * reflection) so the lookup logic can be unit-tested on a plain JVM.
 */
@SuppressWarnings("unused")
public final class MarketplaceOnly {

    /** Marketplace tab ID observed in this build (TabTag.A03). */
    static final long MARKETPLACE_TAB_ID = 1606854132932955L;

    private static final int VIEW_GONE = 8; // android.view.View.GONE

    private MarketplaceOnly() {
    }

    /**
     * The tab controller captured at method entry. Facebook's A06 reuses its "this" register
     * (v27) as scratch space, so by the time it reaches return-void that register holds a
     * different type on different paths and the ART verifier rejects any read of it
     * ("register v27 has type Conflict"). So the patch grabs "this" on entry, where it is
     * guaranteed valid, and the return-void hook takes no arguments.
     */
    private static volatile WeakReference<Object> controller;

    /** Injected at the very start of X.C2GO.A06(boolean). */
    public static void capture(Object tabBarController) {
        controller = new WeakReference<>(tabBarController);
    }

    /** Injected before every return-void: select Marketplace; leave the tab bar alone. */
    public static void onStartingTabDetermined() {
        WeakReference<Object> ref = controller;
        apply(ref == null ? null : ref.get(), false);
    }

    /** Injected before every return-void: select Marketplace and hide the bottom tab bar. */
    public static void onStartingTabDeterminedHideBar() {
        WeakReference<Object> ref = controller;
        apply(ref == null ? null : ref.get(), true);
    }

    static void apply(Object c2go, boolean hideTabBar) {
        try {
            if (c2go == null) return;

            // static X.C2GO.A00(C2GO) -> C23681Hz (tab state holder)
            Method getState = findMethod(c2go.getClass(), "A00", c2go.getClass());
            Object state = getState.invoke(null, c2go);

            // C23681Hz.A05() -> ImmutableList<TabTag> (a java.util.List)
            Object tabsObj = findMethod(state.getClass(), "A05").invoke(state);
            if (!(tabsObj instanceof List)) return;
            List<?> tabs = (List<?>) tabsObj;

            int index = -1;
            for (int i = 0; i < tabs.size(); i++) {
                Object tag = tabs.get(i);
                if (tag != null && findField(tag.getClass(), "A03").getLong(tag) == MARKETPLACE_TAB_ID) {
                    index = i;
                    break;
                }
            }

            // Marketplace not in this account's tab list (feature-gated off, region, etc.):
            // do nothing at all rather than guess.
            if (index < 0) return;

            findField(c2go.getClass(), "A01").setInt(c2go, index);                  // selected position
            findField(c2go.getClass(), "A02").setLong(c2go, MARKETPLACE_TAB_ID);    // selected tab id

            if (hideTabBar) {
                Object bar = findField(c2go.getClass(), "A09").get(c2go);           // tab bar ViewGroup
                if (bar != null) {
                    findMethod(bar.getClass(), "setVisibility", int.class).invoke(bar, VIEW_GONE);
                }
            }
        } catch (Throwable ignored) {
            // Never break app startup because of a patch problem.
        }
    }

    private static Method findMethod(Class<?> start, String name, Class<?>... params) throws NoSuchMethodException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchMethodException(start.getName() + "." + name);
    }

    private static Field findField(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> c = start; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchFieldException(start.getName() + "." + name);
    }
}
