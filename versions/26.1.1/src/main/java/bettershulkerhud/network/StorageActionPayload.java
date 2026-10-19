package bettershulkerhud.network;

import bettershulkerhud.BetterShulkerHudCommon;
import bettershulkerhud.server.BundleStorageAccess;
import bettershulkerhud.server.EnderChestStorageAccess;
import bettershulkerhud.server.ShulkerStorageAccess;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Small, server-validated action protocol for portable storage categories.
 * The shulker actions are used only as the optional smooth Space-drag path;
 * all existing QuickShulker/AxShulkers controller operations remain intact.
 *
 * <p>Interaction model adapted from Sakurastreet/BetterShulkerHUDMod under
 * the MIT license.</p>
 */
public record StorageActionPayload(
        int action, int inventorySlot, int contentSlot, ItemStack expectedItem)
        implements CustomPacketPayload {
    public static final int STORE_SHULKER_STACK = 0;
    public static final int STORE_SHULKER_ONE = 1;
    public static final int EXTRACT_ENDER_STACK = 2;
    public static final int EXTRACT_ENDER_ONE = 3;
    public static final int EXTRACT_ENDER_CURSOR = 4;
    public static final int INSERT_ENDER_CARRIED = 5;
    public static final int STORE_ENDER_STACK = 6;
    public static final int STORE_ENDER_ONE = 7;
    public static final int EXTRACT_BUNDLE_STACK = 8;
    public static final int EXTRACT_BUNDLE_ONE = 9;
    public static final int EXTRACT_BUNDLE_CURSOR = 10;
    public static final int INSERT_BUNDLE_CARRIED = 11;
    public static final int STORE_BUNDLE_STACK = 12;
    public static final int STORE_BUNDLE_ONE = 13;

    public static final Type<StorageActionPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(BetterShulkerHudCommon.MOD_ID, "storage_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageActionPayload> CODEC =
            StreamCodec.ofMember(
                    (payload, buffer) -> {
                        buffer.writeVarInt(payload.action);
                        buffer.writeInt(payload.inventorySlot);
                        buffer.writeInt(payload.contentSlot);
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, payload.expectedItem);
                    },
                    buffer -> new StorageActionPayload(
                            buffer.readVarInt(), buffer.readInt(), buffer.readInt(),
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)));

    public StorageActionPayload {
        expectedItem = expectedItem == null || expectedItem.isEmpty()
                ? ItemStack.EMPTY : expectedItem.copyWithCount(1);
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> handle(payload, context.player())));
    }

    private static void handle(StorageActionPayload payload,
                               net.minecraft.server.level.ServerPlayer player) {
        switch (payload.action) {
            case STORE_SHULKER_STACK ->
                    ShulkerStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, false, payload.expectedItem);
            case STORE_SHULKER_ONE ->
                    ShulkerStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, true, payload.expectedItem);
            case EXTRACT_ENDER_STACK ->
                    EnderChestStorageAccess.extract(
                            player, payload.contentSlot, false, payload.expectedItem);
            case EXTRACT_ENDER_ONE ->
                    EnderChestStorageAccess.extract(
                            player, payload.contentSlot, true, payload.expectedItem);
            case EXTRACT_ENDER_CURSOR ->
                    EnderChestStorageAccess.extractToCursor(
                            player, payload.contentSlot, payload.expectedItem);
            case INSERT_ENDER_CARRIED ->
                    EnderChestStorageAccess.insertCarried(player, payload.expectedItem);
            case STORE_ENDER_STACK ->
                    EnderChestStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, false, payload.expectedItem);
            case STORE_ENDER_ONE ->
                    EnderChestStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, true, payload.expectedItem);
            case EXTRACT_BUNDLE_STACK -> BundleStorageAccess.extract(
                    player, payload.inventorySlot, payload.contentSlot,
                    false, payload.expectedItem);
            case EXTRACT_BUNDLE_ONE -> BundleStorageAccess.extract(
                    player, payload.inventorySlot, payload.contentSlot,
                    true, payload.expectedItem);
            case EXTRACT_BUNDLE_CURSOR -> BundleStorageAccess.extractToCursor(
                    player, payload.inventorySlot, payload.contentSlot, payload.expectedItem);
            case INSERT_BUNDLE_CARRIED ->
                    BundleStorageAccess.insertCarried(
                            player, payload.inventorySlot, payload.expectedItem);
            case STORE_BUNDLE_STACK ->
                    BundleStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, false, payload.expectedItem);
            case STORE_BUNDLE_ONE ->
                    BundleStorageAccess.storeInventoryItem(
                            player, payload.inventorySlot, true, payload.expectedItem);
            default -> {
                // Unknown actions are ignored so future clients cannot trigger
                // an unintended fallback operation on older servers.
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
