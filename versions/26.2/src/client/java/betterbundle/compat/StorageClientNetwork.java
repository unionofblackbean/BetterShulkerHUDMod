package bettershulkerhud.compat;

import bettershulkerhud.network.EnderChestContentsPayload;
import bettershulkerhud.network.EnderChestRequestPayload;
import bettershulkerhud.network.StorageActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Client half of the optional same-mod server protocol. */
public final class StorageClientNetwork {
    private static final NonNullList<ItemStack> ENDER_CONTENTS =
            NonNullList.withSize(27, ItemStack.EMPTY);
    private static boolean initialized;
    private static boolean enderLoaded;
    private static boolean enderRequestPending;
    private static long enderRevision;
    private static int serverRequiredMessageUntilTick;

    private StorageClientNetwork() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientPlayNetworking.registerGlobalReceiver(
                EnderChestContentsPayload.TYPE, (payload, context) ->
                        context.client().execute(() -> updateEnderContents(payload.contents())));
    }

    public static void clearWorldState() {
        enderLoaded = false;
        enderRequestPending = false;
        for (int slot = 0; slot < ENDER_CONTENTS.size(); slot++) {
            ENDER_CONTENTS.set(slot, ItemStack.EMPTY);
        }
        enderRevision++;
        serverRequiredMessageUntilTick = 0;
    }

    public static boolean hasStorageServer() {
        return ClientPlayNetworking.canSend(StorageActionPayload.TYPE);
    }

    public static void showServerRequired() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null
                || client.player.tickCount < serverRequiredMessageUntilTick) return;
        client.player.sendOverlayMessage(Component.translatable(
                "message.better-shulker-hud.storage_server_required"));
        serverRequiredMessageUntilTick = client.player.tickCount + 40;
    }

    public static boolean hasPortableEnderChest() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return false;
        if (client.player.containerMenu.getCarried().is(Items.ENDER_CHEST)) return true;
        Inventory inventory = client.player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).is(Items.ENDER_CHEST)) return true;
        }
        return inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.ENDER_CHEST);
    }

    public static boolean isEnderChestAvailable() {
        return hasPortableEnderChest()
                && ClientPlayNetworking.canSend(EnderChestRequestPayload.TYPE);
    }

    public static boolean requestEnderContents() {
        if (!isEnderChestAvailable()) {
            enderRequestPending = false;
            return false;
        }
        ClientPlayNetworking.send(EnderChestRequestPayload.INSTANCE);
        enderRequestPending = true;
        return true;
    }

    public static boolean isEnderLoaded() {
        return enderLoaded;
    }

    public static boolean isEnderRequestPending() {
        return enderRequestPending;
    }

    public static long getEnderRevision() {
        return enderRevision;
    }

    public static List<ItemStack> getEnderContents() {
        return ENDER_CONTENTS.stream().map(ItemStack::copy).toList();
    }

    public static boolean storeShulkerInventorySlot(
            int inventorySlot, boolean oneItem, ItemStack expectedItem) {
        return send(oneItem ? StorageActionPayload.STORE_SHULKER_ONE
                : StorageActionPayload.STORE_SHULKER_STACK,
                inventorySlot, -1, expectedItem);
    }

    public static boolean extractEnder(
            int contentSlot, boolean oneItem, ItemStack expectedItem) {
        return send(oneItem ? StorageActionPayload.EXTRACT_ENDER_ONE
                : StorageActionPayload.EXTRACT_ENDER_STACK,
                -1, contentSlot, expectedItem);
    }

    public static boolean extractEnderToCursor(
            int contentSlot, ItemStack expectedItem) {
        return send(StorageActionPayload.EXTRACT_ENDER_CURSOR,
                -1, contentSlot, expectedItem);
    }

    public static boolean insertEnderCarried(ItemStack expectedItem) {
        return send(StorageActionPayload.INSERT_ENDER_CARRIED,
                -1, -1, expectedItem);
    }

    public static boolean storeEnderInventorySlot(
            int inventorySlot, boolean oneItem, ItemStack expectedItem) {
        return send(oneItem ? StorageActionPayload.STORE_ENDER_ONE
                : StorageActionPayload.STORE_ENDER_STACK,
                inventorySlot, -1, expectedItem);
    }

    public static boolean extractBundle(
            int inventorySlot, int contentSlot, boolean oneItem,
            ItemStack expectedItem) {
        return send(oneItem ? StorageActionPayload.EXTRACT_BUNDLE_ONE
                : StorageActionPayload.EXTRACT_BUNDLE_STACK,
                inventorySlot, contentSlot, expectedItem);
    }

    public static boolean extractBundleToCursor(
            int inventorySlot, int contentSlot, ItemStack expectedItem) {
        return send(StorageActionPayload.EXTRACT_BUNDLE_CURSOR,
                inventorySlot, contentSlot, expectedItem);
    }

    public static boolean insertBundleCarried(
            int inventorySlot, ItemStack expectedItem) {
        return send(StorageActionPayload.INSERT_BUNDLE_CARRIED,
                inventorySlot, -1, expectedItem);
    }

    public static boolean storeBundleInventorySlot(
            int inventorySlot, boolean oneItem, ItemStack expectedItem) {
        return send(oneItem ? StorageActionPayload.STORE_BUNDLE_ONE
                : StorageActionPayload.STORE_BUNDLE_STACK,
                inventorySlot, -1, expectedItem);
    }

    private static boolean send(
            int action, int inventorySlot, int contentSlot, ItemStack expectedItem) {
        if (!hasStorageServer()) return false;
        ClientPlayNetworking.send(
                new StorageActionPayload(
                        action, inventorySlot, contentSlot, expectedItem));
        return true;
    }

    private static void updateEnderContents(List<ItemStack> contents) {
        for (int slot = 0; slot < ENDER_CONTENTS.size(); slot++) {
            ENDER_CONTENTS.set(slot,
                    slot < contents.size() ? contents.get(slot).copy() : ItemStack.EMPTY);
        }
        enderLoaded = true;
        enderRequestPending = false;
        enderRevision++;
    }
}
