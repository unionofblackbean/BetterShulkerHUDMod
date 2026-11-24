package bettershulkerhud.gametest;

import bettershulkerhud.config.Configs;
import bettershulkerhud.compat.LitematicaEasyPlaceCompatAssertions;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.RecipeSupplyPlanner;
import bettershulkerhud.mixin.RecipeBookComponentAccessor;
import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.core.HudOverlayLayout;
import bettershulkerhud.gui.StorageView;
import bettershulkerhud.gui.BundleCategory;
import bettershulkerhud.server.EnderChestStorageAccess;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.BundleContents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class BetterShulkerHudClientGameTest implements FabricClientGameTest {
    private static final int OPERATION_TIMEOUT_TICKS = 20 * 15;
    private static final Logger LOGGER = LoggerFactory.getLogger(
            "better-shulker-hud-client-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        testOverlayLayoutPolicy();
        testMaLiLibSelectorWrongNameRecovery(context);
        if (FabricLoader.getInstance().isModLoaded("modmenu")) {
            ModMenuEntrypointAssertions.verify(context);
        }
        boolean quickShulkerProfile = FabricLoader.getInstance().isModLoaded("quickshulker");
        String profile = quickShulkerProfile ? "quickshulker"
                : FabricLoader.getInstance().isModLoaded("modmenu") ? "modmenu" : "base";
        LOGGER.info("BSH_CLIENT_GAMETEST_START profile={}", profile);
        LitematicaEasyPlaceCompatAssertions.verifyPolicy();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null);
            // Joining the first world reloads persisted MaLiLib config; apply the fixture afterward.
            context.runOnClient(client -> {
                Configs.Features.AUTO_RESTOCK.setBooleanValue(true);
                Configs.Features.SINGLE_ITEM_AUTO_RESTOCK.setBooleanValue(true);
                Configs.Features.OFFHAND_AUTO_RESTOCK.setBooleanValue(true);
                Configs.Features.LITEMATICA_RESTOCK.setBooleanValue(true);
                Configs.Features.HUD_ENABLED.setBooleanValue(true);
                Configs.General.DIAGNOSTIC_LOGGING.setBooleanValue(true);
            });
            testRecipeSupplyPlan();
            testMovedMainHandTotemDoesNotRestock(context, singleplayer);
            if (quickShulkerProfile) {
                testWaterBucketReplacement(context, singleplayer);
                testPotionContainerReplacement(context, singleplayer);
                testManualPotionMoveDoesNotRestock(context, singleplayer);
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
            testHudPanelLayoutTransaction(context, singleplayer);
            testHudToggleButtonDrag(context);
            testHudFooterAndCategoryGeometry(context, singleplayer);
            testRecipeBookCoexistsWithHud(context, singleplayer);
            if (quickShulkerProfile) {
                testRecipeSupplyFromBox(context, singleplayer);
                testMaxRecipeSupplyFromBox(context, singleplayer);
                testTableRecipeSupplyFromBox(context, singleplayer);
                testMaxTableRecipeSupplyFromBox(context, singleplayer);
            }
        }
        LOGGER.info("BSH_CLIENT_GAMETEST_SUCCESS profile={}", profile);
    }

    private static void testMaLiLibSelectorWrongNameRecovery(ClientGameTestContext context) {
        var previousScreen = context.computeOnClient(client -> client.screen);
        boolean switcherWasEnabled = context.computeOnClient(client ->
                fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue());
        var originalInfo = context.computeOnClient(client -> {
            var entries = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.getAllModsWithConfigScreens().stream()
                    .filter(info -> bettershulkerhud.Reference.MOD_ID.equals(info.modId())).toList();
            assertEquals(1, entries.size(), "the wrong-name fixture requires our unique original registry entry");
            return entries.getFirst();
        });
        try {
            context.runOnClient(client -> {
                fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(true);
                var registry = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN;
                var wrongNameInfo = new fi.dy.masa.malilib.util.data.ModInfo(
                        bettershulkerhud.Reference.MOD_ID, "-", bettershulkerhud.gui.GuiConfigs::new);
                registry.registerConfigScreenFactory(wrongNameInfo);
                var entries = registry.getAllModsWithConfigScreens().stream()
                        .filter(info -> bettershulkerhud.Reference.MOD_ID.equals(info.modId())).toList();
                assertEquals(1, entries.size(), "injecting the wrong name must replace only our own registry entry");
                assertTrue(entries.getFirst() == wrongNameInfo && "-".equals(entries.getFirst().modName()),
                        "the public registry must contain the wrong-name fixture before config initialization");
                client.setScreen(new bettershulkerhud.gui.GuiConfigs());
            });
            context.waitForScreen(bettershulkerhud.gui.GuiConfigs.class);
            context.getInput().setCursorPos(0, 0);
            clearToastsForScreenshot(context);
            context.runOnClient(client -> {
                assertMaLiLibRegistration();
                assertSelectedSwitcherText((bettershulkerhud.gui.GuiConfigs) client.screen);
            });
            context.takeScreenshot("bsh-26.1.1-malilib-wrong-name-recovered");
            LOGGER.info("BSH_MALILIB_WRONG_NAME_REGISTRY_AND_RENDERED_SELECTOR_ASSERTIONS_COMPLETE");
        } finally {
            context.runOnClient(client -> {
                client.setScreen(previousScreen);
                fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(originalInfo);
                fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER
                        .setBooleanValue(switcherWasEnabled);
            });
        }
    }

    /** Kept in a separate class so non-ModMenu profiles never resolve its optional API. */
    private static final class ModMenuEntrypointAssertions {
        private static void verify(ClientGameTestContext context) {
            context.runOnClient(client -> {
                var entrypoints = FabricLoader.getInstance().getEntrypointContainers(
                        "modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
                        .filter(entrypoint -> entrypoint.getProvider().getMetadata().getId()
                                .equals(bettershulkerhud.Reference.MOD_ID)).toList();
                assertEquals(1, entrypoints.size(),
                        "Fabric must expose exactly one Better Shulker HUD ModMenu entrypoint");
                var parent = new net.minecraft.client.gui.screens.Screen(
                        Component.literal("ModMenu parent regression")) {};
                var factory = entrypoints.getFirst().getEntrypoint().getModConfigScreenFactory();
                assertTrue(factory != null, "the actual registered ModMenu entrypoint must supply a factory");
                var created = factory.create(parent);
                assertTrue(created instanceof bettershulkerhud.gui.GuiConfigs,
                        "the registered ModMenu factory must create the real GuiConfigs screen");
                assertTrue(((bettershulkerhud.gui.GuiConfigs) created).getParent() == parent,
                        "ModMenu config screen must retain the exact parent it was given");
            });
            LOGGER.info("BSH_MODMENU_ENTRYPOINT_FACTORY_ASSERTIONS_COMPLETE");
            verifyScreenFlow(context);
        }

        private static void verifyScreenFlow(ClientGameTestContext context) {
            var previousScreen = context.computeOnClient(client -> client.screen);
            boolean switcherWasEnabled = context.computeOnClient(client ->
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue());
            boolean configureWasHidden = context.computeOnClient(client ->
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.getValue());
            AtomicReference<com.terraformersmc.modmenu.gui.ModsScreen> modsScreen = new AtomicReference<>();
            try {
                context.runOnClient(client -> {
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(true);
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.setValue(false);
                    var screen = new com.terraformersmc.modmenu.gui.ModsScreen(previousScreen);
                    modsScreen.set(screen);
                    client.setScreen(screen);
                });
                context.waitForScreen(com.terraformersmc.modmenu.gui.ModsScreen.class);
                context.runOnClient(client -> {
                    var screen = modsScreen.get();
                    var lists = screen.children().stream()
                            .filter(com.terraformersmc.modmenu.gui.widget.ModListWidget.class::isInstance)
                            .map(com.terraformersmc.modmenu.gui.widget.ModListWidget.class::cast).toList();
                    assertEquals(1, lists.size(), "ModMenu must expose its real mod list through screen children");
                    var list = lists.getFirst();
                    var ownEntries = list.children().stream()
                            .filter(entry -> entry.getMod().getId().equals(bettershulkerhud.Reference.MOD_ID)).toList();
                    assertEquals(1, ownEntries.size(), "the actual ModMenu list must contain our mod exactly once");
                    var entry = ownEntries.getFirst();
                    list.select(entry);
                    list.ensureVisible(entry);
                    assertTrue(list.getSelected() == entry && screen.getSelectedEntry() == entry,
                            "normal ModMenu list selection must propagate our exact entry to the screen");
                    assertTrue(screen.getModHasConfigScreen(bettershulkerhud.Reference.MOD_ID),
                            "ModMenu must resolve our registered configuration factory");
                    findConfigureButton(screen);
                });
                context.getInput().setCursorPos(0, 0);
                clearToastsForScreenshot(context);
                context.takeScreenshot("bsh-26.1.1-modmenu-configure-active");
                double[] point = context.computeOnClient(client -> {
                    var button = findConfigureButton(modsScreen.get());
                    return new double[]{(button.getX() + button.getWidth() / 2.0)
                            * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                            (button.getY() + button.getHeight() / 2.0)
                            * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
                });
                context.getInput().setCursorPos(point[0], point[1]);
                context.waitTick();
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitTick();
                context.waitForScreen(bettershulkerhud.gui.GuiConfigs.class);
                context.runOnClient(client -> {
                    var config = (bettershulkerhud.gui.GuiConfigs) client.screen;
                    assertTrue(config.getParent() == modsScreen.get(),
                            "clicking the real configure button must retain the exact ModsScreen as parent");
                    assertMaLiLibRegistration();
                    assertSelectedSwitcherText(config);
                });
                context.getInput().setCursorPos(0, 0);
                clearToastsForScreenshot(context);
                context.takeScreenshot("bsh-26.1.1-malilib-selected-name");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitTick();
                context.waitFor(client -> client.screen == modsScreen.get());
                context.runOnClient(client -> {
                    var selected = modsScreen.get().getSelectedEntry();
                    assertTrue(selected != null && selected.getMod().getId().equals(bettershulkerhud.Reference.MOD_ID),
                            "Esc must preserve our selected entry on the exact original ModsScreen");
                    assertMaLiLibRegistration();
                });
                LOGGER.info("BSH_MODMENU_BUTTON_PARENT_ESCAPE_AND_MALILIB_SELECTOR_ASSERTIONS_COMPLETE");
            } finally {
                context.runOnClient(client -> {
                    client.setScreen(previousScreen);
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER
                            .setBooleanValue(switcherWasEnabled);
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.setValue(configureWasHidden);
                });
            }
        }

        private static net.minecraft.client.gui.components.AbstractWidget findConfigureButton(
                com.terraformersmc.modmenu.gui.ModsScreen screen) {
            // Pinned ModMenu 18 init(): this image-only button has EMPTY message; CONFIGURE is its tooltip.
            // Its public child type and exact bounds identify it without private fields or access wideners.
            var buttons = screen.children().stream()
                    .filter(com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget.class::isInstance)
                    .map(com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget.class::cast)
                    .filter(button -> button.getX() == screen.width - 24 && button.getY() == 48
                            && button.getWidth() == 20 && button.getHeight() == 20).toList();
            assertEquals(1, buttons.size(), "the real ModMenu configure image button must exist exactly once");
            var button = buttons.getFirst();
            assertTrue(button.visible && button.active,
                    "selecting Better Shulker HUD must make the actual configure button visible and active");
            return button;
        }

    }

    private static void assertMaLiLibRegistration() {
        var registry = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN;
        var entries = registry.getAllModsWithConfigScreens().stream()
                .filter(info -> info.modId().equals(bettershulkerhud.Reference.MOD_ID)).toList();
        assertEquals(1, entries.size(), "MaLiLib must register our mod ID exactly once after config open/close");
        var info = entries.getFirst();
        assertTrue(info.modName().equals(bettershulkerhud.Reference.MOD_NAME),
                "MaLiLib registry must use the complete Better Shulker HUD display name");
        assertTrue(info.configScreenSupplier() != null,
                "MaLiLib must retain a non-null config screen supplier");
        var selectedInfo = registry.getModInfoFromConfigScreen(bettershulkerhud.gui.GuiConfigs.class);
        assertTrue(selectedInfo != null && selectedInfo.modId().equals(bettershulkerhud.Reference.MOD_ID)
                        && selectedInfo.modName().equals(bettershulkerhud.Reference.MOD_NAME),
                "MaLiLib config-class lookup must resolve our own ID and complete display name");
    }

    private static void assertSelectedSwitcherText(bettershulkerhud.gui.GuiConfigs screen) {
        var client = net.minecraft.client.Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, -100, -100);
        screen.extractRenderState(graphics, -100, -100, 0.0f);
        graphics.extractDeferredElements(-100, -100, 0.0f);
        StringBuilder selectedText = new StringBuilder();
        // MaLiLib 0.28.11 buildConfigSwitcher(): x=scaledWidth-155, y=6, width=130, height=18.
        // Read only actual submitted text inside that closed-selector rectangle, never the title at x=10.
        state.forEachText(submission -> {
            var bounds = submission.bounds();
            if (bounds == null || bounds.left() < screen.width - 155 || bounds.right() > screen.width - 25
                    || bounds.top() < 6 || bounds.bottom() > 24) return;
            submission.text.accept((index, style, codepoint) -> {
                selectedText.appendCodePoint(codepoint);
                return true;
            });
            selectedText.append(' ');
        });
        assertTrue(selectedText.toString().contains(bettershulkerhud.Reference.MOD_NAME),
                "the actual top-right selected MaLiLib dropdown must render our complete name, not '-' or an ID: "
                        + selectedText);
    }

    private static void clearToastsForScreenshot(ClientGameTestContext context) {
        context.runOnClient(client -> client.getToastManager().clear());
        context.waitTicks(3);
    }

    private static void testOverlayLayoutPolicy() {
        assertTrue(HudOverlayLayout.choose(420, 150, 176, 110, false, true, false).side()
                        == HudOverlayLayout.Side.LEFT,
                "JEI should keep the wider right side and place the HUD on the left");
        assertTrue(HudOverlayLayout.choose(320, 90, 176, 110, false, true, false).side()
                        == HudOverlayLayout.Side.HIDDEN,
                "the HUD must fold when it cannot fit beside JEI");
        assertTrue(HudOverlayLayout.choose(560, 200, 176, 110, true, true, false).side()
                        == HudOverlayLayout.Side.RIGHT,
                "the recipe book requires the HUD on the right when room remains");
    }

    private static void testRecipeSupplyPlan() {
        BundlePanelRenderer.FlatItem boxLogs = new BundlePanelRenderer.FlatItem(
                new ItemStack(Items.OAK_LOG, 2), List.of(
                new BundlePanelRenderer.ItemSource(1, 0, new ItemStack(Items.OAK_LOG, 2))));
        var oneLog = RecipeSupplyPlanner.plan(
                List.of(Ingredient.of(Items.OAK_LOG)), List.of(), List.of(boxLogs));
        assertTrue(oneLog != null && oneLog.size() == 1,
                "one missing log should be supplied from the shulker");
        var alreadyCarried = RecipeSupplyPlanner.plan(
                List.of(Ingredient.of(Items.OAK_LOG)),
                List.of(new ItemStack(Items.OAK_LOG)), List.of(boxLogs));
        assertTrue(alreadyCarried != null && alreadyCarried.isEmpty(),
                "carried ingredients must not trigger an unnecessary shulker extraction");
        var tooFew = RecipeSupplyPlanner.plan(
                List.of(Ingredient.of(Items.OAK_LOG), Ingredient.of(Items.OAK_LOG),
                        Ingredient.of(Items.OAK_LOG)), List.of(), List.of(boxLogs));
        assertTrue(tooFew == null,
                "a recipe requiring more items than the box contains must stay unavailable");
        BundlePanelRenderer.FlatItem twentyLogs = new BundlePanelRenderer.FlatItem(
                new ItemStack(Items.OAK_LOG, 20), List.of(
                new BundlePanelRenderer.ItemSource(1, 0, new ItemStack(Items.OAK_LOG, 20))));
        var maxLogs = RecipeSupplyPlanner.planMax(
                List.of(Ingredient.of(Items.OAK_LOG)), List.of(), List.of(twentyLogs));
        assertTrue(maxLogs != null && maxLogs.crafts() == 20
                        && maxLogs.missingCount() == 20 && maxLogs.supplies().size() == 1,
                "shift placement should stage one source stack, not twenty single-item requests");
        var maxNine = RecipeSupplyPlanner.planMax(
                java.util.Collections.nCopies(9, Ingredient.of(Items.DIAMOND)),
                List.of(), List.of(new BundlePanelRenderer.FlatItem(
                        new ItemStack(Items.DIAMOND, 20), List.of(
                        new BundlePanelRenderer.ItemSource(1, 0,
                                new ItemStack(Items.DIAMOND, 20))))));
        assertTrue(maxNine != null && maxNine.crafts() == 2
                        && maxNine.missingCount() == 18
                        && maxNine.supplies().size() == 1,
                "3x3 shift placement should stage only complete crafts and keep the remainder");
        var alternatePlanks = RecipeSupplyPlanner.planMax(
                java.util.Collections.nCopies(9,
                        Ingredient.of(Items.OAK_PLANKS, Items.SPRUCE_PLANKS)),
                List.of(new ItemStack(Items.OAK_PLANKS, 20)),
                List.of(new BundlePanelRenderer.FlatItem(
                        new ItemStack(Items.SPRUCE_PLANKS, 20), List.of(
                        new BundlePanelRenderer.ItemSource(1, 0,
                                new ItemStack(Items.SPRUCE_PLANKS, 20))))));
        assertTrue(alternatePlanks != null && alternatePlanks.crafts() == 4
                        && alternatePlanks.missingCount() == 16,
                "alternative ingredients should prefer carried stock and finish four crafts");
    }

    private static void testMaxRecipeSupplyFromBox(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.OAK_LOG, 20));
        });
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            for (var slot : player.inventoryMenu.getInputGridSlots()) {
                slot.set(ItemStack.EMPTY);
            }
            player.inventoryMenu.broadcastFullState();
        });
        AtomicReference<RecipeDisplayId> plankDisplay = new AtomicReference<>();
        singleplayer.getServer().runOnServer(server -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(
                    Registries.RECIPE, Identifier.withDefaultNamespace("oak_planks"));
            server.getRecipeManager().listDisplaysForRecipe(
                    key, entry -> plankDisplay.set(entry.id()));
            server.getPlayerList().getPlayers().getFirst()
                    .awardRecipesByKey(List.of(key));
        });
        assertTrue(plankDisplay.get() != null, "vanilla oak-planks display must exist");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> BundlePanelRenderer.getAllShulkers().stream()
                .anyMatch(box -> box.contents().stream()
                        .anyMatch(stack -> stack.is(Items.OAK_LOG) && stack.getCount() == 20)));
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            var collection = client.player.getRecipeBook().getCollections().stream()
                    .filter(group -> group.getRecipes().stream()
                            .anyMatch(entry -> entry.id().equals(plankDisplay.get())))
                    .findFirst().orElseThrow();
            assertTrue(((RecipeBookComponentAccessor) screen.recipeBookComponent)
                            .betterShulkerHud$tryPlaceRecipe(
                                    collection, plankDisplay.get(), true),
                    "shift recipe placement should request bulk shulker supply");
        });
        context.waitFor(client -> {
            assertTrue(client.screen instanceof InventoryScreen,
                    "the inventory must remain visible during bulk shulker supply");
            int grid = client.player.inventoryMenu.getInputGridSlots().stream()
                    .mapToInt(slot -> slot.getItem().is(Items.OAK_LOG)
                            ? slot.getItem().getCount() : 0).sum();
            return grid == 20;
        });
        context.runOnClient(client -> {
            assertEquals(0, countShulkerItem(client.player.getInventory(), Items.OAK_LOG),
                    "all twenty logs must leave the shulker");
            int grid = client.player.inventoryMenu.getInputGridSlots().stream()
                    .mapToInt(slot -> slot.getItem().is(Items.OAK_LOG)
                            ? slot.getItem().getCount() : 0).sum();
            int loose = countItem(client.player.getInventory(), Items.OAK_LOG);
            assertEquals(20, grid + loose,
                    "bulk placement must conserve all logs across grid and inventory");
        });
        context.setScreen(() -> null);
    }

    private static void testRecipeSupplyFromBox(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.OAK_LOG, 2));
        });
        AtomicReference<RecipeDisplayId> plankDisplay = new AtomicReference<>();
        singleplayer.getServer().runOnServer(server -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(
                    Registries.RECIPE, Identifier.withDefaultNamespace("oak_planks"));
            server.getRecipeManager().listDisplaysForRecipe(
                    key, entry -> plankDisplay.set(entry.id()));
            server.getPlayerList().getPlayers().getFirst()
                    .awardRecipesByKey(List.of(key));
        });
        assertTrue(plankDisplay.get() != null, "vanilla oak-planks display must exist");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> BundlePanelRenderer.getAllShulkers().stream()
                .anyMatch(box -> box.contents().stream().anyMatch(stack -> stack.is(Items.OAK_LOG))));
        context.waitFor(client -> client.player.getRecipeBook().getCollections().stream()
                .anyMatch(collection -> collection.getRecipes().stream()
                        .anyMatch(entry -> entry.id().equals(plankDisplay.get()))));
        context.runOnClient(client -> {
            // Recipe supply must hide its temporary menu even if the general hide option is off.
            Configs.Features.HIDE_QUICK_SHULKER_SCREEN.setBooleanValue(false);
            InventoryScreen screen = (InventoryScreen) client.screen;
            var collection = client.player.getRecipeBook().getCollections().stream()
                    .filter(group -> group.getRecipes().stream()
                            .anyMatch(entry -> entry.id().equals(plankDisplay.get())))
                    .findFirst().orElseThrow();
            assertTrue(((RecipeBookComponentAccessor) screen.recipeBookComponent)
                            .betterShulkerHud$tryPlaceRecipe(
                                    collection, plankDisplay.get(), false),
                    "recipe-book placement should queue a shulker ingredient");
        });
        context.waitFor(client -> {
            assertTrue(client.screen instanceof InventoryScreen,
                    "the 2x2 inventory must remain visible during shulker recipe supply");
            return client.player.inventoryMenu.getResultSlot()
                    .getItem().is(Items.OAK_PLANKS);
        });
        context.runOnClient(client -> {
            Configs.Features.HIDE_QUICK_SHULKER_SCREEN.setBooleanValue(true);
            assertEquals(1, countShulkerItem(client.player.getInventory(), Items.OAK_LOG),
                    "one log must be removed from the carried shulker");
            assertTrue(client.player.inventoryMenu.getInputGridSlots().stream()
                            .anyMatch(slot -> slot.getItem().is(Items.OAK_LOG)),
                    "vanilla recipe placement must fill a 2x2 craft slot");
        });
        context.setScreen(() -> null);
    }

    private static void testTableRecipeSupplyFromBox(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 9));
        });
        AtomicReference<RecipeDisplayId> blockDisplay = new AtomicReference<>();
        AtomicReference<BlockPos> tablePos = new AtomicReference<>();
        singleplayer.getServer().runOnServer(server -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(
                    Registries.RECIPE, Identifier.withDefaultNamespace("diamond_block"));
            server.getRecipeManager().listDisplaysForRecipe(
                    key, entry -> blockDisplay.set(entry.id()));
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.awardRecipesByKey(List.of(key));
            BlockPos pos = player.blockPosition().above().relative(Direction.NORTH);
            player.level().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
            tablePos.set(pos);
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new CraftingMenu(
                            containerId, inventory,
                            ContainerLevelAccess.create(player.level(), pos)),
                    Component.translatable("container.crafting")));
        });
        assertTrue(blockDisplay.get() != null, "vanilla diamond-block display must exist");
        context.waitForScreen(CraftingScreen.class);
        context.runOnClient(client -> {
            BlockPos pos = tablePos.get();
            client.hitResult = new BlockHitResult(
                    Vec3.atCenterOf(pos), Direction.UP, pos, false);
        });
        context.waitFor(client -> BundlePanelRenderer.getAllShulkers().stream()
                .anyMatch(box -> box.contents().stream()
                        .anyMatch(stack -> stack.is(Items.DIAMOND))));
        context.waitFor(client -> client.player.getRecipeBook().getCollections().stream()
                .anyMatch(collection -> collection.getRecipes().stream()
                        .anyMatch(entry -> entry.id().equals(blockDisplay.get()))));
        context.runOnClient(client -> {
            CraftingScreen screen = (CraftingScreen) client.screen;
            var collection = client.player.getRecipeBook().getCollections().stream()
                    .filter(group -> group.getRecipes().stream()
                            .anyMatch(entry -> entry.id().equals(blockDisplay.get())))
                    .findFirst().orElseThrow();
            assertTrue(((RecipeBookComponentAccessor) screen.recipeBookComponent)
                            .betterShulkerHud$tryPlaceRecipe(
                                    collection, blockDisplay.get(), false),
                    "table recipe placement should queue shulker ingredients");
        });
        context.waitFor(client -> {
            assertTrue(client.screen instanceof CraftingScreen,
                    "the crafting table must remain visible throughout shulker recipe supply");
            return client.player.containerMenu instanceof CraftingMenu menu
                    && menu.getResultSlot().getItem().is(Items.DIAMOND_BLOCK);
        });
        context.runOnClient(client -> {
            assertEquals(0, countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                    "all nine diamonds must be removed from the carried shulker");
            assertEquals(9, ((CraftingMenu) client.player.containerMenu).getInputGridSlots().stream()
                            .mapToInt(slot -> slot.getItem().is(Items.DIAMOND)
                                    ? slot.getItem().getCount() : 0).sum(),
                    "vanilla recipe placement must fill the 3x3 craft grid");
        });
        context.setScreen(() -> null);
        singleplayer.getServer().runOnServer(server -> server.getPlayerList()
                .getPlayers().getFirst().closeContainer());
        context.waitFor(client -> client.player.containerMenu == client.player.inventoryMenu);
        context.waitTicks(2);
    }

    private static void testMaxTableRecipeSupplyFromBox(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 20));
        });
        AtomicReference<RecipeDisplayId> blockDisplay = new AtomicReference<>();
        AtomicReference<BlockPos> tablePos = new AtomicReference<>();
        singleplayer.getServer().runOnServer(server -> {
            ResourceKey<Recipe<?>> key = ResourceKey.create(
                    Registries.RECIPE, Identifier.withDefaultNamespace("diamond_block"));
            server.getRecipeManager().listDisplaysForRecipe(
                    key, entry -> blockDisplay.set(entry.id()));
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.awardRecipesByKey(List.of(key));
            BlockPos pos = player.blockPosition().above().relative(Direction.NORTH);
            player.level().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
            tablePos.set(pos);
            player.openMenu(new SimpleMenuProvider(
                    (containerId, inventory, ignored) -> new CraftingMenu(
                            containerId, inventory,
                            ContainerLevelAccess.create(player.level(), pos)),
                    Component.translatable("container.crafting")));
        });
        assertTrue(blockDisplay.get() != null, "vanilla diamond-block display must exist");
        context.waitForScreen(CraftingScreen.class);
        context.runOnClient(client -> {
            BlockPos pos = tablePos.get();
            client.hitResult = new BlockHitResult(
                    Vec3.atCenterOf(pos), Direction.UP, pos, false);
        });
        context.waitFor(client -> BundlePanelRenderer.getAllShulkers().stream()
                .anyMatch(box -> box.contents().stream()
                        .anyMatch(stack -> stack.is(Items.DIAMOND)
                                && stack.getCount() == 20)));
        context.runOnClient(client -> {
            CraftingScreen screen = (CraftingScreen) client.screen;
            var collection = client.player.getRecipeBook().getCollections().stream()
                    .filter(group -> group.getRecipes().stream()
                            .anyMatch(entry -> entry.id().equals(blockDisplay.get())))
                    .findFirst().orElseThrow();
            assertTrue(((RecipeBookComponentAccessor) screen.recipeBookComponent)
                            .betterShulkerHud$tryPlaceRecipe(
                                    collection, blockDisplay.get(), true),
                    "shift table recipe placement should request bulk shulker supply");
        });
        context.waitFor(client -> {
            assertTrue(client.screen instanceof CraftingScreen,
                    "the crafting table must remain visible during bulk shulker supply");
            return client.player.containerMenu instanceof CraftingMenu menu
                    && menu.getInputGridSlots().stream()
                    .filter(slot -> slot.getItem().is(Items.DIAMOND)
                            && slot.getItem().getCount() == 2).count() == 9;
        });
        context.runOnClient(client -> {
            int boxed = countShulkerItem(client.player.getInventory(), Items.DIAMOND);
            int loose = countItem(client.player.getInventory(), Items.DIAMOND);
            int grid = ((CraftingMenu) client.player.containerMenu).getInputGridSlots().stream()
                    .mapToInt(slot -> slot.getItem().is(Items.DIAMOND)
                            ? slot.getItem().getCount() : 0).sum();
            assertEquals(18, grid,
                    "shift table placement should fill two diamonds per grid slot");
            assertEquals(20, boxed + loose + grid,
                    "bulk table placement must conserve all twenty diamonds");
        });
        context.setScreen(() -> null);
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


    private static void testHudPanelLayoutTransaction(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, shulkerWith(Items.DIAMOND, 64));
            inventory.setItem(2, new ItemStack(Items.EMERALD, 13));
        });
        context.getInput().resizeWindow(2560, 1440);
        int[] originalPosition = new int[2];
        AtomicReference<List<ItemStack>> before = new AtomicReference<>();
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            originalPosition[0] = Configs.General.HUD_PANEL_X.getIntegerValue();
            originalPosition[1] = Configs.General.HUD_PANEL_Y.getIntegerValue();
            Configs.General.HUD_PANEL_X.setIntegerValue(-1);
            Configs.General.HUD_PANEL_Y.setIntegerValue(-1);
            Configs.General.HUD_TOGGLE_POSITION_EDIT.setBooleanValue(false);
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            client.setScreen(new InventoryScreen(client.player));
        });
        try {
            context.waitForScreen(InventoryScreen.class);
            context.waitFor(client -> BundlePanelRenderer.getAllShulkers().stream()
                    .anyMatch(box -> box.contents().stream()
                            .anyMatch(stack -> stack.is(Items.DIAMOND) && stack.getCount() == 64)));
            context.runOnClient(client -> {
                assertTrue(BundlePanelRenderer.selectStorageView(StorageView.SHULKERS),
                        "the layout regression needs the prepared shulker view");
                before.set(client.player.inventoryMenu.slots.stream()
                        .map(slot -> slot.getItem().copy()).toList());
                assertTrue(BundlePanelRenderer.startPanelLayoutEditor(),
                        "layout editor should open in a settled inventory with an empty cursor");
                assertTrue(bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "the editor must own input after entry");
            });
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            context.takeScreenshot("hud-layout-editor");
            context.runOnClient(client -> {
                bettershulkerhud.gui.HudPanelLayoutEditor.resetDraft();
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftX(),
                        "Reset must restore AUTO in the horizontal draft");
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftY(),
                        "Reset must restore AUTO in the vertical draft");
                assertEquals(-1, Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "Reset must not write saved horizontal coordinates");
                assertEquals(-1, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "Reset must not write saved vertical coordinates");
            });
            dragPanelLayoutPreview(context);

            double[] inventoryCursor = context.computeOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                var slot = client.player.inventoryMenu.slots.stream()
                        .filter(candidate -> candidate.container == client.player.getInventory()
                                && candidate.getContainerSlot() == 2)
                        .findFirst().orElseThrow();
                double sx = client.getWindow().getScreenWidth()
                        / (double) client.getWindow().getGuiScaledWidth();
                double sy = client.getWindow().getScreenHeight()
                        / (double) client.getWindow().getGuiScaledHeight();
                return new double[]{(screen.leftPos + slot.x + 8) * sx,
                        (screen.topPos + slot.y + 8) * sy};
            });
            context.getInput().setCursorPos(inventoryCursor[0], inventoryCursor[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.getInput().pressKey(GLFW.GLFW_KEY_F);
            context.getInput().pressKey(GLFW.GLFW_KEY_1);
            context.getInput().pressKey(GLFW.GLFW_KEY_Q);
            context.waitTicks(3);
            assertPanelLayoutInventoryUnchanged(context, before.get());
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitTick();
            context.runOnClient(client -> {
                assertEquals(-1, Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "Escape must discard horizontal layout changes");
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "Escape must release editor input ownership");
                assertEquals(-1, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "Escape must discard vertical layout changes");
                assertTrue(BundlePanelRenderer.startPanelLayoutEditor(),
                        "layout editor must be usable again after cancellation");
            });
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
            context.waitTick();
            int[] committedPosition = context.computeOnClient(client -> {
                int x = Configs.General.HUD_PANEL_X.getIntegerValue();
                int y = Configs.General.HUD_PANEL_Y.getIntegerValue();
                assertTrue(x >= 0 && x <= 10000 && y >= 0 && y <= 10000,
                        "Enter must commit normalized manual coordinates");
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "Enter must finish the editing transaction");
                return new int[]{x, y};
            });
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                assertEquals(committedPosition[0], Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "saved horizontal coordinates must survive screen closure");
                assertEquals(committedPosition[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "saved vertical coordinates must survive screen closure");
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "closing the screen must clear editor input ownership");
                assertTrue(BundlePanelRenderer.startPanelLayoutEditor(),
                        "the saved layout must be editable again");
            });
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            context.setScreen(() -> null);
            context.waitTick();
            context.runOnClient(client -> {
                assertEquals(committedPosition[0], Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "closing must restore the last saved horizontal coordinate");
                assertEquals(committedPosition[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "closing must restore the last saved vertical coordinate");
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "screen changes must cancel an active layout draft");
            });
            assertPanelLayoutInventoryUnchanged(context, before.get());
            context.runOnClient(client -> assertTrue(BundlePanelRenderer.startPanelLayoutEditor(),
                    "button routing regression must open the editor"));
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            clickPanelLayoutControl(context, 2);
            context.runOnClient(client -> {
                assertTrue(bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "the actual Reset button must keep editing active");
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftX(),
                        "the actual Reset button must restore AUTO X");
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftY(),
                        "the actual Reset button must restore AUTO Y");
            });
            dragPanelLayoutPreview(context);
            clickPanelLayoutControl(context, 1);
            context.runOnClient(client -> {
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "the actual Cancel button must release editor input");
                assertEquals(committedPosition[0], Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "the actual Cancel button must preserve saved X");
                assertEquals(committedPosition[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "the actual Cancel button must preserve saved Y");
                assertTrue(BundlePanelRenderer.startPanelLayoutEditor(),
                        "editor must reopen after clicking Cancel");
            });
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            int[] buttonDraft = context.computeOnClient(client -> new int[]{
                    bettershulkerhud.gui.HudPanelLayoutEditor.draftX(),
                    bettershulkerhud.gui.HudPanelLayoutEditor.draftY()});
            clickPanelLayoutControl(context, 0);
            context.runOnClient(client -> {
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "the actual Done button must release editor input");
                assertEquals(buttonDraft[0], Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "the actual Done button must commit X");
                assertEquals(buttonDraft[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "the actual Done button must commit Y");
            });
            assertPanelLayoutInventoryUnchanged(context, before.get());
            context.setScreen(() -> new bettershulkerhud.gui.GuiConfigs());
            context.waitTicks(3);
            context.takeScreenshot("hud-settings-wide");
            context.getInput().resizeWindow(640, 480);
            context.waitTicks(3);
            context.takeScreenshot("hud-settings-narrow");
            context.getInput().resizeWindow(2560, 1440);
            context.waitTicks(3);
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                Configs.General.HUD_PANEL_X.setIntegerValue(originalPosition[0]);
                Configs.General.HUD_PANEL_Y.setIntegerValue(originalPosition[1]);
            });
        }
    }

    private static void clickPanelLayoutControl(ClientGameTestContext context, int index) {
        double[] point = context.computeOnClient(client -> {
            int guiWidth = client.getWindow().getGuiScaledWidth();
            int width = Math.min(90, Math.max(36, (guiWidth - 16) / 3));
            double x = (guiWidth - (width * 3 + 8)) / 2 + index * (width + 4) + width / 2.0;
            return new double[]{x * client.getWindow().getScreenWidth() / guiWidth,
                    27.0 * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTick();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    private static void dragPanelLayoutPreview(ClientGameTestContext context) {
        double[] cursor = context.computeOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            double sx = client.getWindow().getScreenWidth()
                    / (double) client.getWindow().getGuiScaledWidth();
            double sy = client.getWindow().getScreenHeight()
                    / (double) client.getWindow().getGuiScaledHeight();
            int x = BundlePanelRenderer.panelX(screen.leftPos);
            int y = BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight);
            return new double[]{(x + 12) * sx, (y + 7) * sy, sx, sy, x, y,
                    Configs.General.HUD_PANEL_X.getIntegerValue(),
                    Configs.General.HUD_PANEL_Y.getIntegerValue()};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        // Flush repositioning before dragging: MouseHandler batches cursor deltas per frame.
        context.waitTick();
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
        context.getInput().moveCursor(70 * cursor[2], 25 * cursor[3]);
        context.waitTick();
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            assertTrue(BundlePanelRenderer.panelX(screen.leftPos) != (int) cursor[4]
                            || BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight) != (int) cursor[5],
                    "real pointer drag must move the layout preview");
            assertEquals((int) cursor[6], Configs.General.HUD_PANEL_X.getIntegerValue(),
                    "releasing the mouse must not commit horizontal layout changes");
            assertEquals((int) cursor[7], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                    "releasing the mouse must not commit vertical layout changes");
        });
    }

    private static void assertPanelLayoutInventoryUnchanged(
            ClientGameTestContext context, List<ItemStack> before) {
        context.runOnClient(client -> {
            assertTrue(client.player.containerMenu.getCarried().isEmpty(),
                    "layout editing must never pick up an inventory stack");
            assertEquals(before.size(), client.player.inventoryMenu.slots.size(),
                    "layout editing must preserve the inventory menu");
            for (int i = 0; i < before.size(); i++) {
                assertTrue(ItemStack.matches(before.get(i),
                                client.player.inventoryMenu.slots.get(i).getItem()),
                        "layout editing must preserve slot " + i);
            }
        });
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
        int[] savedPanelPosition = new int[2];
        int[] closedPanelBounds = new int[4];
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            savedPanelPosition[0] = Configs.General.HUD_PANEL_X.getIntegerValue();
            savedPanelPosition[1] = Configs.General.HUD_PANEL_Y.getIntegerValue();
            Configs.General.HUD_PANEL_X.setIntegerValue(2500);
            Configs.General.HUD_PANEL_Y.setIntegerValue(4500);
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
            var bounds = BundlePanelRenderer.panelBounds(
                    screen.leftPos, screen.topPos, screen.imageHeight);
            closedPanelBounds[0] = bounds.x();
            closedPanelBounds[1] = bounds.y();
            closedPanelBounds[2] = bounds.width();
            closedPanelBounds[3] = bounds.height();
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
            var bounds = BundlePanelRenderer.panelBounds(
                    screen.leftPos, screen.topPos, screen.imageHeight);
            int panelX = bounds.x();
            int panelRight = bounds.x() + bounds.width();
            int inventoryRight = screen.leftPos + screen.imageWidth;
            layout[2] = screen.leftPos;
            layout[3] = BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth);
            assertTrue(client.getWindow().getGuiScaledWidth() >= 500,
                    "coexistence test requires a wide GUI viewport");
            assertTrue(BundlePanelRenderer.hasRenderablePanel(),
                    "the complete HUD must remain renderable while the recipe book is open");
            assertEquals(2500, Configs.General.HUD_PANEL_X.getIntegerValue(),
                    "recipe-book placement must not overwrite saved manual X");
            assertEquals(4500, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                    "recipe-book placement must not overwrite saved manual Y");
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
        context.waitFor(client -> findRecipeBookButton((InventoryScreen) client.screen).visible);
        clickRecipeBookButton(context);
        context.waitFor(client -> !BundlePanelRenderer.isRecipeBookOpen());
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            var restored = BundlePanelRenderer.panelBounds(
                    screen.leftPos, screen.topPos, screen.imageHeight);
            assertEquals(closedPanelBounds[0], restored.x(), "closing the book must restore manual X");
            assertEquals(closedPanelBounds[1], restored.y(), "closing the book must restore manual Y");
            assertEquals(closedPanelBounds[2], restored.width(), "closing the book must restore HUD width");
            assertEquals(closedPanelBounds[3], restored.height(), "closing the book must restore HUD height");
        });
        clickRecipeBookButton(context);
        context.waitFor(client -> BundlePanelRenderer.isRecipeBookOpen());
        for (int guiWidth : new int[]{350, 500, 534, 536, 540}) {
            context.getInput().resizeWindow(guiWidth * 2, 1440);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.resizeGui();
            });
            context.waitTicks(3);
            context.runOnClient(client -> assertRecipeBookPanelFit((InventoryScreen) client.screen));
        }
        context.getInput().resizeWindow(2560, 1440);
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
        });
        context.waitTicks(3);
        clickRecipeBookButton(context);
        context.waitFor(client -> !BundlePanelRenderer.isRecipeBookOpen());
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            var restored = BundlePanelRenderer.panelBounds(
                    screen.leftPos, screen.topPos, screen.imageHeight);
            assertEquals(closedPanelBounds[0], restored.x(), "resizing must not lose saved manual X");
            assertEquals(closedPanelBounds[1], restored.y(), "resizing must not lose saved manual Y");
            assertEquals(2500, Configs.General.HUD_PANEL_X.getIntegerValue(), "manual X must remain untouched");
            assertEquals(4500, Configs.General.HUD_PANEL_Y.getIntegerValue(), "manual Y must remain untouched");
            Configs.General.HUD_PANEL_X.setIntegerValue(savedPanelPosition[0]);
            Configs.General.HUD_PANEL_Y.setIntegerValue(savedPanelPosition[1]);
            BundlePanelRenderer.searchFocused = false;
        });
        context.setScreen(() -> null);
    }

    private static void testHudFooterAndCategoryGeometry(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, new ItemStack(Items.SHULKER_BOX));
        });
        int[] saved = new int[4];
        context.runOnClient(client -> {
            saved[0] = Configs.General.HUD_PANEL_X.getIntegerValue();
            saved[1] = Configs.General.HUD_PANEL_Y.getIntegerValue();
            saved[2] = Configs.General.HUD_MAX_COLUMNS.getIntegerValue();
            saved[3] = Configs.General.HUD_MAX_ROWS.getIntegerValue();
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            Configs.General.HUD_MAX_ROWS.setIntegerValue(1);
            BundlePanelRenderer.searchQuery = "";
            BundlePanelRenderer.searchFocused = false;
            client.setScreen(new InventoryScreen(client.player));
        });
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(client -> !BundlePanelRenderer.getAllShulkers().isEmpty());
        try {
            for (int physicalWidth : new int[]{1000, 1072, 2560}) {
                context.getInput().resizeWindow(physicalWidth, 1440);
                context.runOnClient(client -> {
                    client.options.guiScale().set(2);
                    client.resizeGui();
                });
                context.waitTicks(3);
                for (int position : new int[]{-1, 5000}) {
                    for (int columns : new int[]{1, 2, 6}) {
                        context.runOnClient(client -> {
                            Configs.General.HUD_PANEL_X.setIntegerValue(position);
                            Configs.General.HUD_PANEL_Y.setIntegerValue(position);
                            Configs.General.HUD_MAX_COLUMNS.setIntegerValue(columns);
                            BundlePanelRenderer.currentCategory = BundleCategory.values()[
                                    BundleCategory.values().length - 1];
                            InventoryScreen screen = (InventoryScreen) client.screen;
                            assertTrue(BundlePanelRenderer.hasRenderablePanel(),
                                    "the footer regression needs a visible HUD");
                            assertHudFooterGeometry(screen);
                            int height = BundlePanelRenderer.panelHeight(screen.topPos, screen.imageHeight);
                            int categoryHeight = Math.max(1, Math.min(18,
                                    Math.max(0, height - 4) / BundleCategory.values().length));
                            for (int i = 0; i < BundleCategory.values().length; i++) {
                                double x = BundlePanelRenderer.panelX(screen.leftPos) + 8;
                                double y = BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight)
                                        + 2 + i * categoryHeight + categoryHeight / 2.0;
                                BundleCategory hovered = BundlePanelRenderer.getCategoryAt(
                                        x, y, screen.leftPos, screen.topPos, screen.imageHeight);
                                assertTrue(hovered == BundleCategory.values()[i],
                                        "every compressed category must retain its own hover target");
                                assertTrue(!hovered.getDisplayName().isBlank(),
                                        "every category hover needs a localized category name");
                            }
                        });
                    }
                }
            }
            context.runOnClient(client -> assertHudRenderedHover((InventoryScreen) client.screen));
        } finally {
            context.runOnClient(client -> {
                Configs.General.HUD_PANEL_X.setIntegerValue(saved[0]);
                Configs.General.HUD_PANEL_Y.setIntegerValue(saved[1]);
                Configs.General.HUD_MAX_COLUMNS.setIntegerValue(saved[2]);
                Configs.General.HUD_MAX_ROWS.setIntegerValue(saved[3]);
                BundlePanelRenderer.currentCategory = BundleCategory.OVERVIEW;
            });
            context.setScreen(() -> null);
        }
    }

    /** Inspect the actual render submissions, not just the string/helper chosen for a tooltip. */
    private static void assertHudRenderedHover(InventoryScreen screen) {
        BundlePanelRenderer.selectStorageView(StorageView.SHULKERS);
        BundlePanelRenderer.searchQuery = "";
        BundlePanelRenderer.searchFocused = false;
        BundlePanelRenderer.currentCategory = BundleCategory.OVERVIEW;
        assertTrue(BundlePanelRenderer.getVisibleItems().isEmpty(),
                "the empty-grid regression must not manufacture placeholder storage items");
        List<String> stateLabels = List.of("state_empty", "state_no_match", "state_loading", "state_unknown")
                .stream().map(key -> Component.translatable("message.better-shulker-hud." + key).getString())
                .toList();
        String idle = captureHudText(screen, -100, -100, true);
        for (String label : stateLabels) {
            assertTrue(!idle.contains(label), "empty state text must never be painted across idle grid cells");
        }
        int gridX = BundlePanelRenderer.gridX(screen.leftPos) + 9;
        int gridY = BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight) + 9;
        String beforeTooltip = captureHudText(screen, gridX, gridY, false);
        assertTrue(!beforeTooltip.contains(stateLabels.getFirst()),
                "empty state must not be a permanent grid text submission, even while hovered");
        assertTrue(captureHudText(screen, gridX, gridY, true).contains(stateLabels.getFirst()),
                "hovering empty cells must still show the accurate empty-state tooltip");
        BundlePanelRenderer.searchQuery = "__bsh_no_match__";
        assertTrue(captureHudText(screen, gridX, gridY, true).contains(stateLabels.get(1)),
                "a filtered empty grid must retain its distinct no-match hover state");
        BundlePanelRenderer.searchQuery = "";
        int height = BundlePanelRenderer.panelHeight(screen.topPos, screen.imageHeight);
        int categoryHeight = Math.max(1, Math.min(18,
                Math.max(0, height - 4) / BundleCategory.values().length));
        for (int i = 0; i < BundleCategory.values().length; i++) {
            int x = BundlePanelRenderer.panelX(screen.leftPos) + 8;
            int y = BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight)
                    + 2 + i * categoryHeight + categoryHeight / 2;
            String categoryName = BundleCategory.values()[i].getDisplayName();
            assertTrue(captureHudText(screen, x, y, true).contains(categoryName),
                    "every real category render must submit its localized name as a tooltip: " + categoryName);
        }
        String afterLeaving = captureHudText(screen, -100, -100, true);
        assertTrue(!afterLeaving.contains(stateLabels.getFirst()),
                "leaving the grid must not retain the previous frame's empty tooltip");
    }

    private static String captureHudText(InventoryScreen screen, int mouseX, int mouseY, boolean flushTooltip) {
        var client = net.minecraft.client.Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, mouseX, mouseY);
        BundlePanelRenderer.render(graphics, screen.leftPos, screen.topPos, screen.imageHeight, mouseX, mouseY);
        if (flushTooltip) graphics.extractDeferredElements(mouseX, mouseY, 0.0f);
        StringBuilder text = new StringBuilder();
        state.forEachText(submission -> {
            submission.text.accept((index, style, codepoint) -> {
                text.appendCodePoint(codepoint);
                return true;
            });
            text.append(' ');
        });
        return text.toString();
    }

    private static void assertHudFooterGeometry(InventoryScreen screen) {
        int x = BundlePanelRenderer.panelX(screen.leftPos);
        int width = BundlePanelRenderer.panelWidth(screen.leftPos);
        var bounds = BundlePanelRenderer.panelBounds(screen.leftPos, screen.topPos, screen.imageHeight);
        assertTrue(width >= 84, "one-column HUD must still have room for all footer controls");
        assertEquals(width + 8, bounds.width(), "complete bounds must include both protruding edges");
        assertEquals(bounds.width(), BundlePanelRenderer.exclusionWidth(screen.leftPos),
                "overlay exclusions must match complete visual bounds");
        assertEquals(bounds.x(), BundlePanelRenderer.exclusionX(screen.leftPos),
                "overlay exclusion X must match the panel hit region");
        assertEquals(x + width - 63, BundlePanelRenderer.storageTabX(screen.leftPos, 0),
                "all three storage tabs must move together against the right edge");
        assertEquals(4, x + width - (BundlePanelRenderer.storageTabX(screen.leftPos, 2) + 19),
                "selected footer outline must leave four pixels of right padding");
        assertTrue(BundlePanelRenderer.storageTabX(screen.leftPos, 0) - 1 > x + 19,
                "the first footer outline must not overlap even a selected category");
        for (int i = 0; i < StorageView.values().length; i++) {
            int tabX = BundlePanelRenderer.storageTabX(screen.leftPos, i);
            int tabY = BundlePanelRenderer.storageTabY(screen.topPos, screen.imageHeight);
            assertTrue(bounds.contains(tabX - 1, tabY - 1)
                            && bounds.contains(tabX + 18, tabY + 18),
                    "the complete hit/exclusion region must contain every selected footer outline");
            assertTrue(BundlePanelRenderer.getStorageViewAt(tabX + 9, tabY + 9,
                            screen.leftPos, screen.topPos, screen.imageHeight) == StorageView.values()[i],
                    "storage hits must follow the right-aligned drawn positions");
        }
    }

    private static void clickRecipeBookButton(ClientGameTestContext context) {
        double[] point = context.computeOnClient(client -> {
            ImageButton button = findRecipeBookButton((InventoryScreen) client.screen);
            return new double[]{(button.getX() + button.getWidth() / 2.0)
                    * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                    (button.getY() + button.getHeight() / 2.0)
                    * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()};
        });
        context.getInput().setCursorPos(point[0], point[1]);
        context.waitTick();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTick();
    }

    private static void assertRecipeBookPanelFit(InventoryScreen screen) {
        var client = net.minecraft.client.Minecraft.getInstance();
        int viewport = client.getWindow().getGuiScaledWidth();
        int available = viewport - screen.leftPos - screen.imageWidth - 4 - 6
                - ((FabricLoader.getInstance().isModLoaded("jei")
                || FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) ? 64 : 0);
        boolean shouldRender = viewport >= 379 && available >= 92;
        assertTrue(BundlePanelRenderer.isRecipeBookOpen(), "the resized recipe book must remain open");
        assertTrue(BundlePanelRenderer.hasRenderablePanel() == shouldRender,
                "recipe-book visibility must use the whole 92px minimum, including native narrow mode");
        assertEquals(2500, Configs.General.HUD_PANEL_X.getIntegerValue(), "narrow layout must not change saved X");
        assertEquals(4500, Configs.General.HUD_PANEL_Y.getIntegerValue(), "narrow layout must not change saved Y");
        var bounds = BundlePanelRenderer.panelBounds(screen.leftPos, screen.topPos, screen.imageHeight);
        if (shouldRender) {
            assertTrue(bounds.x() >= screen.leftPos + screen.imageWidth + 6,
                    "full HUD bounds must stay outside the inventory, not be clamped back over it");
            assertTrue(bounds.x() + bounds.width() <= viewport - 4,
                    "the complete HUD must fit inside the screen margin");
            assertTrue(bounds.width() <= available, "column selection must fit the complete bounds");
            int columns = BundlePanelRenderer.columnCount(screen.leftPos);
            if (columns < Configs.General.HUD_MAX_COLUMNS.getIntegerValue()) {
                assertTrue(Math.max(84, 49 + (columns + 1) * 18) + 8 > available,
                        "the fitter must keep as many whole columns as possible");
            }
            assertHudFooterGeometry(screen);
        } else {
            double x = BundlePanelRenderer.storageTabX(screen.leftPos, 0) + 9;
            double y = BundlePanelRenderer.storageTabY(screen.topPos, screen.imageHeight) + 9;
            assertTrue(BundlePanelRenderer.getStorageViewAt(x, y,
                            screen.leftPos, screen.topPos, screen.imageHeight) == null,
                    "hidden storage tabs must not intercept clicks");
            assertTrue(!BundlePanelRenderer.isInsidePanelBounds(bounds.x() + 5, bounds.y() + 5,
                            screen.leftPos, screen.topPos, screen.imageHeight),
                    "a hidden HUD must not expose an overall input/exclusion hit");
            assertTrue(BundlePanelRenderer.getCategoryAt(bounds.x() + 8, bounds.y() + 5,
                            screen.leftPos, screen.topPos, screen.imageHeight) == null,
                    "hidden category tabs must not intercept clicks");
            assertTrue(!BundlePanelRenderer.isInsideSearchBar(bounds.x() + 25, bounds.y() + 8,
                            screen.leftPos, screen.topPos, screen.imageHeight),
                    "hidden search field must not intercept clicks");
            assertTrue(!BundlePanelRenderer.isReturnButtonHovered(
                            BundlePanelRenderer.returnButtonX(screen.leftPos) + 5,
                            BundlePanelRenderer.returnButtonY(screen.topPos, screen.imageHeight) + 5,
                            screen.leftPos, screen.topPos, screen.imageHeight),
                    "hidden header action must not intercept clicks");
            assertTrue(!BundlePanelRenderer.handleScrollBarClick(bounds.x() + 25, bounds.y() + 30,
                            GLFW.GLFW_MOUSE_BUTTON_LEFT, screen.leftPos, screen.topPos, screen.imageHeight)
                            && !BundlePanelRenderer.handleScrollBarDrag(bounds.y() + 50,
                            GLFW.GLFW_MOUSE_BUTTON_LEFT, screen.leftPos, screen.topPos, screen.imageHeight)
                            && !BundlePanelRenderer.handleScrollBarRelease(GLFW.GLFW_MOUSE_BUTTON_LEFT),
                    "a hidden scrollbar must not capture click, drag or release");
            BundlePanelRenderer.searchFocused = true;
            String query = BundlePanelRenderer.searchQuery;
            assertTrue(!BundlePanelRenderer.onCharTyped('x')
                            && !BundlePanelRenderer.onSearchKeyPress(GLFW.GLFW_KEY_BACKSPACE, 0),
                    "stale hidden search focus must not capture text or keys");
            assertTrue(query.equals(BundlePanelRenderer.searchQuery),
                    "hidden search input must not mutate its query");
            BundlePanelRenderer.searchFocused = false;
            int offset = BundlePanelRenderer.getScrollOffset();
            BundlePanelRenderer.scrollBy(1);
            assertEquals(offset, BundlePanelRenderer.getScrollOffset(),
                    "hidden HUD scrolling must not mutate the offset");
        }
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

    private static void testPotionContainerReplacement(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (Item potionItem : List.of(Items.POTION, Items.SPLASH_POTION,
                Items.LINGERING_POTION)) {
            resetRememberedItems(context, singleplayer);
            ItemStack potion = new ItemStack(potionItem);
            potion.set(DataComponents.CUSTOM_NAME, Component.literal("potion-identity"));
            setInventory(singleplayer, inventory -> {
                inventory.setSelectedSlot(0);
                inventory.setItem(0, potion.copy());
                inventory.setItem(1, shulkerWithStack(potion));
            });
            context.waitTicks(5);
            setInventorySlot(singleplayer, 0,
                    potionItem == Items.POTION
                            ? new ItemStack(Items.GLASS_BOTTLE) : ItemStack.EMPTY);
            context.waitFor(client -> client.player != null
                    && (potionItem == Items.POTION
                            ? client.player.getInventory().getSelectedItem()
                                    .is(Items.GLASS_BOTTLE)
                            : client.player.getInventory().getSelectedItem().isEmpty()));
            context.waitFor(client -> client.player != null
                    && ItemStack.isSameItemSameComponents(
                            client.player.getInventory().getSelectedItem(), potion)
                    && !QuickShulkerExtractionController.hasActiveOperation(),
                    OPERATION_TIMEOUT_TICKS);
            context.runOnClient(client -> {
                Inventory inventory = client.player.getInventory();
                assertEquals(potionItem == Items.POTION ? 1 : 0,
                        countItem(inventory, Items.GLASS_BOTTLE),
                        "a consumed potion must not replenish glass bottles");
            });
        }
    }

    private static void testManualPotionMoveDoesNotRestock(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.POTION));
            inventory.setItem(1, shulkerWith(Items.POTION, 1));
        });
        context.waitTicks(5);
        moveInventorySlot(singleplayer, 0, 2);
        context.waitTicks(35);
        context.runOnClient(client -> {
            assertTrue(client.player.getInventory().getSelectedItem().isEmpty(),
                    "moving a potion must not refill its former slot");
            assertEquals(1, countShulkerItem(client.player.getInventory(), Items.POTION),
                    "manual movement must not extract another potion");
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
                .requestLitematicaRestock(new ItemStack(Items.GRASS_BLOCK), null));
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

    private static ItemStack shulkerWithStack(ItemStack content) {
        NonNullList<ItemStack> contents = NonNullList.withSize(
                ShulkerContentsHelper.SHULKER_SIZE, ItemStack.EMPTY);
        contents.set(0, content.copy());
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
