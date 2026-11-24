package bettershulkerhud.core;

/** Pure screen-space geometry for the entire HUD, including protruding tabs. */
public final class HudPanelLayout {
    public static final int AUTO = -1;
    public static final int SCALE = 10000;
    public static final int MARGIN = 4;
    public static final int SNAP_DISTANCE = 8;
    private HudPanelLayout() {}

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public static boolean isManual(int x, int y) { return x >= 0 && y >= 0; }

    /** The panel anchor is inset inside its complete visual bounds. */
    public static Rect bounds(int x, int y, int panelWidth, int panelHeight, int tabsRight) {
        return new Rect(x - 4, y - 1, Math.max(panelWidth + 4, tabsRight) + 4,
                panelHeight + 5);
    }

    public static int resolve(int normalized, int screenSize, int extent, int autoPosition) {
        if (normalized < 0) return autoPosition;
        int travel = Math.max(0, screenSize - extent - MARGIN * 2);
        return MARGIN + (int) Math.round(travel * Math.clamp(normalized, 0, SCALE) / (double) SCALE);
    }

    public static int normalize(double position, int screenSize, int extent) {
        int travel = Math.max(0, screenSize - extent - MARGIN * 2);
        return travel == 0 ? 0 : (int) Math.round(Math.clamp(position - MARGIN, 0.0, travel)
                * SCALE / travel);
    }

    public static int snap(double position, int screenSize, int extent) {
        int min = MARGIN;
        int max = Math.max(min, screenSize - extent - MARGIN);
        int value = (int) Math.round(Math.clamp(position, (double) min, (double) max));
        if (value - min <= SNAP_DISTANCE) return min;
        if (max - value <= SNAP_DISTANCE) return max;
        return value;
    }
}
