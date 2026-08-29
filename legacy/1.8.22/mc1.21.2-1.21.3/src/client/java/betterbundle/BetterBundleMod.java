package bettershulkerhud;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BetterBundleMod implements ModInitializer {
    public static final String MOD_ID = Reference.MOD_ID;
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        validateQuickShulkerMinecraftVersion();
        InitializationHandler.getInstance().registerInitializationHandler(new BetterShulkerInitHandler());
    }

    private static void validateQuickShulkerMinecraftVersion() {
        FabricLoader loader = FabricLoader.getInstance();
        String minecraft = loader.getModContainer("minecraft").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        String quickShulker = loader.getModContainer("quickshulker").orElseThrow()
                .getMetadata().getVersion().getFriendlyString();
        if (!quickShulker.endsWith("-" + minecraft)) {
            throw new IllegalStateException("Quick Shulker " + quickShulker
                    + " does not match Minecraft " + minecraft);
        }
    }
}
