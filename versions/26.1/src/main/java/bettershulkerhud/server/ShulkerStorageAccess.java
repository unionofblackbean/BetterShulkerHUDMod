package bettershulkerhud.server;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Server-side fast path used by Space-click/drag when this mod is installed
 * on both sides. It never opens a menu and validates every source and target.
 */
public final class ShulkerStorageAccess {
    private static final int SHULKER_SIZE = 27;

    private ShulkerStorageAccess() {}

    public static void storeInventoryItem(
            ServerPlayer player, int sourceSlot, boolean oneItem,
            ItemStack expectedItem) {
        if (sourceSlot < 0 || sourceSlot >= 36) return;
        ItemStack source = player.getInventory().getItem(sourceSlot);
        if (source.isEmpty() || expectedItem == null || expectedItem.isEmpty()
                || !ItemStack.isSameItemSameComponents(source, expectedItem)
                || StorageServerUtil.isShulker(source)) return;

        int requested = oneItem ? 1 : source.getCount();
        ItemStack candidate = source.copyWithCount(requested);
        int inserted = 0;

        // Prefer matching stacks across all shulkers before consuming a new
        // empty slot. This mirrors the existing client controller's fallback.
        for (int pass = 0; pass < 2 && !candidate.isEmpty(); pass++) {
            for (int targetSlot = 0; targetSlot < 36 && !candidate.isEmpty(); targetSlot++) {
                ItemStack shulker = player.getInventory().getItem(targetSlot);
                if (!StorageServerUtil.isShulker(shulker) || shulker.getCount() != 1) continue;
                int moved = insertIntoShulker(shulker, candidate, pass == 0);
                if (moved > 0) {
                    candidate.shrink(moved);
                    inserted += moved;
                }
            }
        }
        if (inserted <= 0) return;
        source.shrink(inserted);
        player.getInventory().setItem(
                sourceSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        StorageServerUtil.syncInventory(player);
    }

    private static int insertIntoShulker(
            ItemStack shulker, ItemStack candidate, boolean matchingOnly) {
        NonNullList<ItemStack> contents = NonNullList.withSize(
                SHULKER_SIZE, ItemStack.EMPTY);
        shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .copyInto(contents);

        int remaining = candidate.getCount();
        for (int slot = 0; slot < contents.size() && remaining > 0; slot++) {
            ItemStack target = contents.get(slot);
            boolean matching = !target.isEmpty()
                    && ItemStack.isSameItemSameComponents(target, candidate);
            if (matchingOnly ? !matching : !target.isEmpty()) continue;
            int capacity = matching
                    ? Math.max(0, target.getMaxStackSize() - target.getCount())
                    : candidate.getMaxStackSize();
            int moved = Math.min(remaining, capacity);
            if (moved <= 0) continue;
            if (matching) target.grow(moved);
            else contents.set(slot, candidate.copyWithCount(moved));
            remaining -= moved;
        }
        int inserted = candidate.getCount() - remaining;
        if (inserted > 0) {
            shulker.set(DataComponents.CONTAINER,
                    ItemContainerContents.fromItems(contents));
        }
        return inserted;
    }
}
