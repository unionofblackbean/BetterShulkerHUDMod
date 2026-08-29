package bettershulkerhud.mixin;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.InventoryUtils", remap = false)
public abstract class LitematicaInventoryUtilsMixin {
    @Inject(
            method = "schematicWorldPickBlock",
            at = @At("TAIL"),
            require = 0,
            remap = false)
    private static void betterShulkerHud$restockForEasyPlace(
            ItemStack required, BlockPos pos, Level level,
            Minecraft client, CallbackInfo ci) {
        QuickShulkerExtractionController.requestLitematicaRestock(required);
    }
}
