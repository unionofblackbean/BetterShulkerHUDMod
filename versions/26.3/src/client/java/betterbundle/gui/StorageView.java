package bettershulkerhud.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum StorageView {
    SHULKERS("shulkers", Items.SHULKER_BOX),
    ENDER_CHEST("ender_chest", Items.ENDER_CHEST),
    BUNDLES("bundles", Items.BUNDLE);

    private final String translationSuffix;
    private final Item icon;

    StorageView(String translationSuffix, Item icon) {
        this.translationSuffix = translationSuffix;
        this.icon = icon;
    }

    public ItemStack icon() {
        return new ItemStack(icon);
    }

    public Component displayName() {
        return Component.translatable(
                "better-shulker-hud.storage_view." + translationSuffix);
    }
}
