package bettershulkerhud.core;

public final class HudPanelLayoutTest {
    private HudPanelLayoutTest() {}
    public static void main(String[] args) {
        check(!HudPanelLayout.isManual(-1, 5000), "partial AUTO");
        check(HudPanelLayout.resolve(-1, 320, 90, 173) == 173, "AUTO delegates");
        check(HudPanelLayout.resolve(0, 320, 90, 0) == 4, "left edge");
        check(HudPanelLayout.resolve(10000, 320, 90, 0) == 226, "right edge");
        check(HudPanelLayout.resolve(5000, 80, 100, 0) == 4, "oversized safe");
        for (int n : new int[]{0, 1500, 5000, 10000}) {
            int p = HudPanelLayout.resolve(n, 640, 120, 0);
            int roundTrip = HudPanelLayout.normalize(p, 640, 120);
            check(Math.abs(n - roundTrip) <= 10, "normalized round trip");
        }
        check(HudPanelLayout.snap(12, 320, 90) == 4, "8px left snap");
        check(HudPanelLayout.snap(218, 320, 90) == 226, "8px right snap");
        check(HudPanelLayout.snap(17, 320, 90) == 17, "outside snap");
        var b = HudPanelLayout.bounds(20, 30, 67, 76, 75);
        check(b.x() == 16 && b.width() == 79, "protruding tabs included");
        check(b.contains(94, 30) && !b.contains(95, 30), "half-open hit bounds");
        check(b.contains(17, 31), "selected category included");
    }
    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
