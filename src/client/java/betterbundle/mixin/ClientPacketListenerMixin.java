package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void betterShulkerHud$beforeTakeItem(
            ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        QuickShulkerExtractionController.onItemPickup(
                packet.getItemId(), packet.getPlayerId(), packet.getAmount());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void betterShulkerHud$afterContainerContent(
            ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        QuickShulkerExtractionController.onContainerSync(packet.containerId());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void betterShulkerHud$afterContainerSlot(
            ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        QuickShulkerExtractionController.onContainerSync(packet.getContainerId());
    }
}
