package bettershulkerhud.core;

public final class PotionRestockPolicyTest {
    public static void main(String[] args) {
        require(PotionRestockPolicy.wasConsumed(1, 0, true, false, true),
                "drinking should retain the pre-consumption potion identity");
        require(PotionRestockPolicy.wasConsumed(2, 1, true, false, true),
                "throwing should retain the pre-consumption potion identity");
        require(!PotionRestockPolicy.wasConsumed(1, 1, true, false, true),
                "moving the potion to another slot is not consumption");
        require(!PotionRestockPolicy.wasConsumed(1, 0, true, true, true),
                "manual inventory movement is not consumption");
        require(!PotionRestockPolicy.wasConsumed(1, 0, false, false, true),
                "switching the selected slot is not consumption");
        require(!PotionRestockPolicy.wasConsumed(1, 0, true, false, false),
                "an unrelated replacement item is not a returned container");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
