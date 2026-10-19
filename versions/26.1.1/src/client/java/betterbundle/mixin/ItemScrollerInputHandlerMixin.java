package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundlePanelInteraction;
import bettershulkerhud.gui.BundlePanelRenderer;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.util.InputUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.itemscroller.event.InputHandler", remap = false)
public abstract class ItemScrollerInputHandlerMixin {
    @Inject(
            method = "handleInputImpl",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void betterShulkerHud$pauseDuringCursorTransfer(
            int keyCode, boolean keyState, double wheelDelta, Minecraft client,
            CallbackInfoReturnable<Boolean> cir) {
        if (QuickShulkerExtractionController.isCursorTransferInProgress()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "handleInputImpl",
            at = @At(
                    value = "INVOKE",
                    target = "Lfi/dy/masa/itemscroller/util/InventoryUtils;tryMoveItems(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lfi/dy/masa/itemscroller/recipes/RecipeStorage;Z)Z"),
            cancellable = true,
            require = 0,
            remap = false)
    private void betterShulkerHud$handleHudWheel(
            int keyCode, boolean keyState, double wheelDelta, Minecraft client,
            CallbackInfoReturnable<Boolean> cir) {
        if (wheelDelta == 0 || client.player == null
                || !(client.screen instanceof AbstractContainerScreen<?> screen)
                || screen instanceof CreativeModeInventoryScreen
                || !BundlePanelRenderer.isEffectivelyVisible()) return;

        boolean moveStack = betterShulkerHud$isHotkeyHeld("MODIFIER_MOVE_STACK");
        if (!moveStack && (betterShulkerHud$isHotkeyHeld("MODIFIER_MOVE_MATCHING")
                || betterShulkerHud$isHotkeyHeld("MODIFIER_MOVE_EVERYTHING"))) return;
        if (!betterShulkerHud$getToggle(
                moveStack ? "SCROLL_STACKS" : "SCROLL_SINGLE", true)) return;

        boolean scrollingUp = wheelDelta > 0;
        boolean moveToOtherInventory = scrollingUp;
        if (betterShulkerHud$getGeneric(
                "SLOT_POSITION_AWARE_SCROLL_DIRECTION", false)) {
            moveToOtherInventory = !scrollingUp;
        }
        if (betterShulkerHud$getGeneric(
                moveStack ? "REVERSE_SCROLL_DIRECTION_STACKS"
                        : "REVERSE_SCROLL_DIRECTION_SINGLE", false)) {
            moveToOtherInventory = !moveToOtherInventory;
        }
        if (!moveToOtherInventory) return;

        if (BundlePanelInteraction.handleItemScrollerWheel(
                InputUtils.getMouseX(), InputUtils.getMouseY(), moveStack,
                screen.leftPos, screen.topPos, screen.imageHeight)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private static boolean betterShulkerHud$isHotkeyHeld(String fieldName) {
        try {
            Class<?> hotkeys = Class.forName("fi.dy.masa.itemscroller.config.Hotkeys");
            Object value = hotkeys.getField(fieldName).get(null);
            return value instanceof ConfigHotkey hotkey
                    && hotkey.getKeybind().isKeybindHeld();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    @Unique
    private static boolean betterShulkerHud$getToggle(
            String fieldName, boolean fallback) {
        return betterShulkerHud$getConfigBoolean(
                "fi.dy.masa.itemscroller.config.Configs$Toggles", fieldName, fallback);
    }

    @Unique
    private static boolean betterShulkerHud$getGeneric(
            String fieldName, boolean fallback) {
        return betterShulkerHud$getConfigBoolean(
                "fi.dy.masa.itemscroller.config.Configs$Generic", fieldName, fallback);
    }

    @Unique
    private static boolean betterShulkerHud$getConfigBoolean(
            String className, String fieldName, boolean fallback) {
        try {
            Object value = Class.forName(className).getField(fieldName).get(null);
            return value instanceof ConfigBoolean config
                    ? config.getBooleanValue() : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }
}
