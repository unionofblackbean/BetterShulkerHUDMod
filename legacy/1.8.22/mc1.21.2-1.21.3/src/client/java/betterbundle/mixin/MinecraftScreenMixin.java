package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftScreenMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void betterShulkerHud$hideTransientShulkerScreen(
            Screen screen, CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        boolean transientShulker = screen instanceof ShulkerBoxScreen
                && QuickShulkerExtractionController.shouldHideQuickShulkerScreen();
        if (transientShulker) {
            ci.cancel();
            return;
        }
        if (screen == null
                && client.screen instanceof InventoryScreen
                && QuickShulkerExtractionController
                .shouldPreserveInventoryScreenDuringContainerClose()) {
            ci.cancel();
            return;
        }
        if (client.screen != screen) {
            BundlePanelRenderer.resetToggleButtonInteraction();
        }
        if (client.screen instanceof AbstractContainerScreen<?>
                && client.screen != screen) {
            BundlePanelRenderer.prepareSortAfterContainerClose();
        }
    }
}
