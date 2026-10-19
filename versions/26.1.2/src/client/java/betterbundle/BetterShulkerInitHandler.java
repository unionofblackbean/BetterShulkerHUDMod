package bettershulkerhud;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.config.Callbacks;
import bettershulkerhud.config.Configs;
import bettershulkerhud.event.InputHandler;
import bettershulkerhud.event.InventoryDragStoreController;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.gui.GuiConfigs;
import bettershulkerhud.gui.BundlePanelRenderer;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class BetterShulkerInitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new));
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        Callbacks.init();

        BundleCategory.registerCategoryItems();
        TickHandler.getInstance().registerClientTickHandler(
                QuickShulkerExtractionController::onClientTick);
        TickHandler.getInstance().registerClientTickHandler(
                InventoryDragStoreController::onClientTick);
        WorldLoadHandler.getInstance().registerWorldLoadPreHandler(new WorldListener());
    }

    private static final class WorldListener implements IWorldLoadListener {
        @Override
        public void onWorldLoadPre(
                ClientLevel worldBefore, ClientLevel worldAfter, Minecraft client) {
            QuickShulkerExtractionController.clearWorldState();
            StorageClientNetwork.clearWorldState();
            BundleCategory.resetCategoryItems();
            InventoryDragStoreController.clear();
            BundlePanelRenderer.invalidateCache();
        }
    }
}
