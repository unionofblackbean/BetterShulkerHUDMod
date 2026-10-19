package bettershulkerhud.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;

/**
 * Vanilla-capacity bundle extraction and insertion.
 * Adapted from Sakurastreet/BetterShulkerHUDMod under the MIT license.
 */
public final class BundleStorageAccess {
    private BundleStorageAccess() {}

    public static boolean isBundle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof BundleItem;
    }

    public static void extract(
            ServerPlayer player, int inventorySlot, int contentSlot,
            boolean oneItem, ItemStack expectedItem) {
        ItemStack bundle = getBundle(player, inventorySlot);
        BundleContents contents = getContents(bundle);
        contentSlot = resolveContentSlot(contents, contentSlot, expectedItem);
        if (contents == null || contentSlot < 0) return;
        ItemStack sample = contents.itemCopyStream().toList().get(contentSlot);
        if (oneItem) {
            if (moveToPlayer(player, bundle, inventorySlot, contentSlot, 1) > 0) sync(player);
        } else {
            extractMatchingStack(player, sample);
        }
    }

    public static void extractToCursor(
            ServerPlayer player, int inventorySlot, int contentSlot,
            ItemStack expectedItem) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) return;
        ItemStack bundle = getBundle(player, inventorySlot);
        contentSlot = resolveContentSlot(getContents(bundle), contentSlot, expectedItem);
        if (contentSlot < 0) return;
        ItemStack removed = remove(bundle, contentSlot, Integer.MAX_VALUE);
        if (removed.isEmpty()) return;
        PortableReturnTracker.remember(player, removed,
                PortableReturnTracker.Kind.BUNDLE, bundle, inventorySlot, removed.getCount());
        menu.setCarried(removed);
        sync(player);
    }

    public static void insertCarried(
            ServerPlayer player, int inventorySlot, ItemStack expectedItem) {
        ItemStack bundle = getBundle(player, inventorySlot);
        AbstractContainerMenu menu = player.containerMenu;
        ItemStack carried = menu.getCarried();
        if (bundle.isEmpty() || carried.isEmpty()
                || expectedItem == null || expectedItem.isEmpty()
                || !ItemStack.isSameItemSameComponents(carried, expectedItem)) return;
        int requested = carried.getCount();
        PortableReturnTracker.RestoreResult restore = PortableReturnTracker.restore(
                player, carried, requested, PortableReturnTracker.Kind.BUNDLE,
                (targetSlot, candidate) -> insertIntoBundle(
                        player, targetSlot, candidate));
        int inserted = restore.restoredCount();
        int fallback = insert(bundle, carried.copyWithCount(restore.fallbackCount()));
        inserted += fallback;
        if (inserted <= 0) return;
        PortableReturnTracker.consumeOtherKinds(
                player, carried, PortableReturnTracker.Kind.BUNDLE, fallback);
        carried.shrink(inserted);
        menu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        sync(player);
    }

    public static void storeInventoryItem(
            ServerPlayer player, int sourceSlot, boolean oneItem,
            ItemStack expectedItem) {
        if (sourceSlot < 0 || sourceSlot >= 36) return;
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (source.isEmpty() || expectedItem == null || expectedItem.isEmpty()
                || !ItemStack.isSameItemSameComponents(source, expectedItem)
                || !BundleContents.canItemBeInBundle(source)) return;

        int requested = oneItem ? 1 : source.getCount();
        PortableReturnTracker.RestoreResult restore = PortableReturnTracker.restore(
                player, source, requested, PortableReturnTracker.Kind.BUNDLE,
                (targetSlot, candidate) -> insertIntoBundle(player, targetSlot, candidate));
        int inserted = restore.restoredCount();
        int fallbackInserted = 0;
        ItemStack candidate = source.copyWithCount(restore.fallbackCount());
        for (int slot = firstBundleSlot(player, sourceSlot);
             slot >= 0 && !candidate.isEmpty();
             slot = nextBundleSlot(player, slot, sourceSlot)) {
            int moved = insertIntoBundle(player, slot, candidate);
            if (moved <= 0) continue;
            candidate.shrink(moved);
            inserted += moved;
            fallbackInserted += moved;
        }
        if (inserted <= 0) return;
        PortableReturnTracker.consumeOtherKinds(
                player, source, PortableReturnTracker.Kind.BUNDLE, fallbackInserted);
        source.shrink(inserted);
        player.getInventory().setItem(
                sourceSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        sync(player);
    }

    private static void extractMatchingStack(ServerPlayer player, ItemStack sample) {
        int remaining = sample.getMaxStackSize();
        boolean changed = false;
        for (int slot = firstBundleSlot(player, -1);
             slot >= 0 && remaining > 0;
             slot = nextBundleSlot(player, slot, -1)) {
            ItemStack bundle = player.getInventory().getItem(slot);
            while (remaining > 0) {
                int matchingIndex = findMatchingIndex(bundle, sample);
                if (matchingIndex < 0) break;
                int inserted = moveToPlayer(
                        player, bundle, slot, matchingIndex, remaining);
                if (inserted <= 0) {
                    if (changed) sync(player);
                    return;
                }
                remaining -= inserted;
                changed = true;
            }
        }
        if (changed) sync(player);
    }

    private static int moveToPlayer(
            ServerPlayer player, ItemStack bundle, int inventorySlot,
            int contentSlot, int requested) {
        ItemStack removed = remove(bundle, contentSlot, requested);
        if (removed.isEmpty()) return 0;
        ItemStack moving = removed.copy();
        int before = moving.getCount();
        player.getInventory().add(moving);
        int inserted = before - moving.getCount();
        if (inserted <= 0) {
            insert(bundle, removed);
            return 0;
        }
        PortableReturnTracker.remember(player, removed,
                PortableReturnTracker.Kind.BUNDLE, bundle, inventorySlot, inserted);
        if (inserted < removed.getCount()) {
            insert(bundle, removed.copyWithCount(removed.getCount() - inserted));
        }
        return inserted;
    }

    private static ItemStack remove(
            ItemStack bundle, int contentSlot, int requested) {
        BundleContents contents = getContents(bundle);
        if (contents == null || contentSlot < 0 || contentSlot >= contents.size()
                || requested <= 0) return ItemStack.EMPTY;
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        mutable.toggleSelectedItem(contentSlot);
        ItemStack stored = mutable.removeOne();
        if (stored == null || stored.isEmpty()) return ItemStack.EMPTY;
        int removedCount = Math.min(requested, stored.getCount());
        ItemStack removed = stored.copyWithCount(removedCount);
        stored.shrink(removedCount);
        if (!stored.isEmpty()) mutable.tryInsert(stored);
        bundle.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        return removed;
    }

    private static int insertIntoBundle(
            ServerPlayer player, int inventorySlot, ItemStack candidate) {
        ItemStack bundle = getBundle(player, inventorySlot);
        return bundle.isEmpty() || candidate.isEmpty()
                ? 0 : insert(bundle, candidate.copy());
    }

    private static int insert(ItemStack bundle, ItemStack candidate) {
        BundleContents contents = getContents(bundle);
        if (contents == null || candidate.isEmpty()) return 0;
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        int inserted = mutable.tryInsert(candidate);
        if (inserted > 0) {
            bundle.set(DataComponents.BUNDLE_CONTENTS, mutable.toImmutable());
        }
        return inserted;
    }

    private static int findMatchingIndex(ItemStack bundle, ItemStack sample) {
        BundleContents contents = getContents(bundle);
        if (contents == null) return -1;
        List<ItemStack> items = contents.itemCopyStream().toList();
        for (int index = 0; index < items.size(); index++) {
            if (ItemStack.isSameItemSameComponents(sample, items.get(index))) return index;
        }
        return -1;
    }

    private static int resolveContentSlot(
            BundleContents contents, int requestedSlot, ItemStack expectedItem) {
        if (contents == null || expectedItem == null || expectedItem.isEmpty()) return -1;
        List<ItemStack> items = contents.itemCopyStream().toList();
        if (requestedSlot >= 0 && requestedSlot < items.size()
                && ItemStack.isSameItemSameComponents(
                        items.get(requestedSlot), expectedItem)) {
            return requestedSlot;
        }
        for (int slot = 0; slot < items.size(); slot++) {
            if (ItemStack.isSameItemSameComponents(
                    items.get(slot), expectedItem)) return slot;
        }
        return -1;
    }

    private static BundleContents getContents(ItemStack bundle) {
        return isBundle(bundle)
                ? bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                : null;
    }

    private static ItemStack getBundle(ServerPlayer player, int slot) {
        if (!StorageServerUtil.isInventorySlot(slot)) return ItemStack.EMPTY;
        ItemStack stack = player.getInventory().getItem(slot);
        return isBundle(stack) ? stack : ItemStack.EMPTY;
    }

    private static int firstBundleSlot(ServerPlayer player, int excludedSlot) {
        return nextBundleSlot(player, -1, excludedSlot);
    }

    private static int nextBundleSlot(
            ServerPlayer player, int currentSlot, int excludedSlot) {
        for (int slot = currentSlot + 1; slot < 36; slot++) {
            if (slot != excludedSlot && isBundle(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static void sync(ServerPlayer player) {
        StorageServerUtil.syncInventory(player);
    }
}
