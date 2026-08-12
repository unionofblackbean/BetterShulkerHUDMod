package bettershulkerhud.gametest;

import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.config.Configs;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class BetterShulkerHudClientGameTest implements FabricClientGameTest {
    private static final int OPERATION_TIMEOUT_TICKS = 20 * 15;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            Configs.Features.AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(true);
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            Configs.General.DIAGNOSTIC_LOGGING.setBooleanValue(true);
        });

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null);
            testWaterBucketReplacement(context, singleplayer);
            testMainHandTotemRestock(context, singleplayer);
            testMovedMainHandTotemDoesNotRestock(context, singleplayer);
            testManualStoreDoesNotRestock(context, singleplayer);
            testHudOrderStableDuringContinuousExtraction(context, singleplayer);
            testOffhandRestockToggle(context, singleplayer);
            testTakeToOffhandHotkey(context, singleplayer);
            testTakeToOffhandSwap(context, singleplayer);
            testHudToggleButtonDrag(context);
            testRecipeBookCoexistsWithHud(context, singleplayer);
        }
    }

    private static void testHudToggleButtonDrag(ClientGameTestContext context) {
        context.getInput().resizeWindow(2560, 1440);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
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
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getSelectedItem().is(Items.TOTEM_OF_UNDYING),
                    "server must confirm the replacement totem in the selected slot");
            assertEquals(0, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "server must remove the replacement totem from its shulker");
        });
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
            client.gameMode.handleInventoryMouseClick(
                    screen.getMenu().containerId, sourceMenuSlot, 0,
                    ClickType.PICKUP, client.player);
            assertTrue(screen.getMenu().getCarried().is(Items.TOTEM_OF_UNDYING),
                    "manual store setup must pick up the held totem");
            QuickShulkerExtractionController.requestStoreCarried(screen);
        });
        context.waitFor(client -> client.player != null
                && !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
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
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.TOTEM_OF_UNDYING),
                    "server must confirm the replacement totem in the offhand");
            assertEquals(0, countShulkerItem(inventory, Items.TOTEM_OF_UNDYING),
                    "server must remove the offhand totem from its shulker");
        });
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
        singleplayer.getServer().runOnServer(server -> {
            Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
            ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
            assertTrue(offhand.is(Items.DIAMOND) && offhand.getCount() == 3,
                    "server must confirm all hotkey-transferred diamonds in the offhand");
            assertEquals(0, countShulkerItem(inventory, Items.DIAMOND),
                    "server must remove hotkey-transferred diamonds from their shulker");
        });
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
        context.runOnClient(client -> assertTrue(
                BundlePanelRenderer.getVisibleItems().getFirst().stack().is(Items.DIAMOND),
                "the initial HUD order must be count-descending"));

        double[] cursor = firstHudItemCursor(context);
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> QuickShulkerExtractionController.hasActiveOperation());
        context.waitFor(client -> !QuickShulkerExtractionController.hasActiveOperation(),
                OPERATION_TIMEOUT_TICKS);
        context.waitFor(client -> BundlePanelRenderer.getVisibleItems().size() >= 2);
        context.runOnClient(client -> {
            ItemStack first = BundlePanelRenderer.getVisibleItems().getFirst().stack();
            assertTrue(first.is(Items.DIAMOND) && first.getCount() == 9,
                    "extracting while the inventory stays open must keep HUD order");
        });

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

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
