package bettershulkerhud.gametest;

import bettershulkerhud.config.Configs;
import bettershulkerhud.compat.LitematicaEasyPlaceCompatAssertions;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.gui.StorageView;
import bettershulkerhud.server.EnderChestStorageAccess;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.BundleContents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class BetterShulkerHudClientGameTest implements FabricClientGameTest {
    private static final int OPERATION_TIMEOUT_TICKS = 20 * 15;
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "better-shulker-hud-client-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean quickShulkerProfile = FabricLoader.getInstance().isModLoaded("quickshulker");
        LOGGER.info("BSH_CLIENT_GAMETEST_START profile={}",
                quickShulkerProfile ? "quickshulker" : "base");
        LitematicaEasyPlaceCompatAssertions.verifyPolicy();
        context.runOnClient(client -> {
            Configs.Features.AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.LITEMATICA_RESTOCK.setBooleanValue(true);
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            Configs.General.DIAGNOSTIC_LOGGING.setBooleanValue(true);
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null);
            testMovedMainHandTotemDoesNotRestock(context, singleplayer);
            if (quickShulkerProfile) {
                testWaterBucketReplacement(context, singleplayer);
                testFullInventoryLitematicaHandSwap(context, singleplayer);
                testMainHandTotemRestock(context, singleplayer);
                testManualStoreDoesNotRestock(context, singleplayer);
                testRightClickDirectCursor(context, singleplayer);
                testItemScrollerHudWheel(context, singleplayer);
                testHudOrderStableDuringContinuousExtraction(context, singleplayer);
                testOffhandRestockToggle(context, singleplayer);
                testTakeToOffhandHotkey(context, singleplayer);
                testTakeToOffhandSwap(context, singleplayer);
                testPortableStorageViewsAndTransfers(context, singleplayer);
            }
            testHudToggleButtonDrag(context);
            testRecipeBookCoexistsWithHud(context, singleplayer);
        }
        LOGGER.info("BSH_CLIENT_GAMETEST_SUCCESS profile={}",
                quickShulkerProfile ? "quickshulker" : "base");
    }

    private static void testPortableStorageViewsAndTransfers(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, bundleWith(Items.DIAMOND, 5));
            inventory.setItem(2, new ItemStack(Items.ENDER_CHEST));
            inventory.setItem(3, shulkerWith(Items.EMERALD, 3));
            inventory.setItem(4, new ItemStack(Items.EMERALD, 4));
        });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getEnderChestInventory().clearContent();
            player.getEnderChestInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 6));
            player.getEnderChestInventory().setChanged();
        });
        context.waitTicks(5);

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> StorageClientNetwork.hasStorageServer()
                && BundlePanelRenderer.hasRenderablePanel());
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            assertEquals(BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight) + 4,
                    BundlePanelRenderer.returnButtonY(screen.topPos, screen.imageHeight),
                    "the return button must occupy the former top-right minimize position");
            assertTrue(BundlePanelRenderer.storageTabY(screen.topPos, screen.imageHeight)
                            > BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight),
                    "portable-storage tabs must be placed in the footer");
            assertEquals(BundlePanelRenderer.STORAGE_TAB_SIZE + 2,
                    BundlePanelRenderer.storageTabX(screen.leftPos, 1)
                            - BundlePanelRenderer.storageTabX(screen.leftPos, 0),
                    "portable-storage tabs must have stable non-overlapping spacing");
            assertTrue(BundlePanelRenderer.selectStorageView(StorageView.SHULKERS),
                    "shulker storage view must be selectable while a shulker is carried");
            ItemStack looseEmeralds = client.player.getInventory().getItem(4);
            assertTrue(StorageClientNetwork.storeShulkerInventorySlot(
                            4, false, looseEmeralds),
                    "direct server shulker-storage payload must be sendable");
        });
        context.waitFor(client -> countItem(client.player.getInventory(), Items.EMERALD) == 0
                && countShulkerItem(client.player.getInventory(), Items.EMERALD) == 7
                && BundlePanelRenderer.getVisibleItems().stream()
                .anyMatch(item -> item.stack().is(Items.EMERALD)
                        && item.stack().getCount() == 7));
        context.runOnClient(client -> assertTrue(
                BundlePanelRenderer.selectStorageView(StorageView.BUNDLES),
                "bundle storage view must be selectable while a bundle is carried"));
        context.waitFor(client -> BundlePanelRenderer.getVisibleItems().stream()
                .anyMatch(item -> item.stack().is(Items.DIAMOND)
                        && item.stack().getCount() == 5));

        context.runOnClient(client -> {
            BundlePanelRenderer.FlatItem diamonds = BundlePanelRenderer.getVisibleItems().stream()
                    .filter(item -> item.stack().is(Items.DIAMOND)).findFirst().orElseThrow();
            BundlePanelRenderer.ItemSource source = diamonds.sources().getFirst();
            assertTrue(StorageClientNetwork.extractBundle(
                            source.inventorySlot(), source.shulkerSlot(), true, source.stack()),
                    "bundle extraction payload must be sendable");
        });
        context.waitFor(client -> countItem(client.player.getInventory(), Items.DIAMOND) == 1
                && countBundleItem(client.player.getInventory(), Items.DIAMOND) == 4);

        // Deliberately reuse an invalid/stale content index. The expected-item
        // guard must relocate the diamond instead of touching another entry.
        context.runOnClient(client -> assertTrue(StorageClientNetwork.extractBundle(
                        1, 99, true, new ItemStack(Items.DIAMOND)),
                "stale bundle-slot extraction payload must still be accepted for validation"));
        context.waitFor(client -> countItem(client.player.getInventory(), Items.DIAMOND) == 2
                && countBundleItem(client.player.getInventory(), Items.DIAMOND) == 3);
        context.runOnClient(client -> {
            int slot = findInventoryItemSlot(client.player.getInventory(), Items.DIAMOND);
            assertTrue(slot >= 0, "extracted diamonds must exist in the player inventory");
            ItemStack stack = client.player.getInventory().getItem(slot);
            assertTrue(StorageClientNetwork.storeBundleInventorySlot(slot, false, stack),
                    "bundle return payload must be sendable");
        });
        context.waitFor(client -> countItem(client.player.getInventory(), Items.DIAMOND) == 0
                && countBundleItem(client.player.getInventory(), Items.DIAMOND) == 5);

        context.runOnClient(client -> assertTrue(
                BundlePanelRenderer.selectStorageView(StorageView.ENDER_CHEST),
                "ender-chest view must be selectable while an ender chest is carried"));
        context.waitFor(client -> StorageClientNetwork.isEnderLoaded()
                && BundlePanelRenderer.getVisibleItems().stream()
                .anyMatch(item -> item.stack().is(Items.GOLD_INGOT)
                        && item.stack().getCount() == 6));
        context.runOnClient(client -> assertTrue(StorageClientNetwork.extractEnder(
                        26, true, new ItemStack(Items.GOLD_INGOT)),
                "stale ender-chest-slot extraction payload must be sendable"));
        context.waitFor(client -> countItem(client.player.getInventory(), Items.GOLD_INGOT) == 1
                && countCachedEnderItem(Items.GOLD_INGOT) == 5);
        context.runOnClient(client -> assertTrue(StorageClientNetwork.extractEnder(
                        26, true, new ItemStack(Items.GOLD_INGOT)),
                "repeated ender-chest extraction must remain server validated"));
        context.waitFor(client -> countItem(client.player.getInventory(), Items.GOLD_INGOT) == 2
                && countCachedEnderItem(Items.GOLD_INGOT) == 4);
        context.runOnClient(client -> {
            int slot = findInventoryItemSlot(client.player.getInventory(), Items.GOLD_INGOT);
            assertTrue(slot >= 0, "extracted gold must exist in the player inventory");
            ItemStack stack = client.player.getInventory().getItem(slot);
            assertTrue(StorageClientNetwork.storeEnderInventorySlot(slot, false, stack),
                    "ender-chest return payload must be sendable");
        });
        context.waitFor(client -> countItem(client.player.getInventory(), Items.GOLD_INGOT) == 0
                && countCachedEnderItem(Items.GOLD_INGOT) == 6);
        context.setScreen(() -> null);

        // The last portable ender chest may be on the cursor. Preserve one
        // access item and insert only the excess, matching the reference mod.
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(2, ItemStack.EMPTY);
            player.containerMenu.setCarried(new ItemStack(Items.ENDER_CHEST, 3));
            EnderChestStorageAccess.insertCarried(
                    player, new ItemStack(Items.ENDER_CHEST));
            assertEquals(1, player.containerMenu.getCarried().getCount(),
                    "cursor-only ender-chest storage must preserve one portable access item");
            assertEquals(2, countEnderItem(player, Items.ENDER_CHEST),
                    "cursor-only ender-chest storage must insert only the excess chests");
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        context.waitTicks(3);
    }


    private static void testHudToggleButtonDrag(ClientGameTestContext context) {
        context.getInput().resizeWindow(2560, 1440);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            BundlePanelRenderer.resetToggleButtonInteraction();
            Configs.Features.SHOW_HUD_TOGGLE_BUTTON.setBooleanValue(true);
            Configs.General.HUD_TOGGLE_POSITION_EDIT.setBooleanValue(true);
            Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(-1);
            Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(-1);
        });
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);

        double[] cursor = context.computeOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            double scaleX = client.getWindow().getScreenWidth()
                    / (double) client.getWindow().getGuiScaledWidth();
            double scaleY = client.getWindow().getScreenHeight()
                    / (double) client.getWindow().getGuiScaledHeight();
            return new double[]{
                    (BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth) + 10) * scaleX,
                    (BundlePanelRenderer.toggleY(screen.topPos) + 10) * scaleY,
                    scaleX,
                    scaleY
            };
        });

        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().moveCursor(80 * cursor[2], -45 * cursor[3]);
        context.waitTick();
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();

        context.runOnClient(client -> {
            assertTrue(Configs.General.HUD_TOGGLE_POSITION_X.getIntegerValue() >= 0,
                    "dragging the HUD toggle must save its horizontal position");
            assertTrue(Configs.General.HUD_TOGGLE_POSITION_Y.getIntegerValue() >= 0,
                    "dragging the HUD toggle must save its vertical position");
            Configs.General.HUD_TOGGLE_POSITION_EDIT.setBooleanValue(false);
            Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(-1);
            Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(-1);
            BundlePanelRenderer.resetToggleButtonInteraction();
        });
        context.setScreen(() -> null);
    }

    private static void testRecipeBookCoexistsWithHud(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 64));
        });
        context.getInput().resizeWindow(2560, 1440);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            Configs.Features.HIDE_RECIPE_BOOK_BUTTON.setBooleanValue(false);
            Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(5000);
            Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(5000);
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        double[] recipeButtonPosition = new double[2];
        int[] layout = new int[4];
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            ImageButton recipeButton = findRecipeBookButton(screen);
            double scaleX = client.getWindow().getScreenWidth()
                    / (double) client.getWindow().getGuiScaledWidth();
            double scaleY = client.getWindow().getScreenHeight()
                    / (double) client.getWindow().getGuiScaledHeight();
            recipeButtonPosition[0] = (recipeButton.getX() + recipeButton.getWidth() / 2.0) * scaleX;
            recipeButtonPosition[1] = (recipeButton.getY() + recipeButton.getHeight() / 2.0) * scaleY;
            layout[0] = screen.leftPos;
            layout[1] = BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth);
            assertTrue(recipeButton.visible,
                    "recipe book button must be visible when its hide option is disabled");
            assertTrue(!BundlePanelRenderer.isRecipeBookOpen(),
                    "recipe book must start closed for the coexistence test");
        });
        context.getInput().setCursorPos(recipeButtonPosition[0], recipeButtonPosition[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> BundlePanelRenderer.isRecipeBookOpen());
        context.waitTicks(3);

        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            int panelX = BundlePanelRenderer.panelX(screen.leftPos);
            int panelRight = panelX + BundlePanelRenderer.panelWidth(screen.leftPos);
            int inventoryRight = screen.leftPos + screen.imageWidth;
            layout[2] = screen.leftPos;
            layout[3] = BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth);
            assertTrue(client.getWindow().getGuiScaledWidth() >= 500,
                    "coexistence test requires a wide GUI viewport");
            assertTrue(BundlePanelRenderer.isEffectivelyVisible(),
                    "HUD must remain visible while the recipe book is open");
            assertTrue(panelX >= inventoryRight,
                    "HUD must stay to the right of the inventory when the recipe book is open");
            assertTrue(panelRight <= client.getWindow().getGuiScaledWidth() - 4,
                    "HUD must remain inside the right screen edge");
            assertEquals(layout[2] - layout[0], layout[3] - layout[1],
                    "a saved HUD toggle position must follow the inventory recipe-book shift");
            Configs.Features.HIDE_RECIPE_BOOK_BUTTON.setBooleanValue(true);
        });
        context.waitFor(client -> !findRecipeBookButton((InventoryScreen) client.screen).visible);
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            ImageButton recipeButton = findRecipeBookButton(screen);
            double scaleX = client.getWindow().getScreenWidth()
                    / (double) client.getWindow().getGuiScaledWidth();
            double scaleY = client.getWindow().getScreenHeight()
                    / (double) client.getWindow().getGuiScaledHeight();
            recipeButtonPosition[0] = (recipeButton.getX() + recipeButton.getWidth() / 2.0) * scaleX;
            recipeButtonPosition[1] = (recipeButton.getY() + recipeButton.getHeight() / 2.0) * scaleY;
        });
        context.getInput().setCursorPos(recipeButtonPosition[0], recipeButtonPosition[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
        context.runOnClient(client -> {
            assertTrue(BundlePanelRenderer.isRecipeBookOpen(),
                    "clicking the hidden recipe book button position must not close the recipe book");
            Configs.Features.HIDE_RECIPE_BOOK_BUTTON.setBooleanValue(false);
            Configs.General.HUD_TOGGLE_POSITION_X.setIntegerValue(-1);
            Configs.General.HUD_TOGGLE_POSITION_Y.setIntegerValue(-1);
        });
        context.takeScreenshot("recipe-book-hud-coexistence");
        context.setScreen(() -> null);
    }

    private static ImageButton findRecipeBookButton(InventoryScreen screen) {
        for (var child : screen.children()) {
            if (child instanceof ImageButton button
                    && button.getWidth() == 20
                    && button.getHeight() == 18) {
                return button;
            }
        }
        throw new AssertionError("recipe book button was not registered on the inventory screen");
    }

    private static void testWaterBucketReplacement(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WATER_BUCKET));
            inventory.setItem(1, shulkerWith(Items.WATER_BUCKET, 1));
        });
        context.waitTicks(5);

        setInventorySlot(singleplayer, 0, new ItemStack(Items.BUCKET));
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getSelectedItem().is(Items.BUCKET));
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getSelectedItem().is(Items.WATER_BUCKET)
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);

        context.runOnClient(client -> {
            Inventory inventory = client.player.getInventory();
            assertEquals(1, countItem(inventory, Items.BUCKET),
                    "used water bucket must leave exactly one empty bucket");
            assertEquals(0, countShulkerItem(inventory, Items.WATER_BUCKET),
                    "replacement water bucket must be removed from its shulker");
        });
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getSelectedItem().is(Items.WATER_BUCKET),
                    "server must confirm the replacement water bucket in the selected slot");
            assertEquals(1, countItem(inventory, Items.BUCKET),
                    "server must retain exactly one used empty bucket");
        });
    }

    private static void testFullInventoryLitematicaHandSwap(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            for (int slot = 0; slot < 36; slot++) {
                inventory.setItem(slot, new ItemStack(Items.SEA_LANTERN, 64));
            }
            inventory.setItem(1, fullShulkerWith(Items.GRASS_BLOCK, 64));
        });
        context.waitTicks(5);

        context.runOnClient(client -> QuickShulkerExtractionController
                .requestLitematicaRestock(new ItemStack(Items.GRASS_BLOCK)));
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getSelectedItem().is(Items.GRASS_BLOCK)
                && client.player.getInventory().getSelectedItem().getCount() == 64
                && countShulkerItem(client.player.getInventory(), Items.GRASS_BLOCK) == 26 * 64
                && countShulkerItem(client.player.getInventory(), Items.SEA_LANTERN) == 64
                && client.player.containerMenu.getCarried().isEmpty()
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);

        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getSelectedItem().is(Items.GRASS_BLOCK)
                            && inventory.getSelectedItem().getCount() == 64,
                    "server must confirm the full requested stack in the selected hotbar slot");
            assertEquals(26 * 64, countShulkerItem(inventory, Items.GRASS_BLOCK),
                    "the source shulker must lose exactly one requested stack");
            assertEquals(64, countShulkerItem(inventory, Items.SEA_LANTERN),
                    "the original held stack must replace the requested shulker slot");
            assertTrue(server.getPlayerList().getPlayers().getFirst()
                            .containerMenu.getCarried().isEmpty(),
                    "the direct swap must leave the server cursor empty");
        });
    }

    private static void testMainHandTotemRestock(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
            inventory.setItem(1, shulkerWith(Items.TOTEM_OF_UNDYING, 1));
            inventory.setItem(2, new ItemStack(Items.TOTEM_OF_UNDYING));
        });
        context.waitTicks(5);

        setInventorySlot(singleplayer, 0, ItemStack.EMPTY);
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getSelectedItem().isEmpty());
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getSelectedItem().is(Items.TOTEM_OF_UNDYING)
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
    }

    private static void testMovedMainHandTotemDoesNotRestock(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
            inventory.setItem(1, shulkerWith(Items.TOTEM_OF_UNDYING, 1));
        });
        context.waitTicks(5);

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        moveInventorySlot(singleplayer, 0, 2);
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getItem(0).isEmpty()
                && client.player.getInventory().getItem(2).is(Items.TOTEM_OF_UNDYING));
        context.waitTicks(5);
        context.setScreen(() -> null);
        context.waitTicks(30);

        assertMovedTotemState(context, singleplayer);
    }

    private static void testManualStoreDoesNotRestock(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.TOTEM_OF_UNDYING));
            inventory.setItem(1, shulkerWith(Items.TOTEM_OF_UNDYING, 0));
        });
        context.waitTicks(5);

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.runOnClient(client -> {
            AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.screen;
            Inventory inventory = client.player.getInventory();
            int sourceMenuSlot = findInventoryMenuSlot(screen, inventory, 0);
            assertTrue(sourceMenuSlot >= 0, "selected hotbar slot must exist in inventory menu");
            client.gameMode.handleContainerInput(
                    screen.getMenu().containerId, sourceMenuSlot, 0,
                    ContainerInput.PICKUP, client.player);
            assertTrue(screen.getMenu().getCarried().is(Items.TOTEM_OF_UNDYING),
                    "manual store setup must pick up the held totem");
            QuickShulkerExtractionController.requestStoreCarried(screen);
        });
        context.waitFor(client -> {
            assertTrue(!(client.screen instanceof ShulkerBoxScreen),
                    "hidden QuickShulker operations must not expose a transient shulker screen");
            if (client.player != null
                    && client.player.containerMenu instanceof ShulkerBoxMenu menu) {
                int menuCount = 0;
                for (int slot = 0; slot < ShulkerContentsHelper.SHULKER_SIZE; slot++) {
                    ItemStack stack = menu.getSlot(slot).getItem();
                    if (stack.is(Items.TOTEM_OF_UNDYING)) menuCount += stack.getCount();
                }
                assertEquals(menuCount,
                        countHudShulkerItem(Items.TOTEM_OF_UNDYING),
                        "HUD count must follow the active shulker menu without waiting for close");
            }
            return client.player != null
                    && !QuickShulkerExtractionController.hasActiveOperation();
        }, OPERATION_TIMEOUT_TICKS);
        context.setScreen(() -> null);
        context.waitTicks(30);

        context.runOnClient(client -> {
            Inventory inventory = client.player.getInventory();
            assertTrue(inventory.getSelectedItem().isEmpty(),
                    "a manually stored held totem must not be auto-restocked");
            assertEquals(1, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "the manually stored totem must remain in its shulker");
        });
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getSelectedItem().isEmpty(),
                    "server must keep the selected slot empty after manual store");
            assertEquals(1, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "server must retain the manually stored totem in its shulker");
        });
    }

    private static void testOffhandRestockToggle(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        context.runOnClient(client ->
                Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(false));
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.TOTEM_OF_UNDYING, 1));
            inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        });
        context.waitTicks(5);

        setInventorySlot(singleplayer, Inventory.SLOT_OFFHAND, ItemStack.EMPTY);
        context.waitTicks(30);
        context.runOnClient(client -> {
            assertTrue(client.player.getInventory().getItem(Inventory.SLOT_OFFHAND).isEmpty(),
                    "disabled offhand restock must leave the offhand empty");
            assertEquals(1,
                    countShulkerItem(client.player.getInventory(), Items.TOTEM_OF_UNDYING),
                    "disabled offhand restock must not consume the source totem");
            Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(true);
        });

        context.waitFor(client -> client.player != null
                && client.player.getInventory().getItem(Inventory.SLOT_OFFHAND)
                        .is(Items.TOTEM_OF_UNDYING)
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
    }

    private static void testRightClickDirectCursor(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 5));
            inventory.setItem(2, ItemStack.EMPTY);
        });
        context.waitTicks(5);

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> !BundlePanelRenderer.getVisibleItems().isEmpty());

        double[] cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitFor(client ->
                QuickShulkerExtractionController.isCursorTransferInProgress());
        context.runOnClient(client -> {
            ItemStack preview = QuickShulkerExtractionController.getCursorTransferPreview();
            assertTrue(preview.is(Items.DIAMOND) && preview.getCount() == 5,
                    "right-click must expose the requested stack directly at the cursor");
            assertTrue(QuickShulkerExtractionController.getCursorStagingInventorySlot() >= 0,
                    "cursor transfer must retain an internal rollback-safe staging slot");
        });

        context.waitFor(client -> client.player != null
                && client.player.inventoryMenu.getCarried().is(Items.DIAMOND)
                && client.player.inventoryMenu.getCarried().getCount() == 5
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        singleplayer.getServer().runOnServer(server -> {
            ItemStack carried = server.getPlayerList().getPlayers().getFirst()
                    .inventoryMenu.getCarried();
            assertTrue(carried.is(Items.DIAMOND) && carried.getCount() == 5,
                    "server must confirm the right-click stack on the inventory cursor");
        });

        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            int destination = findInventoryMenuSlot(
                    screen, client.player.getInventory(), 2);
            assertTrue(destination >= 0,
                    "cursor cleanup destination must exist in the inventory menu");
            client.gameMode.handleContainerInput(
                    screen.getMenu().containerId, destination, 0,
                    ContainerInput.PICKUP, client.player);
        });
        context.waitFor(client -> client.player != null
                && client.player.inventoryMenu.getCarried().isEmpty());
        context.setScreen(() -> null);
    }

    private static void testItemScrollerHudWheel(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        if (!FabricLoader.getInstance().isModLoaded("itemscroller")) return;

        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 6));
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> !BundlePanelRenderer.getVisibleItems().isEmpty());

        double[] cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().scroll(1.0);
        context.waitTick();
        context.waitFor(client -> QuickShulkerExtractionController.hasActiveOperation());
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.runOnClient(client -> {
            assertEquals(1, countItem(client.player.getInventory(), Items.DIAMOND),
                    "one upward HUD wheel notch must extract exactly one item");
            assertEquals(5, countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                    "one upward HUD wheel notch must leave the remaining source items");
        });

        // Several wheel events in one input burst must be queued rather than
        // dropped or interpreted as HUD-list scrolling.
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 6));
        });
        context.waitTicks(5);
        context.waitFor(client -> !BundlePanelRenderer.getVisibleItems().isEmpty());
        cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        for (int i = 0; i < 6; i++) context.getInput().scroll(1.0);
        context.waitTick();
        context.waitFor(client -> QuickShulkerExtractionController.hasActiveOperation());
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.runOnClient(client -> {
            assertEquals(6, countItem(client.player.getInventory(), Items.DIAMOND),
                    "continuous HUD wheel events must extract every queued item");
            assertEquals(0, countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                    "continuous HUD wheel events must drain the source stack");
        });

        // The Item Scroller stack modifier must map to the normal HUD stack
        // extraction path, not to six single-item transactions.
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 7));
        });
        context.waitTicks(5);
        context.waitFor(client -> !BundlePanelRenderer.getVisibleItems().isEmpty());
        cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().holdShift();
        context.getInput().scroll(1.0);
        context.getInput().releaseShift();
        context.waitTick();
        context.waitFor(client -> QuickShulkerExtractionController.hasActiveOperation());
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.runOnClient(client -> {
            assertEquals(7, countItem(client.player.getInventory(), Items.DIAMOND),
                    "Item Scroller stack modifier must extract the whole HUD source stack");
            assertEquals(0, countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                    "stack extraction must empty the source stack");
        });
        context.setScreen(() -> null);
    }

    private static void testHudOrderStableDuringContinuousExtraction(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        context.runOnClient(client -> {
            Configs.Features.AUTO_RESTOCK.setBooleanValue(false);
            Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.setBooleanValue(false);
            Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(false);
        });
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWithTwo(
                    Items.DIAMOND, 10, Items.APPLE, 9));
        });
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> BundlePanelRenderer.getVisibleItems().size() >= 2);

        context.runOnClient(client -> {
            List<BundlePanelRenderer.FlatItem> items =
                    BundlePanelRenderer.getVisibleItems();
            assertTrue(items.getFirst().stack().is(Items.DIAMOND)
                            && items.getFirst().stack().getCount() == 10,
                    "the initial HUD order must be count-descending");
        });
        double[] cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> QuickShulkerExtractionController.hasActiveOperation());
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.waitFor(client -> BundlePanelRenderer.getVisibleItems().size() >= 2);
        context.runOnClient(client -> {
            List<BundlePanelRenderer.FlatItem> items =
                    BundlePanelRenderer.getVisibleItems();
            assertTrue(items.getFirst().stack().is(Items.DIAMOND)
                            && items.getFirst().stack().getCount() == 9,
                    "extracting while the inventory stays open must keep HUD order");
        });

        // The same coordinates must still address diamonds for the next click.
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.runOnClient(client -> {
            assertEquals(2, countItem(client.player.getInventory(), Items.DIAMOND),
                    "continuous HUD clicks must keep extracting the hovered item");
            assertEquals(8, countShulkerItem(
                            client.player.getInventory(), Items.DIAMOND),
                    "the second click must remove another diamond");
            assertEquals(9, countShulkerItem(
                            client.player.getInventory(), Items.APPLE),
                    "the second click must not switch to the tied apple entry");
        });
        context.runOnClient(client -> {
            Configs.Features.AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(true);
        });
        context.setScreen(() -> null);
        context.waitTick();
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> BundlePanelRenderer.getVisibleItems().size() >= 2);
        context.runOnClient(client -> {
            ItemStack first = BundlePanelRenderer.getVisibleItems().getFirst().stack();
            assertTrue(first.is(Items.APPLE) && first.getCount() == 9,
                    "closing and reopening the inventory must prepare a new count-based order");
        });
        context.setScreen(() -> null);
    }

    private static void testTakeToOffhandHotkey(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        context.runOnClient(client ->
                Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(false));
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 3));
        });
        context.waitTicks(5);

        pressOffhandHotkeyOverFirstHudItem(context);

        context.waitFor(client -> client.player != null
                && client.player.getInventory().getItem(Inventory.SLOT_OFFHAND).is(Items.DIAMOND)
                && client.player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 3
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.runOnClient(client -> assertEquals(0,
                countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                "F hotkey must remove the transferred diamonds from their shulker"));
        context.setScreen(() -> null);
    }

    private static void testTakeToOffhandSwap(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 3));
            inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        });
        context.waitTicks(5);

        pressOffhandHotkeyOverFirstHudItem(context);
        context.waitFor(client -> client.player != null
                && client.player.getInventory().getItem(Inventory.SLOT_OFFHAND).is(Items.DIAMOND)
                && client.player.getInventory().getItem(Inventory.SLOT_OFFHAND).getCount() == 3
                && countItem(client.player.getInventory(), Items.SHIELD) == 1
                && client.player.containerMenu.getCarried().isEmpty()
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
            assertTrue(offhand.is(Items.DIAMOND) && offhand.getCount() == 3,
                    "server must confirm swapped diamonds in the offhand");
            assertEquals(1, countItem(inventory, Items.SHIELD),
                    "server must return the replaced shield to the inventory");
            assertEquals(0, countShulkerItem(inventory, Items.DIAMOND),
                    "server must remove swapped diamonds from their shulker");
        });
        context.setScreen(() -> null);
    }

    private static void pressOffhandHotkeyOverFirstHudItem(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> !BundlePanelRenderer.getVisibleItems().isEmpty());

        double[] cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTick();
        context.getInput().pressKey(GLFW.GLFW_KEY_F);
    }

    private static double[] firstHudItemCursor(ClientGameTestContext context) {
        return context.computeOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            int guiX = BundlePanelRenderer.gridX(screen.leftPos)
                    + BundlePanelRenderer.SLOT_SIZE / 2;
            int guiY = BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight)
                    + BundlePanelRenderer.SLOT_SIZE / 2;
            double xScale = (double) client.getWindow().getScreenWidth()
                    / client.getWindow().getGuiScaledWidth();
            double yScale = (double) client.getWindow().getScreenHeight()
                    / client.getWindow().getGuiScaledHeight();
            return new double[]{guiX * xScale, guiY * yScale};
        });
    }

    private static void resetRememberedItems(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        });
        context.waitTicks(5);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
        });
        context.waitTicks(5);
    }

    private static void setInventory(
            TestSingleplayerContext singleplayer,
            java.util.function.Consumer<Inventory> setup) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            Inventory inventory = player.getInventory();
            inventory.clearContent();
            setup.accept(inventory);
            inventory.setChanged();
            player.inventoryMenu.broadcastFullState();
        });
    }

    private static void setInventorySlot(
            TestSingleplayerContext singleplayer, int slot, ItemStack stack) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().setItem(slot, stack);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastFullState();
        });
    }

    private static void moveInventorySlot(
            TestSingleplayerContext singleplayer, int sourceSlot, int targetSlot) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            Inventory inventory = player.getInventory();
            ItemStack moved = inventory.getItem(sourceSlot).copy();
            inventory.setItem(sourceSlot, ItemStack.EMPTY);
            inventory.setItem(targetSlot, moved);
            inventory.setChanged();
            player.inventoryMenu.broadcastFullState();
        });
    }

    private static void assertMovedTotemState(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.runOnClient(client -> {
            Inventory inventory = client.player.getInventory();
            assertTrue(inventory.getSelectedItem().isEmpty(),
                    "moving a held totem must leave the selected slot empty");
            assertTrue(inventory.getItem(2).is(Items.TOTEM_OF_UNDYING),
                    "the moved totem must remain in its destination slot");
            assertEquals(1, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "moving a held totem must not consume the shulker source");
        });
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getSelectedItem().isEmpty(),
                    "server must keep the selected slot empty after moving the held totem");
            assertTrue(inventory.getItem(2).is(Items.TOTEM_OF_UNDYING),
                    "server must retain the moved totem in its destination slot");
            assertEquals(1, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "server must retain the shulker source after moving the held totem");
        });
    }

    private static int findInventoryMenuSlot(
            AbstractContainerScreen<?> screen, Inventory inventory, int inventorySlot) {
        for (int menuSlot = 0; menuSlot < screen.getMenu().slots.size(); menuSlot++) {
            var slot = screen.getMenu().slots.get(menuSlot);
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                return menuSlot;
            }
        }
        return -1;
    }

    private static ItemStack shulkerWith(Item item, int count) {
        NonNullList<ItemStack> contents = NonNullList.withSize(
                ShulkerContentsHelper.SHULKER_SIZE, ItemStack.EMPTY);
        contents.set(0, new ItemStack(item, count));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return shulker;
    }

    private static ItemStack fullShulkerWith(Item item, int countPerSlot) {
        NonNullList<ItemStack> contents = NonNullList.withSize(
                ShulkerContentsHelper.SHULKER_SIZE, ItemStack.EMPTY);
        for (int slot = 0; slot < ShulkerContentsHelper.SHULKER_SIZE; slot++) {
            contents.set(slot, new ItemStack(item, countPerSlot));
        }
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return shulker;
    }

    private static ItemStack shulkerWithTwo(
            Item first, int firstCount, Item second, int secondCount) {
        NonNullList<ItemStack> contents = NonNullList.withSize(
                ShulkerContentsHelper.SHULKER_SIZE, ItemStack.EMPTY);
        contents.set(0, new ItemStack(first, firstCount));
        contents.set(1, new ItemStack(second, secondCount));
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
        return shulker;
    }

    private static ItemStack bundleWith(Item item, int count) {
        ItemStack bundle = new ItemStack(Items.BUNDLE);
        BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
        contents.tryInsert(new ItemStack(item, count));
        bundle.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        return bundle;
    }

    private static int findInventoryItemSlot(Inventory inventory, Item item) {
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot).is(item)) return slot;
        }
        return -1;
    }

    private static int countBundleItem(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof net.minecraft.world.item.BundleItem)) continue;
            count += stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                    .itemCopyStream().filter(stored -> stored.is(item))
                    .mapToInt(ItemStack::getCount).sum();
        }
        return count;
    }

    private static int countCachedEnderItem(Item item) {
        return StorageClientNetwork.getEnderContents().stream()
                .filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static int countEnderItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int slot = 0;
             slot < player.getEnderChestInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getEnderChestInventory().getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countItem(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int countShulkerItem(Inventory inventory, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            List<ItemStack> contents = ShulkerContentsHelper.getStacks(inventory.getItem(slot));
            for (ItemStack stack : contents) {
                if (stack.is(item)) count += stack.getCount();
            }
        }
        return count;
    }

    private static int countHudShulkerItem(Item item) {
        int count = 0;
        for (BundlePanelRenderer.ShulkerSlotEntry entry
                : BundlePanelRenderer.getAllShulkers()) {
            for (ItemStack stack : entry.contents()) {
                if (stack.is(item)) count += stack.getCount();
            }
        }
        return count;
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
