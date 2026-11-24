package bettershulkerhud.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds carried shulker contents to the client recipe book's available-item index. */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixin {
    @Inject(method = "accountStack(Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void accountCarriedShulkerContents(ItemStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;
        StackedItemContents stackedContents = (StackedItemContents) (Object) this;
        contents.nonEmptyItemCopyStream().forEach(stackedContents::accountStack);
    }
}
