package bettershulkerhud.event;

import bettershulkerhud.Reference;
import bettershulkerhud.config.Configs;
import bettershulkerhud.config.Hotkeys;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

public final class InputHandler implements IKeybindProvider {
    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler() {}

    public static InputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        Configs.Features.OPTIONS.forEach(option ->
                manager.addKeybindToMap(option.getKeybind()));
        Hotkeys.HOTKEY_LIST.forEach(hotkey ->
                manager.addKeybindToMap(hotkey.getKeybind()));
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(
                Reference.MOD_NAME,
                Reference.MOD_ID + ".hotkeys.category.features",
                Configs.Features.OPTIONS);
        manager.addHotkeysForCategory(
                Reference.MOD_NAME,
                Reference.MOD_ID + ".hotkeys.category.general",
                Hotkeys.HOTKEY_LIST);
    }
}
