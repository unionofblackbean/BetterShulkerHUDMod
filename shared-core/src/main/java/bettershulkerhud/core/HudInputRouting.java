package bettershulkerhud.core;

/** Arbitration for external inventory handlers that run before the owning screen. */
public final class HudInputRouting {
    private HudInputRouting() {}

    /** null: run the external handler; false: skip it but deliver input to the screen. */
    public static Boolean externalHandlerResult(boolean layoutEditing, boolean cursorTransfer) {
        if (layoutEditing) return Boolean.FALSE;
        if (cursorTransfer) return Boolean.TRUE;
        return null;
    }
}
