package bettershulkerhud;

import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.fabricmc.api.ClientModInitializer;

public class BetterBundleMod implements ClientModInitializer {
    public static final String MOD_ID = "better-shulker-hud";

    @Override
    public void onInitializeClient() {
        BundleCategory.registerCategoryItems();
        QuickShulkerExtractionController.register();
    }
}
