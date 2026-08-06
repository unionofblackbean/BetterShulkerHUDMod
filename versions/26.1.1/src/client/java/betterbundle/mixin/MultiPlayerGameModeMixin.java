package bettershulkerhud.mixin;

import bettershulkerhud.compat.LitematicaEasyPlaceCompat;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(
            method = "performUseItemOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"),
            cancellable = true,
            require = 2,
            allow = 2)
    private void betterShulkerHud$blockVanillaEasyPlaceFallback(
            LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<InteractionResult> cir) {
        if (LitematicaEasyPlaceCompat.shouldBlockFallbackPlacement(
                player.getItemInHand(hand))) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
