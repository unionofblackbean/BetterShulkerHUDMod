package bettershulkerhud.compat;

import bettershulkerhud.BetterBundleMod;
import bettershulkerhud.gui.BundlePanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.List;

/** Supplies missing ingredients one at a time, then asks vanilla to fill the 2x2 grid. */
public final class RecipePreparationController {
    private static final int TIMEOUT_TICKS = 20 * 20;
    private static Pending pending;

    private RecipePreparationController() {}

    public static boolean begin(RecipeDisplayId recipeId, List<Ingredient> requirements) {
        Minecraft client = Minecraft.getInstance();
        RecipeBookMenu menu = activeMenu(client);
        boolean workbench = menu instanceof CraftingMenu;
        BlockPos tablePos = workbench ? craftingTableUnderCrosshair(client) : null;
        if (pending != null || client.player == null || client.gameMode == null
                || menu == null || workbench && tablePos == null
                || !menu.getCarried().isEmpty()
                || QuickShulkerExtractionController.shouldPreserveHudOrderOnScreenTransition()) {
            return false;
        }
        List<BundlePanelRenderer.FlatItem> missing = planNow(client, requirements);
        if (missing == null || missing.isEmpty()) return false;
        pending = new Pending(List.copyOf(requirements), () ->
                client.gameMode.handlePlaceRecipe(
                        client.player.containerMenu.containerId, recipeId, false),
                false, workbench, tablePos, menu.containerId);
        return true;
    }

    /** JEI keeps its ordinary transfer implementation; this only supplies missing items. */
    public static boolean beginJei(List<Ingredient> requirements, Runnable transfer) {
        Minecraft client = Minecraft.getInstance();
        RecipeBookMenu menu = activeMenu(client);
        boolean workbench = menu instanceof CraftingMenu;
        BlockPos tablePos = workbench ? craftingTableUnderCrosshair(client) : null;
        if (pending != null || client.player == null || client.gameMode == null
                || menu == null || workbench && tablePos == null
                || !menu.getCarried().isEmpty()
                || QuickShulkerExtractionController.shouldPreserveHudOrderOnScreenTransition()) {
            return false;
        }
        List<BundlePanelRenderer.FlatItem> missing = planNow(client, requirements);
        if (missing == null || missing.isEmpty()) return false;
        // Defer the screen switch until JEI finishes its transfer-button callback.
        pending = new Pending(List.copyOf(requirements), transfer,
                menu instanceof InventoryMenu, workbench, tablePos, menu.containerId);
        return true;
    }

    public static List<BundlePanelRenderer.FlatItem> preview(List<Ingredient> requirements) {
        Minecraft client = Minecraft.getInstance();
        return activeMenu(client) != null
                ? planNow(client, requirements) : null;
    }

    /** Read-only operation state used to reject layout editing during recipe supply. */
    public static boolean isRecipeSupplyPending() {
        return pending != null;
    }

    public static void onClientTick(Minecraft client) {
        Pending task = pending;
        if (task == null) return;
        if (client.player == null || client.gameMode == null
                || ++task.ticks > TIMEOUT_TICKS) {
            fail(client);
            return;
        }
        if (task.switchToInventoryScreen) {
            if (client.player.containerMenu != client.player.inventoryMenu) {
                fail(client);
                return;
            }
            task.switchToInventoryScreen = false;
            client.setScreen(new InventoryScreen(client.player));
            return;
        }
        if (task.workbench) {
            if (client.player.containerMenu instanceof CraftingMenu menu
                    && client.screen instanceof CraftingScreen) {
                if (task.reopenRequested) {
                    BetterBundleMod.LOGGER.info(
                            "Recipe supply reopened vanilla crafting table at {} (menu {})",
                            task.tablePos, menu.containerId);
                    task.menuId = menu.containerId;
                    task.reopenRequested = false;
                    restoreSelectedSlot(client, task);
                }
            } else if (client.player.containerMenu == client.player.inventoryMenu) {
                if (QuickShulkerExtractionController.isRecipeSupplyActive()) return;
                if (!task.reopenRequested) {
                    if (!reopenCraftingTable(client, task)) {
                        fail(client);
                        return;
                    }
                    task.reopenRequested = true;
                }
                return;
            } else {
                return;
            }
        }
        if (QuickShulkerExtractionController.shouldPreserveHudOrderOnScreenTransition()) {
            return;
        }
        RecipeBookMenu activeMenu = activeMenu(client);
        if (activeMenu == null || activeMenu.containerId != task.menuId
                || !activeMenu.getCarried().isEmpty()) {
            fail(client);
            return;
        }

        List<BundlePanelRenderer.FlatItem> missing = planNow(client, task.requirements);
        if (missing == null) {
            fail(client);
            return;
        }
        if (task.awaitingItem) {
            // An extraction must reduce the deficit before another box can be opened.
            if (missing.size() >= task.previousDeficit) {
                fail(client);
                return;
            }
            task.awaitingItem = false;
        }
        if (missing.isEmpty()) {
            pending = null;
            try {
                task.placeAction.run();
            } catch (RuntimeException exception) {
                BetterBundleMod.LOGGER.warn("Recipe transfer failed after shulker supply", exception);
                fail(client);
            }
            return;
        }
        task.previousDeficit = missing.size();
        if (task.workbench && !canFitBatch(client.player.getInventory(), missing)) {
            fail(client);
            return;
        }
        boolean started = task.workbench
                ? QuickShulkerExtractionController.requestRecipeIngredients(missing)
                : QuickShulkerExtractionController.requestRecipeIngredient(missing.getFirst());
        if (!started) {
            fail(client);
            return;
        }
        task.awaitingItem = true;
    }

    public static void clearWorldState() {
        pending = null;
    }

    private static List<BundlePanelRenderer.FlatItem> planNow(
            Minecraft client, List<Ingredient> requirements) {
        Inventory inventory = client.player.getInventory();
        RecipeBookMenu menu = activeMenu(client);
        if (menu == null) return null;
        List<ItemStack> looseAndGrid = new ArrayList<>(40);
        for (int i = 0; i < 36; i++) looseAndGrid.add(inventory.getItem(i));
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            for (Slot slot : craftingMenu.getInputGridSlots()) looseAndGrid.add(slot.getItem());
        }
        return RecipeSupplyPlanner.plan(requirements, looseAndGrid,
                BundlePanelRenderer.buildFlatItemList(BundlePanelRenderer.getAllShulkers()));
    }

    private static void fail(Minecraft client) {
        if (pending != null) restoreSelectedSlot(client, pending);
        pending = null;
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(
                    "message.better-shulker-hud.recipe_supply_failed"));
        }
    }

    private static final class Pending {
        private final List<Ingredient> requirements;
        private final Runnable placeAction;
        private boolean switchToInventoryScreen;
        private int ticks;
        private int previousDeficit;
        private boolean awaitingItem;
        private final boolean workbench;
        private final BlockPos tablePos;
        private int menuId;
        private boolean reopenRequested;
        private int restoreSelectedSlot = -1;

        private Pending(List<Ingredient> requirements, Runnable placeAction,
                boolean switchToInventoryScreen, boolean workbench,
                BlockPos tablePos, int menuId) {
            this.requirements = requirements;
            this.placeAction = placeAction;
            this.switchToInventoryScreen = switchToInventoryScreen;
            this.workbench = workbench;
            this.tablePos = tablePos;
            this.menuId = menuId;
        }
    }

    private static RecipeBookMenu activeMenu(Minecraft client) {
        if (client.player == null) return null;
        if (client.screen instanceof InventoryScreen
                && client.player.containerMenu == client.player.inventoryMenu) {
            return client.player.inventoryMenu;
        }
        if (client.screen instanceof CraftingScreen
                && client.player.containerMenu instanceof CraftingMenu menu) return menu;
        return null;
    }

    private static BlockPos craftingTableUnderCrosshair(Minecraft client) {
        if (client.level == null || !(client.hitResult instanceof BlockHitResult hit)) return null;
        BlockPos pos = hit.getBlockPos();
        return client.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE) ? pos.immutable() : null;
    }

    private static boolean reopenCraftingTable(Minecraft client, Pending task) {
        BlockPos pos = task.tablePos;
        if (client.level == null || client.player == null || client.gameMode == null
                || !client.level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) return false;

        // Vanilla's empty-hand fallback is only evaluated for MAIN_HAND.
        // Temporarily select an empty hotbar slot rather than risk using a held item.
        if (!client.player.getMainHandItem().isEmpty()) {
            Inventory inventory = client.player.getInventory();
            int emptyHotbarSlot = -1;
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).isEmpty()) {
                    emptyHotbarSlot = slot;
                    break;
                }
            }
            if (emptyHotbarSlot < 0) return false;
            task.restoreSelectedSlot = inventory.getSelectedSlot();
            inventory.setSelectedSlot(emptyHotbarSlot);
        }

        BlockHitResult hit = new BlockHitResult(
                Vec3.atLowerCornerOf(pos).add(0.5, 1.0, 0.5), Direction.UP, pos, false);
        var result = client.gameMode.useItemOn(
                client.player, InteractionHand.MAIN_HAND, hit);
        BetterBundleMod.LOGGER.info(
                "Request vanilla crafting-table reopen at {} returned {}", pos, result);
        if (!result.consumesAction()) {
            restoreSelectedSlot(client, task);
            return false;
        }
        return true;
    }

    private static void restoreSelectedSlot(Minecraft client, Pending task) {
        if (task.restoreSelectedSlot < 0 || client.player == null) return;
        client.player.getInventory().setSelectedSlot(task.restoreSelectedSlot);
        task.restoreSelectedSlot = -1;
    }

    private static boolean canFitBatch(
            Inventory inventory, List<BundlePanelRenderer.FlatItem> items) {
        List<ItemStack> simulated = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) simulated.add(inventory.getItem(slot).copy());
        for (BundlePanelRenderer.FlatItem flat : items) {
            ItemStack remaining = flat.stack().copyWithCount(1);
            for (ItemStack stack : simulated) {
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, remaining)
                        && stack.getCount() < stack.getMaxStackSize()) {
                    stack.grow(1);
                    remaining = ItemStack.EMPTY;
                    break;
                }
            }
            if (!remaining.isEmpty()) {
                int empty = -1;
                for (int slot = 0; slot < simulated.size(); slot++) {
                    if (simulated.get(slot).isEmpty()) {
                        empty = slot;
                        break;
                    }
                }
                if (empty < 0) return false;
                simulated.set(empty, remaining);
            }
        }
        return true;
    }
}
