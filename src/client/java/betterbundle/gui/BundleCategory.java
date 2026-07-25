package bettershulkerhud.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum BundleCategory {
    BUILDING_BLOCKS(CreativeModeTabs.BUILDING_BLOCKS, Items.BRICKS),
    COLORED_BLOCKS(CreativeModeTabs.COLORED_BLOCKS, Items.CYAN_WOOL),
    NATURAL_BLOCKS(CreativeModeTabs.NATURAL_BLOCKS, Items.GRASS_BLOCK),
    FUNCTIONAL_BLOCKS(CreativeModeTabs.FUNCTIONAL_BLOCKS, Items.OAK_SIGN),
    REDSTONE_BLOCKS(CreativeModeTabs.REDSTONE_BLOCKS, Items.REDSTONE),
    TOOLS_AND_UTILITIES(CreativeModeTabs.TOOLS_AND_UTILITIES, Items.DIAMOND_PICKAXE),
    COMBAT(CreativeModeTabs.COMBAT, Items.IRON_SWORD),
    FOOD_AND_DRINKS(CreativeModeTabs.FOOD_AND_DRINKS, Items.GOLDEN_APPLE),
    INGREDIENTS(CreativeModeTabs.INGREDIENTS, Items.IRON_INGOT),
    SPAWN_EGGS(CreativeModeTabs.SPAWN_EGGS, Items.CREEPER_SPAWN_EGG);

    private final ResourceKey<CreativeModeTab> tabKey;
    private final Item fallbackIcon;

    BundleCategory(ResourceKey<CreativeModeTab> tabKey, Item fallbackIcon) {
        this.tabKey = tabKey;
        this.fallbackIcon = fallbackIcon;
    }

    public String getDisplayName() {
        CreativeModeTab tab = getTab();
        return tab != null
                ? tab.getDisplayName().getString()
                : Component.translatable("itemGroup." + tabKey.identifier().getPath()).getString();
    }

    public ItemStack getIcon() {
        CreativeModeTab tab = getTab();
        return tab != null && !tab.getIconItem().isEmpty()
                ? tab.getIconItem().copy()
                : new ItemStack(fallbackIcon);
    }

    public boolean matches(ItemStack stack) {
        CreativeModeTab tab = getTab();
        if (tab == null || stack.isEmpty()) return false;
        return tab.getDisplayItems().stream()
                .anyMatch(display -> display.getItem() == stack.getItem());
    }

    private CreativeModeTab getTab() {
        return BuiltInRegistries.CREATIVE_MODE_TAB.getValue(tabKey);
    }

    public static void registerCategoryItems() {
        // Vanilla rebuilds the creative tab contents after joining a world.
    }
}
