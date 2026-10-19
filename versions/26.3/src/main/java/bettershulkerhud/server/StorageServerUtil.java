package bettershulkerhud.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

final class StorageServerUtil {
    private StorageServerUtil() {}

    static boolean isInventorySlot(int slot) {
        return slot >= 0 && slot < 36 || slot == Inventory.SLOT_OFFHAND;
    }

    static boolean isShulker(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
