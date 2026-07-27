package bettershulkerhud.config;

import bettershulkerhud.Reference;
import bettershulkerhud.gui.BundlePanelRenderer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.json.JsonUtils;
import com.google.common.collect.ImmutableList;

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
        public static final ConfigOptionList CLEAR_SLOT_LIST_MODE =
                new ConfigOptionList("clearSlotListMode", ClearanceListMode.BLACKLIST).apply(PREFIX);
        public static final ConfigStringList CLEAR_SLOT_WHITELIST =
                new ConfigStringList("clearSlotWhitelist", ImmutableList.of()).apply(PREFIX);
        public static final ConfigStringList CLEAR_SLOT_BLACKLIST =
                new ConfigStringList("clearSlotBlacklist", ImmutableList.of()).apply(PREFIX);
        public static final ConfigStringList ORGANIZE_BLACKLIST =
                new ConfigStringList("organizeBlacklist",
                        ImmutableList.of("minecraft:firework_rocket")).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_THRESHOLD =
                new ConfigInteger("autoRestockThreshold", 6, 1, 64).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_AMOUNT =
                new ConfigInteger("autoRestockAmount", 64, 1, 64).apply(PREFIX);
        public static final ConfigInteger AUTO_RESTOCK_SCAN_INTERVAL =
                new ConfigInteger("autoRestockScanInterval", 4, 1, 40).apply(PREFIX);
        public static final ConfigBoolean DIAGNOSTIC_LOGGING =
                new ConfigBoolean("diagnosticLogging", false).apply(PREFIX);

        public static final List<IConfigBase> OPTIONS = List.of(
                HUD_MAX_COLUMNS,
                HUD_MAX_ROWS,
                CLEAR_SLOT_LIST_MODE,
                CLEAR_SLOT_WHITELIST,
                CLEAR_SLOT_BLACKLIST,
                ORGANIZE_BLACKLIST,
                AUTO_RESTOCK_THRESHOLD,
                AUTO_RESTOCK_AMOUNT,
                AUTO_RESTOCK_SCAN_INTERVAL,
                DIAGNOSTIC_LOGGING
        );

        private General() {}
    }

    public static final class Features {
        private static final String PREFIX = Reference.MOD_ID + ".config.feature";

        public static final ConfigBooleanHotkeyed HUD_ENABLED =
                new ConfigBooleanHotkeyed("hudEnabled", false, "").apply(PREFIX);
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
                LITEMATICA_RESTOCK,
                HIDE_QUICK_SHULKER_SCREEN,
                PINYIN_SEARCH,
                RETURN_HISTORY,
                AUTO_RESTOCK
        );

        private Features() {}
    }

    public enum ClearanceListMode implements IConfigOptionListEntry {
        BLACKLIST("blacklist"),
        WHITELIST("whitelist");

        private final String configValue;

        ClearanceListMode(String configValue) {
            this.configValue = configValue;
        }

        @Override
        public String getStringValue() {
            return configValue;
        }

        @Override
        public String getDisplayName() {
            return StringUtils.translate(
                    Reference.MOD_ID + ".config.clear_slot_list_mode." + configValue);
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward) {
            int offset = forward ? 1 : values().length - 1;
            return values()[(ordinal() + offset) % values().length];
        }

        @Override
        public ClearanceListMode fromString(String value) {
            for (ClearanceListMode mode : values()) {
                if (mode.configValue.equalsIgnoreCase(value)) return mode;
            }
            return BLACKLIST;
        }
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
