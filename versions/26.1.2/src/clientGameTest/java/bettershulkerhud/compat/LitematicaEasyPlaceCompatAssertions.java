package bettershulkerhud.compat;

public final class LitematicaEasyPlaceCompatAssertions {
    private LitematicaEasyPlaceCompatAssertions() {}

    public static void verifyPolicy() {
        assertFalse(LitematicaEasyPlaceCompat.shouldBlockFallback(false, false, true),
                "disabled easy place must allow normal block placement");
        assertFalse(LitematicaEasyPlaceCompat.shouldBlockFallback(true, true, true),
                "Litematica's internal placement must be allowed");
        assertFalse(LitematicaEasyPlaceCompat.shouldBlockFallback(true, false, false),
                "non-block interactions must not be blocked");
        assertTrue(LitematicaEasyPlaceCompat.shouldBlockFallback(true, false, true),
                "unhandled block placement must be blocked while easy place is enabled");
        assertFalse(LitematicaEasyPlaceCompat.shouldPreserveEmptyBucketSelection(
                        false, true, true),
                "inactive easy place must not affect bucket selection");
        assertFalse(LitematicaEasyPlaceCompat.shouldPreserveEmptyBucketSelection(
                        true, false, true),
                "non-water projection items must retain normal selection");
        assertFalse(LitematicaEasyPlaceCompat.shouldPreserveEmptyBucketSelection(
                        true, true, false),
                "non-bucket tools must retain normal selection");
        assertTrue(LitematicaEasyPlaceCompat.shouldPreserveEmptyBucketSelection(
                        true, true, true),
                "easy place must not replace a deliberately held empty bucket with water");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) throw new AssertionError(message);
    }
}
