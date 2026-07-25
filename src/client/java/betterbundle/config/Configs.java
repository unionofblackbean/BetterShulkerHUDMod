package bettershulkerhud.config;

import bettershulkerhud.Reference;
import bettershulkerhud.gui.BundlePanelRenderer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Configs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    public Configs() {}

    public static final class General {
        private static final String PREFIX = Reference.MOD_ID + ".config.general";

        public static final ConfigInteger HUD_MAX_COLUMNS =
                new ConfigInteger("hudMaxColumns", 6, 2, 12).apply(PREFIX);
        public static final ConfigInteger HUD_MAX_ROWS =
                new ConfigInteger("hudMaxRows", 8, 3, 16).apply(PREFIX);
        public static final ConfigInteger HUD_CORNER_RADIUS =
                new ConfigInteger("hudCornerRadius", 3, 0, 6).apply(PREFIX);
        public static final ConfigDouble AUTO_PICKUP_RANGE =
                new ConfigDouble("autoPickupRange", 1.25D, 0.5D, 6.0D).apply(PREFIX);
        public static final ConfigInteger AUTO_PICKUP_SCAN_INTERVAL =
                new ConfigInteger("autoPickupScanInterval", 2, 1, 20).apply(PREFIX);
        public static final ConfigInteger PICKUP_TRACKING_TIMEOUT =
                new ConfigInteger("pickupTrackingTimeout", 40, 10, 200).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_THRESHOLD =
                new ConfigInteger("autoRestockThreshold", 6, 1, 64).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_AMOUNT =
                new ConfigInteger("autoRestockAmount", 64, 1, 64).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_SCAN_INTERVAL =
                new ConfigInteger("autoRestockScanInterval", 4, 1, 40).apply(PREFIX);

        public static final List<IConfigBase> OPTIONS = List.of(
                HUD_MAX_COLUMNS,
                HUD_MAX_ROWS,
                HUD_CORNER_RADIUS,
                AUTO_PICKUP_RANGE,
                AUTO_PICKUP_SCAN_INTERVAL,
                PICKUP_TRACKING_TIMEOUT,
                AUTO_RESTOCK_THRESHOLD,
                AUTO_RESTOCK_AMOUNT,
                AUTO_RESTOCK_SCAN_INTERVAL
        );

        private General() {}
    }

    public static final class Features {
        private static final String PREFIX = Reference.MOD_ID + ".config.feature";

        public static final ConfigBooleanHotkeyed HUD_ENABLED =
                new ConfigBooleanHotkeyed("hudEnabled", false, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed AUTO_SHULKER_PICKUP =
                new ConfigBooleanHotkeyed("autoShulkerPickup", true, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed LITEMATICA_RESTOCK =
                new ConfigBooleanHotkeyed("litematicaRestock", true, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed HIDE_QUICK_SHULKER_SCREEN =
                new ConfigBooleanHotkeyed("hideQuickShulkerScreen", true, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed PINYIN_SEARCH =
                new ConfigBooleanHotkeyed("pinyinSearch", true, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed RETURN_HISTORY =
                new ConfigBooleanHotkeyed("returnHistory", true, "").apply(PREFIX);
        public static final ConfigBooleanHotkeyed AUTO_RESTOCK =
                new ConfigBooleanHotkeyed("autoRestock", true, "").apply(PREFIX);

        public static final List<ConfigBooleanHotkeyed> OPTIONS = List.of(
                HUD_ENABLED,
                AUTO_SHULKER_PICKUP,
                LITEMATICA_RESTOCK,
                HIDE_QUICK_SHULKER_SCREEN,
                PINYIN_SEARCH,
                RETURN_HISTORY,
                AUTO_RESTOCK
        );

        private Features() {}
    }

    public static void loadFromFile() {
        Path configFile = FileUtils.getConfigDirectory().resolve(CONFIG_FILE_NAME);
        if (!Files.isRegularFile(configFile) || !Files.isReadable(configFile)) return;

        JsonElement element = JsonUtils.parseJsonFile(configFile);
        if (element == null || !element.isJsonObject()) return;

        JsonObject root = element.getAsJsonObject();
        ConfigUtils.readConfigBase(root, "General", General.OPTIONS);
        ConfigUtils.readHotkeyToggleOptions(
                root, "FeatureHotkeys", "FeatureToggles", Features.OPTIONS);
        ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
    }

    public static void saveToFile() {
        Path directory = FileUtils.getConfigDirectory();
        FileUtils.createDirectoriesIfMissing(directory);
        if (!Files.isDirectory(directory)) return;

        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "General", General.OPTIONS);
        ConfigUtils.writeHotkeyToggleOptions(
                root, "FeatureHotkeys", "FeatureToggles", Features.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
        JsonUtils.writeJsonToFile(root, directory.resolve(CONFIG_FILE_NAME));
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }

    @Override
    public void onConfigsChanged() {
        BundlePanelRenderer.invalidateCache();
    }
}
