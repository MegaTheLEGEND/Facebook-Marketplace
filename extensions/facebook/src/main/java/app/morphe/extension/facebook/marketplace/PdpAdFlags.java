package app.morphe.extension.facebook.marketplace;

import java.util.HashMap;
import java.util.Map;

/**
 * Forces OFF the MobileConfig flags that tell Marketplace's listing page (PDP) it can render end-to-end /
 * dynamic ad modules, so the ad blocks are not requested or drawn (no view scanning; Facebook 582.0.0.50.54).
 * Same hook points and flag format as {@link GroupSuggestions}: names/specifiers are from assets/rn_params.txt.
 */
@SuppressWarnings("unused")
public final class PdpAdFlags {
    private static final long DEFAULT_TRUE_MARKER = 0x2000000000000000L;
    private static final Map<String, Boolean> BY_NAME = new HashMap<>();
    private static final Map<Long, Boolean> BY_SPEC = new HashMap<>();

    private static void add(String name, long spec) {
        BY_NAME.put(name, Boolean.FALSE);
        BY_SPEC.put(spec & ~DEFAULT_TRUE_MARKER, Boolean.FALSE);
    }

    static {
        add("fb_marketplace_ads:enable_marketplace_pdp_e2e_ads_rendering_capability", 0x8100b0046908cfL);
        add("fb_marketplace_ads:enable_marketplace_pdp_e2e_carousel_ads", 0x8100b0039f0889L);
        add("fb_marketplace_ads:should_use_dynamic_ad_modules", 0x8100b001ab07ddL);
    }

    private PdpAdFlags() {
    }

    public static boolean filterBoolByName(String name, boolean original) {
        try {
            return name != null && BY_NAME.containsKey(name) ? false : original;
        } catch (Throwable t) {
            return original;
        }
    }

    public static boolean filterBoolBySpec(long spec, boolean original) {
        try {
            return BY_SPEC.containsKey(spec & ~DEFAULT_TRUE_MARKER) ? false : original;
        } catch (Throwable t) {
            return original;
        }
    }
}
