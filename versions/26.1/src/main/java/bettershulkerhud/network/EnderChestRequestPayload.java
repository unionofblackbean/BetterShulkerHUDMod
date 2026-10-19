package bettershulkerhud.network;

import bettershulkerhud.BetterShulkerHudCommon;
import bettershulkerhud.server.EnderChestStorageAccess;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Adapted from Sakurastreet/BetterShulkerHUDMod under the MIT license. */
public record EnderChestRequestPayload() implements CustomPacketPayload {
    public static final EnderChestRequestPayload INSTANCE = new EnderChestRequestPayload();
    public static final Type<EnderChestRequestPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(
                    BetterShulkerHudCommon.MOD_ID, "ender_chest_request"));
    public static final StreamCodec<FriendlyByteBuf, EnderChestRequestPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> {
                    if (EnderChestStorageAccess.hasPortableAccess(context.player())) {
                        EnderChestContentsPayload.send(context.player());
                    }
                }));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
