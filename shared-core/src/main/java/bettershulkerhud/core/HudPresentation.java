package bettershulkerhud.core;

/** Display policy; stored item quantities remain exact. */
public final class HudPresentation {
    private static final String[] UNITS = {"", "K", "M", "B", "T", "P", "E"};
    private HudPresentation() {}

    /** Truncate so displayed stock never exceeds actual stock. */
    public static String formatCount(long count, boolean compact) {
        if (!compact || count < 1000) return Long.toString(count);
        long divisor = 1;
        int unit = 0;
        while (unit + 1 < UNITS.length && divisor <= count / 1000) {
            divisor *= 1000;
            unit++;
        }
        long whole = count / divisor;
        long fraction = (count % divisor) / (divisor / 10);
        return whole + (fraction == 0 ? "" : "." + fraction) + UNITS[unit];
    }
}
