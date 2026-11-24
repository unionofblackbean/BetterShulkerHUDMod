package bettershulkerhud.core;

public final class HudInputRoutingTest {
    private HudInputRoutingTest() {}

    public static void main(String[] args) {
        check(HudInputRouting.externalHandlerResult(false, false) == null,
                "ordinary input must keep the external handler unchanged");
        check(Boolean.TRUE.equals(HudInputRouting.externalHandlerResult(false, true)),
                "cursor transfer must still consume competing inventory input");
        check(Boolean.FALSE.equals(HudInputRouting.externalHandlerResult(true, false)),
                "layout editing must deliver mouse and keyboard input to the screen");
        check(Boolean.FALSE.equals(HudInputRouting.externalHandlerResult(true, true)),
                "the layout screen must retain ownership even if a transfer flag is present");
    }

    private static void check(boolean pass, String message) {
        if (!pass) throw new AssertionError(message);
    }
}
