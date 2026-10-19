package bettershulkerhud.network;

import bettershulkerhud.BetterShulkerHudCommon;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Adapted from Sakurastreet/BetterShulkerHUDMod under the MIT license. */
public record EnderChestContentsPayload(List<ItemStack> contents)
        implements CustomPacketPayload {
    public static final Type<EnderChestContentsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    BetterShulkerHudCommon.MOD_ID, "ender_chest_contents"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnderChestContentsPayload> CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_LIST_STREAM_CODEC,
                    EnderChestContentsPayload::contents,
                    EnderChestContentsPayload::new);

    public EnderChestContentsPayload {
        contents = contents.stream().map(ItemStack::copy).toList();
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    public static void send(ServerPlayer player) {
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        List<ItemStack> contents = new ArrayList<>(container.getContainerSize());
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            contents.add(container.getItem(slot).copy());
        }
        ServerPlayNetworking.send(player, new EnderChestContentsPayload(contents));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
