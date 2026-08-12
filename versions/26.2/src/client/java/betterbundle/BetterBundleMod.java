package bettershulkerhud;

import bettershulkerhud.compat.StorageClientNetwork;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterBundleMod implements ClientModInitializer {
    public static final String MOD_ID = Reference.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        StorageClientNetwork.initialize();
        InitializationHandler.getInstance().registerInitializationHandler(new BetterShulkerInitHandler());
    }
}
