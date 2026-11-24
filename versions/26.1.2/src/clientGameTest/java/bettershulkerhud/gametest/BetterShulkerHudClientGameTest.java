package bettershulkerhud.gametest;

import bettershulkerhud.config.Configs;
import bettershulkerhud.compat.LitematicaEasyPlaceCompatAssertions;
import bettershulkerhud.compat.QuickShulkerExtractionController;
import bettershulkerhud.compat.RecipeSupplyPlanner;
import bettershulkerhud.mixin.RecipeBookComponentAccessor;
import bettershulkerhud.compat.StorageClientNetwork;
import bettershulkerhud.gui.BundlePanelRenderer;
import bettershulkerhud.gui.BundleCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import bettershulkerhud.core.HudOverlayLayout;
import bettershulkerhud.gui.StorageView;
import bettershulkerhud.server.EnderChestStorageAccess;
import bettershulkerhud.util.ShulkerContentsHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.ImageButton;
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
        boolean quickShulkerProfile = FabricLoader.getInstance().isModLoaded("quickshulker");
        boolean modMenuProfile = FabricLoader.getInstance().isModLoaded("modmenu");
        String profile = quickShulkerProfile ? "quickshulker" : modMenuProfile ? "modmenu" : "base";
        LOGGER.info("BSH_CLIENT_GAMETEST_START profile={}",
                profile);
        testConfigScreenSelector(context);
        if (modMenuProfile) ModMenuEntrypointAssertions.verify(context);
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
            // World-join config loading can overwrite pre-world fixture settings.
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
            testRecipeBookCoexistsWithHud(context, singleplayer);
            testRecipeBookPanelLayout(context, singleplayer);
            testHudRenderedHover(context, singleplayer);
            if (quickShulkerProfile) {
                testRecipeSupplyFromBox(context, singleplayer);
                testTableRecipeSupplyFromBox(context, singleplayer);
            }
        }
        LOGGER.info("BSH_CLIENT_GAMETEST_SUCCESS profile={}",
                profile);
    }

    private static void testConfigScreenSelector(ClientGameTestContext context) {
        var originalScreen = context.computeOnClient(client -> client.screen);
        boolean switcherEnabled = context.computeOnClient(client ->
                fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue());
        var originalMod = context.computeOnClient(client -> {
            var matching = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.getAllModsWithConfigScreens().stream()
                    .filter(info -> bettershulkerhud.Reference.MOD_ID.equals(info.modId())).toList();
            assertEquals(1, matching.size(), "the selector recovery fixture must preserve one original registry entry");
            return matching.getFirst();
        });
        try {
            context.runOnClient(client -> {
                fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(true);
                var registry = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN;
                var badName = new fi.dy.masa.malilib.util.data.ModInfo(
                        bettershulkerhud.Reference.MOD_ID, "-", bettershulkerhud.gui.GuiConfigs::new);
                registry.registerConfigScreenFactory(badName);
                var matching = registry.getAllModsWithConfigScreens().stream()
                        .filter(info -> bettershulkerhud.Reference.MOD_ID.equals(info.modId())).toList();
                assertEquals(1, matching.size(), "injecting a bad name must replace only our own registry entry");
                assertTrue(matching.getFirst() == badName && "-".equals(matching.getFirst().modName()),
                        "the bad '-' selector name must exist before real GuiConfigs initialization");
            });
            context.setScreen(() -> new bettershulkerhud.gui.GuiConfigs().setParent(originalScreen));
            context.waitTicks(3);
            context.runOnClient(client -> {
                assertTrue(client.screen instanceof bettershulkerhud.gui.GuiConfigs,
                        "the MaLiLib selector fixture must open the real config screen");
                assertConfigScreenSelector((bettershulkerhud.gui.GuiConfigs) client.screen);
                client.getToastManager().clear();
            });
            context.getInput().setCursorPos(0.0, 0.0);
            context.waitTicks(3);
            context.takeScreenshot("malilib-selector-selected");
            LOGGER.info("BSH_MALILIB_SELECTOR_ASSERTIONS_COMPLETE registry=unique selected-label=rendered-top-right injected-name=- repaired-name=canonical");
        } finally {
            try {
                context.setScreen(() -> originalScreen);
            } finally {
                context.runOnClient(client -> {
                    try {
                        fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN.registerConfigScreenFactory(originalMod);
                    } finally {
                        fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(switcherEnabled);
                    }
                });
            }
        }
    }

    private static void assertConfigScreenSelector(bettershulkerhud.gui.GuiConfigs screen) {
        var registry = fi.dy.masa.malilib.registry.Registry.CONFIG_SCREEN;
        var registered = registry.getModInfoFromConfigScreen(bettershulkerhud.gui.GuiConfigs.class);
        assertTrue(registered != null, "MaLiLib must resolve the actual config screen's ModInfo");
        assertTrue(bettershulkerhud.Reference.MOD_ID.equals(registered.modId()),
                "the MaLiLib selector registration must use our exact mod id");
        assertTrue(bettershulkerhud.Reference.MOD_NAME.equals(registered.modName()),
                "the MaLiLib selector registration must use the full mod name, not '-'");
        assertTrue(registered.configScreenSupplier() != null,
                "the registered MaLiLib selector entry must have a screen supplier");
        var matching = registry.getAllModsWithConfigScreens().stream()
                .filter(info -> bettershulkerhud.Reference.MOD_ID.equals(info.modId())).toList();
        assertEquals(1, matching.size(), "MaLiLib must expose exactly one selector entry for our mod id");
        assertTrue(bettershulkerhud.Reference.MOD_NAME.equals(matching.getFirst().modName()),
                "the unique MaLiLib selector entry must retain the correct name");

        var client = net.minecraft.client.Minecraft.getInstance();
        GuiRenderState state = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, state, -100, -100);
        screen.extractRenderState(graphics, -100, -100, 0.0f);
        graphics.extractDeferredElements(-100, -100, 0.0f);
        StringBuilder selectedLabel = new StringBuilder();
        state.forEachText(submission -> {
            var bounds = submission.bounds();
            // Pinned MaLiLib 0.28.11 builds the selector at (width - 155, 6), 130 x 18.
            // Restrict to that real rendered region: the title at x=10 must never satisfy this check.
            if (bounds != null && bounds.left() >= screen.width - 155 && bounds.right() <= screen.width - 25
                    && bounds.top() >= 6 && bounds.bottom() <= 24) {
                submission.text.accept((index, style, codepoint) -> {
                    selectedLabel.appendCodePoint(codepoint);
                    return true;
                });
                selectedLabel.append('\n');
            }
        });
        assertTrue(selectedLabel.toString().contains(bettershulkerhud.Reference.MOD_NAME),
                "the actual closed top-right MaLiLib selector must render the selected mod name; captured: "
                        + selectedLabel);
    }

    /** Kept in a separate class so non-ModMenu profiles never resolve its optional API. */
    private static final class ModMenuEntrypointAssertions {
        private static void verify(ClientGameTestContext context) {
            var originalScreen = context.computeOnClient(client -> client.screen);
            boolean[] options = context.computeOnClient(client -> new boolean[]{
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.getValue(),
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.getBooleanValue()});
            try {
                context.runOnClient(client -> {
                    var entrypoints = FabricLoader.getInstance().getEntrypointContainers(
                            "modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class).stream()
                            .filter(entrypoint -> entrypoint.getProvider().getMetadata().getId()
                                    .equals(bettershulkerhud.Reference.MOD_ID)).toList();
                    assertEquals(1, entrypoints.size(),
                            "Fabric must expose exactly one Better Shulker HUD ModMenu entrypoint");
                    var factory = entrypoints.getFirst().getEntrypoint().getModConfigScreenFactory();
                    assertTrue(factory != null, "the registered ModMenu entrypoint must supply a factory");
                    var created = factory.create(originalScreen);
                    assertTrue(created instanceof bettershulkerhud.gui.GuiConfigs,
                            "the registered ModMenu factory must create the real GuiConfigs screen");
                    assertTrue(((bettershulkerhud.gui.GuiConfigs) created).getParent() == originalScreen,
                            "the registered factory must retain the exact supplied parent");
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.setValue(false);
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(true);
                });
                var mods = context.computeOnClient(client -> new com.terraformersmc.modmenu.gui.ModsScreen(originalScreen));
                context.setScreen(() -> mods);
                context.waitTicks(3);
                context.runOnClient(client -> {
                    var lists = mods.children().stream()
                            .filter(com.terraformersmc.modmenu.gui.widget.ModListWidget.class::isInstance)
                            .map(com.terraformersmc.modmenu.gui.widget.ModListWidget.class::cast).toList();
                    assertEquals(1, lists.size(), "the actual ModMenu screen must contain one mod list");
                    var list = lists.getFirst();
                    var entries = list.children().stream()
                            .filter(entry -> bettershulkerhud.Reference.MOD_ID.equals(entry.getMod().getId())).toList();
                    assertEquals(1, entries.size(), "the actual ModMenu list must contain our mod exactly once");
                    var entry = entries.getFirst();
                    list.select(entry);
                    list.ensureVisible(entry);
                    assertTrue(list.getSelected() == entry && mods.getSelectedEntry() == entry,
                            "public ModMenu list selection must update both the list and its real screen");
                    assertTrue(mods.getModHasConfigScreen(bettershulkerhud.Reference.MOD_ID),
                            "ModMenu must discover our registered configuration screen");
                    configureButton(mods);
                    client.getToastManager().clear();
                });
                context.waitTicks(3);
                context.takeScreenshot("modmenu-config-button");
                double[] point = context.computeOnClient(client -> {
                    var button = configureButton(mods);
                    return new double[]{(button.getX() + button.getWidth() / 2.0)
                            * client.getWindow().getScreenWidth() / mods.width,
                            (button.getY() + button.getHeight() / 2.0)
                            * client.getWindow().getScreenHeight() / mods.height};
                });
                context.getInput().setCursorPos(point[0], point[1]);
                context.waitTick();
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                context.waitFor(client -> client.screen instanceof bettershulkerhud.gui.GuiConfigs);
                context.runOnClient(client -> {
                    var config = (bettershulkerhud.gui.GuiConfigs) client.screen;
                    assertTrue(config.getParent() == mods,
                            "clicking the actual ModMenu configure button must retain that exact ModsScreen parent");
                    assertConfigScreenSelector(config);
                    client.getToastManager().clear();
                });
                context.getInput().setCursorPos(0.0, 0.0);
                context.waitTicks(3);
                context.takeScreenshot("modmenu-opened-config");
                context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
                context.waitFor(client -> client.screen == mods);
                context.runOnClient(client -> assertTrue(mods.getSelectedEntry() != null
                                && bettershulkerhud.Reference.MOD_ID.equals(mods.getSelectedEntry().getMod().getId()),
                        "Esc from the real config screen must return to the same selected ModMenu entry"));
                LOGGER.info("BSH_MODMENU_UI_ASSERTIONS_COMPLETE selected=true configure-active=true configure-visible=true mouse-click=true parent=true esc-return=true");
            } finally {
                context.setScreen(() -> originalScreen);
                context.runOnClient(client -> {
                    com.terraformersmc.modmenu.config.ModMenuConfig.HIDE_CONFIG_BUTTONS.setValue(options[0]);
                    fi.dy.masa.malilib.MaLiLibConfigs.Generic.ENABLE_CONFIG_SWITCHER.setBooleanValue(options[1]);
                });
            }
        }

        private static net.minecraft.client.gui.components.AbstractWidget configureButton(
                com.terraformersmc.modmenu.gui.ModsScreen mods) {
            // ModMenu 18 uses an empty message and CONFIGURE only as tooltip text.
            // Its public child widget has these exact pinned, javap-verified bounds.
            var buttons = mods.children().stream()
                    .filter(com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget.class::isInstance)
                    .map(com.terraformersmc.modmenu.gui.widget.LegacyTexturedButtonWidget.class::cast)
                    .filter(button -> button.getX() == mods.width - 24 && button.getY() == 48
                            && button.getWidth() == 20 && button.getHeight() == 20).toList();
            assertEquals(1, buttons.size(), "the actual ModMenu configure button must be uniquely present");
            var button = buttons.getFirst();
            assertTrue(button.active && button.visible,
                    "the selected Better Shulker HUD mod's actual configure button must be active and visible");
            return button;
        }
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
                .anyMatch(box -> box.contents().stream()
                        .anyMatch(stack -> stack.is(Items.OAK_LOG))));
        context.waitFor(client -> client.player.getRecipeBook().getCollections().stream()
                .anyMatch(collection -> collection.getRecipes().stream()
                        .anyMatch(entry -> entry.id().equals(plankDisplay.get()))));
        context.runOnClient(client -> {
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
        context.waitFor(client -> client.player.inventoryMenu.getResultSlot()
                .getItem().is(Items.OAK_PLANKS));
        context.runOnClient(client -> {
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
        context.waitFor(client -> client.player.containerMenu instanceof CraftingMenu menu
                && menu.getResultSlot().getItem().is(Items.DIAMOND_BLOCK));
        context.runOnClient(client -> {
            assertEquals(0, countShulkerItem(client.player.getInventory(), Items.DIAMOND),
                    "all nine diamonds must be removed from the carried shulker");
            assertEquals(9, ((CraftingMenu) client.player.containerMenu).getInputGridSlots().stream()
                            .mapToInt(slot -> slot.getItem().is(Items.DIAMOND)
                                    ? slot.getItem().getCount() : 0).sum(),
                    "vanilla recipe placement must fill the 3x3 craft grid");
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
                        "successful entry must activate the layout transaction");
            });
            context.waitTicks(3);
            dragPanelLayoutPreview(context);
            context.takeScreenshot("hud-layout-editor");
            context.runOnClient(client -> {
                bettershulkerhud.gui.HudPanelLayoutEditor.resetDraft();
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftX(),
                        "reset must restore horizontal AUTO in the draft");
                assertEquals(-1, bettershulkerhud.gui.HudPanelLayoutEditor.draftY(),
                        "reset must restore vertical AUTO in the draft");
                assertEquals(-1, Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "reset must not save horizontal coordinates");
                assertEquals(-1, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "reset must not save vertical coordinates");
                assertTrue(bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "reset must keep the layout transaction active");
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
                        "Escape must end the layout transaction");
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
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "Enter must end the layout transaction");
                int x = Configs.General.HUD_PANEL_X.getIntegerValue();
                int y = Configs.General.HUD_PANEL_Y.getIntegerValue();
                assertTrue(x >= 0 && x <= 10000 && y >= 0 && y <= 10000,
                        "Enter must commit normalized manual coordinates");
                return new int[]{x, y};
            });
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                assertEquals(committedPosition[0], Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "saved horizontal coordinates must survive screen closure");
                assertEquals(committedPosition[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "saved vertical coordinates must survive screen closure");
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
                assertTrue(!bettershulkerhud.gui.HudPanelLayoutEditor.isActive(),
                        "screen closure must end the layout transaction");
                assertEquals(committedPosition[1], Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "closing must restore the last saved vertical coordinate");
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

    private static void testHudRenderedHover(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        resetRememberedItems(context, singleplayer);
        setInventory(singleplayer, inventory -> {
            inventory.setSelectedSlot(0);
            inventory.setItem(0, new ItemStack(Items.WOODEN_SWORD));
            inventory.setItem(1, new ItemStack(Items.SHULKER_BOX));
        });
        int[] saved = new int[2];
        context.runOnClient(client -> {
            saved[0] = Configs.General.HUD_PANEL_X.getIntegerValue();
            saved[1] = Configs.General.HUD_PANEL_Y.getIntegerValue();
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            Configs.General.HUD_PANEL_X.setIntegerValue(5000);
            Configs.General.HUD_PANEL_Y.setIntegerValue(5000);
            BundlePanelRenderer.searchQuery = "";
            BundlePanelRenderer.searchFocused = false;
            client.setScreen(new InventoryScreen(client.player));
        });
        try {
            context.waitForScreen(InventoryScreen.class);
            context.waitFor(client -> !BundlePanelRenderer.getAllShulkers().isEmpty());
            context.runOnClient(client -> assertHudRenderedHover((InventoryScreen) client.screen));
        } finally {
            context.runOnClient(client -> {
                Configs.General.HUD_PANEL_X.setIntegerValue(saved[0]);
                Configs.General.HUD_PANEL_Y.setIntegerValue(saved[1]);
                BundlePanelRenderer.searchQuery = "";
                BundlePanelRenderer.searchFocused = false;
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

    private static void testRecipeBookPanelLayout(
            ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        context.getInput().resizeWindow(1400, 900);
        int[] original = new int[3];
        boolean[] originalHideButton = new boolean[1];
        context.runOnClient(client -> {
            client.options.guiScale().set(2);
            client.resizeGui();
            original[0] = Configs.General.HUD_PANEL_X.getIntegerValue();
            original[1] = Configs.General.HUD_PANEL_Y.getIntegerValue();
            original[2] = Configs.General.HUD_MAX_COLUMNS.getIntegerValue();
            originalHideButton[0] = Configs.Features.HIDE_RECIPE_BOOK_BUTTON.getBooleanValue();
            Configs.General.HUD_MAX_COLUMNS.setIntegerValue(1);
            Configs.General.HUD_PANEL_X.setIntegerValue(5000);
            Configs.General.HUD_PANEL_Y.setIntegerValue(5000);
            Configs.Features.HIDE_RECIPE_BOOK_BUTTON.setBooleanValue(false);
            Configs.Features.HUD_ENABLED.setBooleanValue(true);
            client.setScreen(new InventoryScreen(client.player));
        });
        try {
            context.waitForScreen(InventoryScreen.class);
            context.waitTicks(3);
            double[] recipeButtonPosition = context.computeOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                ImageButton button = findRecipeBookButton(screen);
                return new double[]{button.getX() + button.getWidth() / 2.0,
                        button.getY() + button.getHeight() / 2.0};
            });
            double scaleX = context.computeOnClient(client -> client.getWindow().getScreenWidth()
                    / (double) client.getWindow().getGuiScaledWidth());
            double scaleY = context.computeOnClient(client -> client.getWindow().getScreenHeight()
                    / (double) client.getWindow().getGuiScaledHeight());
            if (!context.computeOnClient(client -> BundlePanelRenderer.isRecipeBookOpen())) {
                context.getInput().setCursorPos(recipeButtonPosition[0] * scaleX,
                        recipeButtonPosition[1] * scaleY);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            context.waitFor(client -> BundlePanelRenderer.isRecipeBookOpen());
            context.waitTicks(3);
            context.runOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                int width = client.getWindow().getGuiScaledWidth();
                int panelX = BundlePanelRenderer.panelX(screen.leftPos);
                int panelWidth = BundlePanelRenderer.panelWidth(screen.leftPos);
                var bounds = BundlePanelRenderer.panelBounds(
                        screen.leftPos, screen.topPos, screen.imageHeight);
                assertTrue(width >= 379, "wide placement fixture must exceed the narrow threshold");
                assertTrue(BundlePanelRenderer.hasRenderablePanel(),
                        "recipe-book HUD must remain visible when a full panel fits");
                assertTrue(panelX >= screen.leftPos + screen.imageWidth,
                        "recipe-book override must place the complete HUD to the inventory right");
                assertTrue(bounds.x() >= screen.leftPos + screen.imageWidth - 4,
                        "recipe-book HUD full bounds must stay to the inventory right");
                assertTrue(bounds.x() + bounds.width() <= width - 4,
                        "recipe-book HUD full bounds must fit within the screen margin");
                assertTrue(panelWidth >= 84,
                        "a one-column panel must still provide the minimum width for the complete footer tab group");
                assertEquals(panelWidth + 8, bounds.width(),
                        "panel bounds include the full eight-pixel visual extent");
                assertTrue(BundlePanelRenderer.storageTabX(screen.leftPos, 0) >= panelX + 20,
                        "the first storage tab must not overlap the selected category edge");
                for (int i = 0; i < StorageView.values().length; i++) {
                    assertEquals(panelX + panelWidth - 5 - 58 + i * 20,
                            BundlePanelRenderer.storageTabX(screen.leftPos, i),
                            "storage tabs must be right aligned as a group");
                }
                assertEquals(5000, Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "recipe-book placement must not rewrite saved manual X");
                assertEquals(5000, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "recipe-book placement must not rewrite saved manual Y");
            });
            context.takeScreenshot("recipe-book-panel-right-aligned");
            double[] categoryHover = context.computeOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                int x = BundlePanelRenderer.panelX(screen.leftPos) + 5;
                int y = BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight) + 4;
                assertTrue(BundlePanelRenderer.getCategoryAt(
                                x, y, screen.leftPos, screen.topPos, screen.imageHeight) != null,
                        "category hover fixture must target a real category button");
                return new double[]{x * client.getWindow().getScreenWidth()
                        / (double) client.getWindow().getGuiScaledWidth(),
                        y * client.getWindow().getScreenHeight()
                                / (double) client.getWindow().getGuiScaledHeight()};
            });
            context.getInput().setCursorPos(categoryHover[0], categoryHover[1]);
            context.waitTicks(3);
            context.takeScreenshot("recipe-book-panel-category-tooltip");

            context.getInput().resizeWindow(700, 900);
            context.waitTicks(5);
            context.runOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                assertTrue(client.getWindow().getGuiScaledWidth() < 379,
                        "narrow fixture must exercise the hidden recipe-book state");
                assertTrue(!BundlePanelRenderer.hasRenderablePanel(),
                        "narrow recipe-book HUD must be absent, not clipped");
                double hitX = BundlePanelRenderer.panelX(screen.leftPos) + 8;
                double hitY = BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight) + 8;
                assertTrue(!BundlePanelRenderer.isInsidePanelBounds(hitX, hitY,
                                screen.leftPos, screen.topPos, screen.imageHeight),
                        "a hidden narrow HUD must not keep a panel hitbox");
                assertTrue(BundlePanelRenderer.getCategoryAt(hitX, hitY,
                                screen.leftPos, screen.topPos, screen.imageHeight) == null,
                        "a hidden narrow HUD must not keep category hitboxes");
                assertTrue(BundlePanelRenderer.getStorageViewAt(
                                BundlePanelRenderer.storageTabX(screen.leftPos, 0) + 9,
                                BundlePanelRenderer.storageTabY(screen.topPos, screen.imageHeight) + 9,
                                screen.leftPos, screen.topPos, screen.imageHeight) == null,
                        "a hidden narrow HUD must not keep storage-tab hitboxes");
                String query = BundlePanelRenderer.searchQuery;
                BundlePanelRenderer.searchFocused = true;
                assertTrue(!BundlePanelRenderer.onCharTyped(65)
                                && query.equals(BundlePanelRenderer.searchQuery),
                        "hidden search must not consume or mutate typed input");
                BundlePanelRenderer.searchFocused = true;
                assertTrue(!BundlePanelRenderer.onSearchKeyPress(GLFW.GLFW_KEY_A, 0)
                                && !BundlePanelRenderer.searchFocused,
                        "hidden panel must release search focus instead of trapping input");
            });
            context.takeScreenshot("recipe-book-panel-hidden-narrow");
            context.runOnClient(client -> Configs.General.HUD_MAX_COLUMNS.setIntegerValue(8));
            for (int windowWidth : new int[]{1000, 1068, 1072}) {
                context.getInput().resizeWindow(windowWidth, 900);
                context.waitTicks(4);
                context.runOnClient(client -> {
                    InventoryScreen screen = (InventoryScreen) client.screen;
                    int guiWidth = client.getWindow().getGuiScaledWidth();
                    assertTrue(BundlePanelRenderer.isRecipeBookOpen(),
                            "window resizing must keep the recipe book open for boundary checks");
                    var placement = HudOverlayLayout.choose(guiWidth, screen.leftPos, screen.imageWidth,
                            92, true, FabricLoader.getInstance().isModLoaded("jei"),
                            FabricLoader.getInstance().isModLoaded("roughlyenoughitems"));
                    boolean fits = placement.side() == HudOverlayLayout.Side.RIGHT;
                    assertTrue(BundlePanelRenderer.hasRenderablePanel() == fits,
                            "saved manual coordinates must not bypass insufficient recipe-book space");
                    if (fits) {
                        int columns = BundlePanelRenderer.columnCount(screen.leftPos);
                        assertTrue(BundlePanelRenderer.panelWidth(screen.leftPos) + 8 <= placement.availableWidth(),
                                "selected columns must fit their complete visual width");
                        int nextWidth = Math.max(84, 49 + (columns + 1) * 18) + 8;
                        assertTrue(columns == Configs.General.HUD_MAX_COLUMNS.getIntegerValue()
                                        || nextWidth > placement.availableWidth(),
                                "the chosen column count must be the largest complete fit");
                    } else {
                        assertTrue(!BundlePanelRenderer.isInsidePanelBounds(
                                        BundlePanelRenderer.gridX(screen.leftPos) + 1,
                                        BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight) + 1,
                                        screen.leftPos, screen.topPos, screen.imageHeight),
                                "insufficient-space HUD must not leave a ghost grid hitbox");
                    }
                });
            }
            context.runOnClient(client -> Configs.General.HUD_MAX_COLUMNS.setIntegerValue(1));
            context.getInput().resizeWindow(1400, 900);
            context.waitTicks(4);
            recipeButtonPosition = context.computeOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                double sx = client.getWindow().getScreenWidth()
                        / (double) client.getWindow().getGuiScaledWidth();
                double sy = client.getWindow().getScreenHeight()
                        / (double) client.getWindow().getGuiScaledHeight();
                ImageButton button = findRecipeBookButton(screen);
                return new double[]{(button.getX() + button.getWidth() / 2.0) * sx,
                        (button.getY() + button.getHeight() / 2.0) * sy};
            });
            context.getInput().setCursorPos(recipeButtonPosition[0], recipeButtonPosition[1]);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitFor(client -> !BundlePanelRenderer.isRecipeBookOpen());
            context.runOnClient(client -> {
                InventoryScreen screen = (InventoryScreen) client.screen;
                assertTrue(BundlePanelRenderer.hasRenderablePanel(),
                        "closing recipe book must restore the saved manual HUD position");
                assertEquals(5000, Configs.General.HUD_PANEL_X.getIntegerValue(),
                        "closing the recipe book must preserve saved X");
                assertEquals(5000, Configs.General.HUD_PANEL_Y.getIntegerValue(),
                        "closing the recipe book must preserve saved Y");
                int expectedX = bettershulkerhud.core.HudPanelLayout.resolve(5000,
                        client.getWindow().getGuiScaledWidth(),
                        BundlePanelRenderer.panelWidth(screen.leftPos) + 8, 0) + 4;
                assertEquals(expectedX, BundlePanelRenderer.panelX(screen.leftPos),
                        "closing recipe book restores normalized manual X placement");
                int expectedY = bettershulkerhud.core.HudPanelLayout.resolve(5000,
                        client.getWindow().getGuiScaledHeight(),
                        BundlePanelRenderer.panelHeight(screen.topPos, screen.imageHeight) + 5, 0) + 1;
                assertEquals(expectedY, BundlePanelRenderer.panelY(screen.topPos, screen.imageHeight),
                        "closing recipe book restores normalized manual Y placement");
            });
        } finally {
            context.setScreen(() -> null);
            context.runOnClient(client -> {
                Configs.General.HUD_PANEL_X.setIntegerValue(original[0]);
                Configs.General.HUD_PANEL_Y.setIntegerValue(original[1]);
                Configs.General.HUD_MAX_COLUMNS.setIntegerValue(original[2]);
                Configs.Features.HIDE_RECIPE_BOOK_BUTTON.setBooleanValue(originalHideButton[0]);
            });
            context.getInput().resizeWindow(2560, 1440);
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
        context.waitTick();
        context.runOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            var items = BundlePanelRenderer.getVisibleItems();
            int shulkers = BundlePanelRenderer.getAllShulkers().size();
            double guiX = cursor[0] * client.getWindow().getGuiScaledWidth()
                    / client.getWindow().getScreenWidth();
            double guiY = cursor[1] * client.getWindow().getGuiScaledHeight()
                    / client.getWindow().getScreenHeight();
            LOGGER.info("BSH_RIGHT_CLICK_PRECONDITIONS enabled={} diagnostics={} visible={} renderable={} "
                            + "carried={} items={} shulkers={} cursor={},{} gui={},{} grid={},{} scroll={}",
                    Configs.Features.HUD_ENABLED.getBooleanValue(),
                    Configs.General.DIAGNOSTIC_LOGGING.getBooleanValue(),
                    BundlePanelRenderer.isEffectivelyVisible(), BundlePanelRenderer.hasRenderablePanel(),
                    client.player.containerMenu.getCarried(), items.size(), shulkers,
                    cursor[0], cursor[1], guiX, guiY,
                    BundlePanelRenderer.gridX(screen.leftPos),
                    BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight),
                    BundlePanelRenderer.getScrollOffset());
            assertTrue(Configs.Features.HUD_ENABLED.getBooleanValue(),
                    "native right-click fixture must enable the HUD after world settings reload");
            assertTrue(Configs.General.DIAGNOSTIC_LOGGING.getBooleanValue(),
                    "native right-click fixture must keep extraction diagnostics enabled");
            assertTrue(BundlePanelRenderer.isEffectivelyVisible()
                            && BundlePanelRenderer.hasRenderablePanel(),
                    "native right-click fixture must have a visible renderable HUD");
            assertTrue(client.player.containerMenu.getCarried().isEmpty()
                            && client.player.inventoryMenu.getCarried().isEmpty(),
                    "native right-click fixture must start with an empty cursor");
            assertEquals(1, shulkers, "native right-click fixture must expose exactly one shulker");
            assertEquals(1, items.size(), "native right-click fixture must expose one HUD item");
            ItemStack first = items.getFirst().stack();
            assertTrue(first.is(Items.DIAMOND) && first.getCount() == 5,
                    "native right-click first HUD item must be exactly five diamonds");
            assertTrue(items.getFirst().storageView() == StorageView.SHULKERS,
                    "native right-click first HUD item must come from the shulker view");
            assertEquals(0, BundlePanelRenderer.getScrollOffset(),
                    "native right-click coordinates must target the first visible HUD item");
            assertTrue(cursor[0] >= 0 && cursor[0] < client.getWindow().getScreenWidth()
                            && cursor[1] >= 0 && cursor[1] < client.getWindow().getScreenHeight(),
                    "native right-click cursor must be inside the window");
            int gridX = BundlePanelRenderer.gridX(screen.leftPos);
            int gridY = BundlePanelRenderer.gridY(screen.topPos, screen.imageHeight);
            assertTrue(guiX >= gridX && guiX < gridX + BundlePanelRenderer.SLOT_SIZE
                            && guiY >= gridY && guiY < gridY + BundlePanelRenderer.SLOT_SIZE
                            && BundlePanelRenderer.isInsidePanelBounds(guiX, guiY,
                            screen.leftPos, screen.topPos, screen.imageHeight),
                    "native right-click scaled coordinates must hit the first HUD cell");
        });
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
