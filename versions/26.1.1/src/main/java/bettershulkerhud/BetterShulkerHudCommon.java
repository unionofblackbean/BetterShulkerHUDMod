package bettershulkerhud;

import bettershulkerhud.network.EnderChestContentsPayload;
import bettershulkerhud.network.EnderChestRequestPayload;
import bettershulkerhud.network.StorageActionPayload;
import net.fabricmc.api.ModInitializer;

/**
 * Common networking entry point. This deliberately has no MaLiLib or other
 * client-only references, so the same JAR can be installed on a dedicated
 * server to enable ender-chest, bundle, and smooth store operations.
 */
public final class BetterShulkerHudCommon implements ModInitializer {
    public static final String MOD_ID = "better-shulker-hud";

    @Override
    public void onInitialize() {
        EnderChestContentsPayload.register();
        EnderChestRequestPayload.register();
        StorageActionPayload.register();
    }
}
