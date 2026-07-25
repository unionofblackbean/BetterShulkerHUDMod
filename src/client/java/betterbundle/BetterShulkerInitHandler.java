package bettershulkerhud;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.BundlePanelRenderer;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class BetterShulkerInitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        BundleCategory.registerCategoryItems();
        TickHandler.getInstance().registerClientTickHandler(
                QuickShulkerExtractionController::onClientTick);
        WorldLoadHandler.getInstance().registerWorldLoadPreHandler(new WorldListener());
    }

    private static final class WorldListener implements IWorldLoadListener {
        @Override
        public void onWorldLoadPre(
                ClientLevel worldBefore, ClientLevel worldAfter, Minecraft client) {
            QuickShulkerExtractionController.clearWorldState();
            BundlePanelRenderer.invalidateCache();
        }
    }
}
