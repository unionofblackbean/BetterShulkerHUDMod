package bettershulkerhud.config;

import bettershulkerhud.gui.GuiConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBoolean;

public final class Callbacks {
    private Callbacks() {}

    public static void init() {
        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback((action, keybind) -> {
            GuiBase.openGui(new GuiConfigs());
            return true;
        });

        for (var feature : Configs.Features.OPTIONS) {
            feature.getKeybind().setCallback(new KeyCallbackToggleBoolean(feature));
        }
    }
}
