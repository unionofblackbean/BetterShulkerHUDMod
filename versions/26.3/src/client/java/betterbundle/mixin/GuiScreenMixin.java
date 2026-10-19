package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void betterShulkerHud$guardScreenTransition(Screen screen, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        boolean transientShulker = screen instanceof ShulkerBoxScreen
                && QuickShulkerExtractionController.shouldHideQuickShulkerScreen();
        if (transientShulker) {
            ci.cancel();
            return;
        }
        if (screen == null
                && client.gui.screen() instanceof InventoryScreen
                && QuickShulkerExtractionController
                .shouldPreserveInventoryScreenDuringContainerClose()) {
            ci.cancel();
            return;
        }
        if (client.gui.screen() != screen) {
            BundlePanelRenderer.resetToggleButtonInteraction();
        }
        if (client.gui.screen() instanceof AbstractContainerScreen<?>
                && client.gui.screen() != screen) {
            if (QuickShulkerExtractionController
                    .shouldPreserveHudOrderOnScreenTransition()) {
                BundlePanelRenderer.prepareOrderAfterTransientContainerClose();
            } else {
                BundlePanelRenderer.prepareSortAfterContainerClose();
            }
        }
    }
}
