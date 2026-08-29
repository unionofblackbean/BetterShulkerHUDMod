package bettershulkerhud.config;

import bettershulkerhud.Reference;
import fi.dy.masa.malilib.config.options.ConfigHotkey;

import java.util.List;

public final class Hotkeys {
    private static final String PREFIX = Reference.MOD_ID + ".config.hotkey";

    public static final ConfigHotkey OPEN_CONFIG_GUI =
            new ConfigHotkey("openConfigGui", "B,C").apply(PREFIX);

    public static final List<ConfigHotkey> HOTKEY_LIST = List.of(OPEN_CONFIG_GUI);

    private Hotkeys() {}
}
