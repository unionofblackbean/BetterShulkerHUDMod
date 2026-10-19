package bettershulkerhud.compat;

import bettershulkerhud.BetterBundleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LitematicaEasyPlaceCompat {
    private static final String LITEMATICA_MOD_ID = "litematica";

    private static boolean initialized;
    private static boolean available;
    private static Object easyPlaceMode;
    private static Method getBooleanValue;
    private static Method isHandling;
    private static boolean replayInitialized;
    private static boolean replayAvailable;
    private static Method handleEasyPlaceWithMessage;
    private static Method getSchematicWorld;

    private LitematicaEasyPlaceCompat() {}

    public static boolean shouldBlockFallbackPlacement(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        initialize();
        if (!available) return false;

        try {
            boolean enabled = (boolean) getBooleanValue.invoke(easyPlaceMode);
            boolean handling = (boolean) isHandling.invoke(null);
            return shouldBlockFallback(enabled, handling, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            available = false;
            BetterBundleMod.LOGGER.warn(
                    "Disabling Litematica easy-place fallback guard after a compatibility error",
                    exception);
            return false;
        }
    }

    public static boolean shouldPreserveEmptyBucketSelection(
            ItemStack required, ItemStack held) {
        if (required == null || held == null
                || required.getItem() != Items.WATER_BUCKET
                || held.getItem() != Items.BUCKET) return false;
        initialize();
        if (!available) return false;

        try {
            return shouldPreserveEmptyBucketSelection(
                    (boolean) isHandling.invoke(null), true, true);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            available = false;
            BetterBundleMod.LOGGER.warn(
                    "Disabling Litematica empty-bucket selection guard after a compatibility error",
                    exception);
            return false;
        }
    }

    static boolean shouldBlockFallback(
            boolean easyPlaceEnabled, boolean easyPlaceHandling, boolean blockItem) {
        return easyPlaceEnabled && !easyPlaceHandling && blockItem;
    }

    static boolean shouldPreserveEmptyBucketSelection(
            boolean easyPlaceHandling, boolean waterBucketRequired, boolean emptyBucketHeld) {
        return easyPlaceHandling && waterBucketRequired && emptyBucketHeld;
    }

    public static boolean isHandlingEasyPlace() {
        initialize();
        if (!available) return false;
        try {
            return (boolean) getBooleanValue.invoke(easyPlaceMode)
                    && (boolean) isHandling.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableAfterError(exception);
            return false;
        }
    }

    public static BlockState schematicStateAt(BlockPos pos) {
        if (pos == null || !initializeReplay()) return null;
        try {
            Object world = getSchematicWorld.invoke(null);
            return world instanceof Level level ? level.getBlockState(pos) : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableReplayAfterError(exception);
            return null;
        }
    }

    public static void replayEasyPlace() {
        if (!initializeReplay()) return;
        try {
            if ((boolean) getBooleanValue.invoke(easyPlaceMode)
                    && !(boolean) isHandling.invoke(null)) {
                // Litematica performs its own ray trace, projection checks and placement.
                handleEasyPlaceWithMessage.invoke(null);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            disableReplayAfterError(exception);
        }
    }

    private static void disableAfterError(Exception exception) {
        available = false;
        BetterBundleMod.LOGGER.warn("Disabling Litematica easy-place guard after a compatibility error", exception);
    }

    private static void disableReplayAfterError(Exception exception) {
        replayAvailable = false;
        BetterBundleMod.LOGGER.warn("Disabling Litematica one-click replay after a compatibility error", exception);
    }

    private static synchronized boolean initializeReplay() {
        initialize();
        if (!available) return false;
        if (!replayInitialized) {
            replayInitialized = true;
            try {
                handleEasyPlaceWithMessage = Class.forName(
                        "fi.dy.masa.litematica.util.EasyPlaceUtils")
                        .getMethod("handleEasyPlaceWithMessage");
                getSchematicWorld = Class.forName(
                        "fi.dy.masa.litematica.world.SchematicWorldHandler")
                        .getMethod("getSchematicWorld");
                replayAvailable = true;
            } catch (ReflectiveOperationException | LinkageError exception) {
                BetterBundleMod.LOGGER.warn("Litematica one-click replay is unavailable", exception);
            }
        }
        return replayAvailable;
    }

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        if (!FabricLoader.getInstance().isModLoaded(LITEMATICA_MOD_ID)) return;

        try {
            Class<?> genericConfigs = Class.forName(
                    "fi.dy.masa.litematica.config.Configs$Generic");
            Field easyPlaceModeField = genericConfigs.getField("EASY_PLACE_MODE");
            easyPlaceMode = easyPlaceModeField.get(null);
            getBooleanValue = easyPlaceMode.getClass().getMethod("getBooleanValue");

            Class<?> easyPlaceUtils = Class.forName(
                    "fi.dy.masa.litematica.util.EasyPlaceUtils");
            isHandling = easyPlaceUtils.getMethod("isHandling");
            available = true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            BetterBundleMod.LOGGER.warn(
                    "Litematica easy-place fallback guard is unavailable", exception);
        }
    }
}
