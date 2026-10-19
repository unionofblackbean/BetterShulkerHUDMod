package bettershulkerhud.server;

import bettershulkerhud.network.EnderChestContentsPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Adapted from Sakurastreet/BetterShulkerHUDMod under the MIT license. */
public final class EnderChestStorageAccess {
    private EnderChestStorageAccess() {}

    public static boolean hasPortableAccess(Player player) {
        return countPortableEnderChests(player) > 0;
    }

    public static void extract(
            ServerPlayer player, int contentSlot, boolean oneItem,
            ItemStack expectedItem) {
        if (!hasPortableAccess(player)) return;
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        contentSlot = resolveContentSlot(container, contentSlot, expectedItem);
        if (contentSlot < 0) return;
        ItemStack stored = container.getItem(contentSlot);
        if (stored.isEmpty()) return;
        if (oneItem) {
            if (moveToPlayer(player, container, contentSlot, 1) > 0) sync(player, container);
        } else {
            extractMatchingStack(player, container, stored);
        }
    }

    public static void extractToCursor(
            ServerPlayer player, int contentSlot, ItemStack expectedItem) {
        if (!hasPortableAccess(player)) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return;
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        contentSlot = resolveContentSlot(container, contentSlot, expectedItem);
        if (contentSlot < 0) return;
        ItemStack stored = container.getItem(contentSlot);
        if (stored.isEmpty()) return;
        ItemStack moved = stored.copy();
        PortableReturnTracker.remember(player, stored,
                PortableReturnTracker.Kind.ENDER_CHEST, ItemStack.EMPTY, -1, moved.getCount());
        container.setItem(contentSlot, ItemStack.EMPTY);
        menu.setCarried(moved);
        sync(player, container);
    }

    public static void insertCarried(ServerPlayer player, ItemStack expectedItem) {
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        int portableCount = countPortableEnderChests(player);
        if (carried.isEmpty()
                || expectedItem == null || expectedItem.isEmpty()
                || !ItemStack.isSameItemSameComponents(carried, expectedItem)
                || portableCount <= 0 && !carried.is(Items.ENDER_CHEST)) return;
        int requested = carried.getCount();
        if (carried.is(Items.ENDER_CHEST)) {
            requested = Math.min(requested, portableCount + carried.getCount() - 1);
            if (requested <= 0) return;
        }
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        PortableReturnTracker.RestoreResult restore = PortableReturnTracker.restore(
                player, carried, requested, PortableReturnTracker.Kind.ENDER_CHEST,
                (ignored, candidate) -> insert(container, candidate));
        int inserted = restore.restoredCount();
        int fallback = insert(
                container, carried.copyWithCount(restore.fallbackCount()));
        inserted += fallback;
        if (inserted <= 0) return;
        PortableReturnTracker.consumeOtherKinds(
                player, carried, PortableReturnTracker.Kind.ENDER_CHEST, fallback);
        carried.shrink(inserted);
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        sync(player, container);
    }

    public static void storeInventoryItem(
            ServerPlayer player, int sourceSlot, boolean oneItem,
            ItemStack expectedItem) {
        if (sourceSlot < 0 || sourceSlot >= 36 || !hasPortableAccess(player)) return;
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (source.isEmpty() || expectedItem == null || expectedItem.isEmpty()
                || !ItemStack.isSameItemSameComponents(source, expectedItem)) return;
        int requested = oneItem ? 1 : source.getCount();
        if (source.is(Items.ENDER_CHEST)) {
            requested = Math.min(requested, countPortableEnderChests(player) - 1);
            if (requested <= 0) return;
        }
        PlayerEnderChestContainer container = player.getEnderChestInventory();
        PortableReturnTracker.RestoreResult restore = PortableReturnTracker.restore(
                player, source, requested, PortableReturnTracker.Kind.ENDER_CHEST,
                (ignored, candidate) -> insert(container, candidate));
        int inserted = restore.restoredCount();
        int fallback = insert(container, source.copyWithCount(restore.fallbackCount()));
        inserted += fallback;
        if (inserted <= 0) return;
        PortableReturnTracker.consumeOtherKinds(
                player, source, PortableReturnTracker.Kind.ENDER_CHEST, fallback);
        source.shrink(inserted);
        player.getInventory().setItem(
                sourceSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        sync(player, container);
    }

    private static void extractMatchingStack(
            ServerPlayer player, PlayerEnderChestContainer container, ItemStack sample) {
        int remaining = sample.getMaxStackSize();
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stored = container.getItem(slot);
            if (stored.isEmpty() || !ItemStack.isSameItemSameComponents(sample, stored)) continue;
            int requested = Math.min(remaining, stored.getCount());
            int inserted = moveToPlayer(player, container, slot, requested);
            if (inserted <= 0) break;
            remaining -= inserted;
            changed = true;
            if (inserted < requested) break;
        }
        if (changed) sync(player, container);
    }

    private static int moveToPlayer(
            ServerPlayer player, PlayerEnderChestContainer container,
            int slot, int requested) {
        ItemStack stored = container.getItem(slot);
        ItemStack moving = stored.copyWithCount(Math.min(requested, stored.getCount()));
        int before = moving.getCount();
        player.getInventory().add(moving);
        int inserted = before - moving.getCount();
        if (inserted <= 0) return 0;
        PortableReturnTracker.remember(player, stored,
                PortableReturnTracker.Kind.ENDER_CHEST, ItemStack.EMPTY, -1, inserted);
        stored.shrink(inserted);
        container.setItem(slot, stored.isEmpty() ? ItemStack.EMPTY : stored);
        return inserted;
    }

    private static int insert(PlayerEnderChestContainer container, ItemStack candidate) {
        if (candidate.isEmpty()) return 0;
        ItemStack moving = candidate.copy();
        int before = moving.getCount();
        ItemStack remainder = container.addItem(moving);
        return before - remainder.getCount();
    }

    private static int resolveContentSlot(
            PlayerEnderChestContainer container, int requestedSlot,
            ItemStack expectedItem) {
        if (expectedItem == null || expectedItem.isEmpty()) return -1;
        if (requestedSlot >= 0 && requestedSlot < container.getContainerSize()
                && ItemStack.isSameItemSameComponents(
                        container.getItem(requestedSlot), expectedItem)) {
            return requestedSlot;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (ItemStack.isSameItemSameComponents(
                    container.getItem(slot), expectedItem)) return slot;
        }
        return -1;
    }

    private static int countPortableEnderChests(Player player) {
        if (player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.ENDER_CHEST)) count += stack.getCount();
        }
        if (player.getOffhandItem().is(Items.ENDER_CHEST)) {
            count += player.getOffhandItem().getCount();
        }
        return count;
    }

    private static void sync(
            ServerPlayer player, PlayerEnderChestContainer container) {
        container.setChanged();
        StorageServerUtil.syncInventory(player);
        EnderChestContentsPayload.send(player);
    }
}
