package bettershulkerhud.util;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public final class ShulkerContentsHelper {
    public static final int SHULKER_SIZE = 27;

    private ShulkerContentsHelper() {}

    public static boolean isShulker(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock;
    }

    public static List<ItemStack> getStacks(ItemStack stack) {
        if (!isShulker(stack)) return List.of();

        ItemContainerContents contents = stack.getOrDefault(
                DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> stacks = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        contents.copyInto(stacks);
        return stacks;
    }

    public static boolean isNonEmptyShulker(ItemStack stack) {
        return getStacks(stack).stream().anyMatch(item -> !item.isEmpty());
    }
}
