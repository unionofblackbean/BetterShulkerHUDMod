package bettershulkerhud.core;

public final class HudOverlayLayoutTest {
    private HudOverlayLayoutTest() {}

    public static void main(String[] args) {
        expect(HudOverlayLayout.Side.LEFT, 420, 150, 176, 110, false, true, false);
        expect(HudOverlayLayout.Side.HIDDEN, 320, 90, 176, 110, false, true, false);
        expect(HudOverlayLayout.Side.RIGHT, 560, 200, 176, 110, true, true, false);
        expect(HudOverlayLayout.Side.RIGHT, 520, 200, 176, 110, false, false, true);
        expect(HudOverlayLayout.Side.LEFT, 420, 150, 176, 110, false, false, false);
        // The full 84-pixel panel plus its eight-pixel exclusion margin must fit on the right.
        expect(HudOverlayLayout.Side.HIDDEN, 409, 132, 176, 92, true, false, false);
        expect(HudOverlayLayout.Side.RIGHT, 410, 132, 176, 92, true, false, false);
        // An ingredient overlay reserves 64 pixels before deciding whether the HUD fits.
        expect(HudOverlayLayout.Side.HIDDEN, 473, 132, 176, 92, true, true, false);
        expect(HudOverlayLayout.Side.RIGHT, 474, 132, 176, 92, true, true, false);
    }

    private static void expect(HudOverlayLayout.Side expected, int screenWidth,
            int guiLeft, int guiWidth, int minimumPanelWidth,
            boolean recipeBookOpen, boolean jeiInstalled, boolean reiInstalled) {
        HudOverlayLayout.Side actual = HudOverlayLayout.choose(screenWidth,
                guiLeft, guiWidth, minimumPanelWidth,
                recipeBookOpen, jeiInstalled, reiInstalled).side();
        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }
}
