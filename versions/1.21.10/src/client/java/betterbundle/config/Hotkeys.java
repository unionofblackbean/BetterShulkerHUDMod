package bettershulkerhud.config;

import bettershulkerhud.Reference;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;

import java.util.List;

public final class Hotkeys {
    private static final String PREFIX = Reference.MOD_ID + ".config.hotkey";

    public static final ConfigHotkey OPEN_CONFIG_GUI =
            new ConfigHotkey("openConfigGui", "B,C").apply(PREFIX);

    public static final ConfigHotkey INVENTORY_DRAG_STORE_MODIFIER =
            new ConfigHotkey(
                    "inventoryDragStoreModifier", "SPACE", KeybindSettings.MODIFIER_GUI)
                    .apply(PREFIX);
    public static final ConfigHotkey TAKE_TO_OFFHAND =
            new ConfigHotkey("takeToOffhand", "F", KeybindSettings.GUI)
                    .apply(PREFIX);

    public static final List<ConfigHotkey> HOTKEY_LIST = List.of(
            OPEN_CONFIG_GUI,
            INVENTORY_DRAG_STORE_MODIFIER,
            TAKE_TO_OFFHAND);

    private Hotkeys() {}
}
