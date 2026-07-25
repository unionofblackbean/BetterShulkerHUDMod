package bettershulkerhud;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;

public class BetterBundleMod implements ModInitializer {
    public static final String MOD_ID = Reference.MOD_ID;

    @Override
    public void onInitialize() {
        InitializationHandler.getInstance().registerInitializationHandler(new BetterShulkerInitHandler());
    }
}
