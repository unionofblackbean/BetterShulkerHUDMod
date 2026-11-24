package bettershulkerhud.core;

/** Distinguishes a consumed potion from an inventory move before replacing its container. */
public final class PotionRestockPolicy {
    private PotionRestockPolicy() {}

    public static boolean wasConsumed(int previousLooseCount, int currentLooseCount,
            boolean sameSelectedSlot, boolean inventoryScreenOpen,
            boolean handEmptyOrReturnedBottle) {
        return previousLooseCount > 0 && currentLooseCount < previousLooseCount
                && sameSelectedSlot && !inventoryScreenOpen && handEmptyOrReturnedBottle;
    }
}
