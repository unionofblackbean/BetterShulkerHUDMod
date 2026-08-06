package bettershulkerhud.config;

import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.GuiConfigs;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class Callbacks {
    private Callbacks() {}

    public static void init() {
        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback((action, keybind) -> {
            GuiBase.openGui(new GuiConfigs());
            return true;
        });

        Hotkeys.TAKE_TO_OFFHAND.getKeybind().setCallback((action, keybind) -> {
            Minecraft client = Minecraft.getInstance();
            if (!(client.screen instanceof AbstractContainerScreen<?> screen)) return false;
            double mouseX = client.mouseHandler.xpos()
                    * client.getWindow().getGuiScaledWidth()
                    / client.getWindow().getScreenWidth();
            double mouseY = client.mouseHandler.ypos()
                    * client.getWindow().getGuiScaledHeight()
                    / client.getWindow().getScreenHeight();
            return BundlePanelInteraction.handleTakeToOffhand(
                    mouseX, mouseY, screen.leftPos, screen.topPos, screen.imageHeight);
        });

        for (var feature : Configs.Features.OPTIONS) {
            feature.getKeybind().setCallback(new KeyCallbackToggleBoolean(feature));
        }
    }
}
