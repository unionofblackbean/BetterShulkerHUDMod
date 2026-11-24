package bettershulkerhud.core;

/** Screen-space policy shared by the HUD and optional ingredient overlays. */
public final class HudOverlayLayout {
    public enum Side { LEFT, RIGHT, HIDDEN }

    public record Placement(Side side, int availableWidth) {}

    private static final int SCREEN_MARGIN = 4;
    private static final int PANEL_GAP = 6;
    private static final int INGREDIENT_MIN_WIDTH = 64;

    private HudOverlayLayout() {}

    public static Placement choose(int screenWidth, int guiLeft, int guiWidth,
            int minimumPanelWidth, boolean recipeBookOpen,
            boolean jeiInstalled, boolean reiInstalled) {
        int left = Math.max(0, guiLeft - SCREEN_MARGIN - PANEL_GAP);
        int right = Math.max(0, screenWidth - guiLeft - guiWidth - SCREEN_MARGIN - PANEL_GAP);
        boolean ingredientOverlay = jeiInstalled || reiInstalled;
        if (recipeBookOpen) {
            int available = right - (ingredientOverlay ? INGREDIENT_MIN_WIDTH : 0);
            return available >= minimumPanelWidth
                    ? new Placement(Side.RIGHT, available)
                    : new Placement(Side.HIDDEN, 0);
        }
        if (!ingredientOverlay) {
            if (left >= right && left >= minimumPanelWidth) return new Placement(Side.LEFT, left);
            if (right >= minimumPanelWidth) return new Placement(Side.RIGHT, right);
            if (left >= minimumPanelWidth) return new Placement(Side.LEFT, left);
            return new Placement(Side.HIDDEN, 0);
        }
        if (left >= minimumPanelWidth && right >= minimumPanelWidth) {
            return left <= right
                    ? new Placement(Side.LEFT, left)
                    : new Placement(Side.RIGHT, right);
        }
        if (left >= minimumPanelWidth && right >= INGREDIENT_MIN_WIDTH) {
            return new Placement(Side.LEFT, left);
        }
        if (right >= minimumPanelWidth && left >= INGREDIENT_MIN_WIDTH) {
            return new Placement(Side.RIGHT, right);
        }
        return new Placement(Side.HIDDEN, 0);
    }
}
