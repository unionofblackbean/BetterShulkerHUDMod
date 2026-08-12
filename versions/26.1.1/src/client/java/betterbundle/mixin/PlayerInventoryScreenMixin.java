package bettershulkerhud.mixin;

import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class PlayerInventoryScreenMixin {
    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(
            MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (BundlePanelRenderer.handleToggleButtonRelease(
                event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
