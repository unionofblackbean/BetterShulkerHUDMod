package bettershulkerhud.compat;

import bettershulkerhud.BetterBundleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class LitematicaEasyPlaceCompat {
    private static final String LITEMATICA_MOD_ID = "litematica";

    private static boolean initialized;
    private static boolean available;
    private static Object easyPlaceMode;
    private static Method getBooleanValue;
    private static Method isHandling;

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
