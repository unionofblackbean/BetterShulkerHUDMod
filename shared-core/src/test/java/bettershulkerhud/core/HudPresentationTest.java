package bettershulkerhud.core;

public final class HudPresentationTest {
    private HudPresentationTest() {}
    public static void main(String[] args) {
        expect("0", 0, true);
        expect("1", 1, true);
        expect("999", 999, true);
        expect("1K", 1000, true);
        expect("1K", 1099, true);
        expect("1.1K", 1100, true);
        expect("1.2K", 1299, true);
        expect("999.9K", 999999, true);
        expect("1M", 1000000, true);
        expect("2.1B", Integer.MAX_VALUE, true);
        expect("9.2E", Long.MAX_VALUE, true);
        expect("-1", -1, true);
        expect(Long.toString(Long.MIN_VALUE), Long.MIN_VALUE, true);
        expect("1000000", 1000000, false);
        expect(Long.toString(Long.MAX_VALUE), Long.MAX_VALUE, false);
    }
    private static void expect(String expected, long count, boolean compact) {
        String actual = HudPresentation.formatCount(count, compact);
        if (!expected.equals(actual)) {
            throw new AssertionError(count + ": expected " + expected + " but got " + actual);
        }
    }
}
