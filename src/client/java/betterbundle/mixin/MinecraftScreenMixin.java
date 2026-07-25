package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
        if (screen instanceof ShulkerBoxScreen
                && QuickShulkerExtractionController.shouldHideQuickShulkerScreen()) {
            ci.cancel();
        }
    }
}
