package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public final class JeiExclusionPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("better-shulker-hud:gui_exclusions");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        IRecipeTransferHandlerHelper helper = registration.getTransferHelper();
        var info = helper.createBasicRecipeTransferInfo(
                InventoryMenu.class, null, RecipeTypes.CRAFTING,
                InventoryMenu.CRAFT_SLOT_START, InventoryMenu.CRAFT_SLOT_COUNT,
                InventoryMenu.INV_SLOT_START, 36);
        IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> vanilla =
                helper.createUnregisteredRecipeTransferHandler(info);
        registration.addRecipeTransferHandler(
                new ShulkerCraftingTransferHandler(vanilla, helper), RecipeTypes.CRAFTING);
        var tableInfo = helper.createBasicRecipeTransferInfo(
                CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING,
                1, 9, 10, 36);
        IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> tableVanilla =
                helper.createUnregisteredRecipeTransferHandler(tableInfo);
        registration.addRecipeTransferHandler(
                new ShulkerTableTransferHandler(tableVanilla, helper), RecipeTypes.CRAFTING);

    }

    private record ShulkerTableTransferHandler(
            IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> vanilla,
            IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<CraftingMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends CraftingMenu> getContainerClass() {
            return CraftingMenu.class;
        }

        @Override
        public Optional<MenuType<CraftingMenu>> getMenuType() {
            return vanilla.getMenuType();
        }

        @Override
        public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public IRecipeTransferError transferRecipe(
                CraftingMenu menu, RecipeHolder<CraftingRecipe> recipe,
                IRecipeSlotsView slots, Player player,
                boolean maxTransfer, boolean doTransfer) {
            var placement = recipe.value().placementInfo();
            List<Ingredient> requirements = placement.ingredients();
            if (placement.isImpossibleToPlace() || requirements.size() > 9
                    || !helper.recipeTransferHasServerSupport()) {
                return vanilla.transferRecipe(menu, recipe, slots, player,
                        maxTransfer, doTransfer);
            }
            List<BundlePanelRenderer.FlatItem> missing =
                    RecipePreparationController.preview(requirements, maxTransfer);
            if (missing == null || missing.isEmpty()) {
                return vanilla.transferRecipe(menu, recipe, slots, player,
                        maxTransfer, doTransfer);
            }
            if (!doTransfer) return null;
            boolean started = RecipePreparationController.beginJei(requirements, maxTransfer, () -> {
                if (Minecraft.getInstance().player.containerMenu instanceof CraftingMenu current) {
                    vanilla.transferRecipe(current, recipe, slots, player, maxTransfer, true);
                }
            });
            return started ? null : helper.createUserErrorWithTooltip(
                    Component.translatable("message.better-shulker-hud.recipe_supply_failed"));
        }
    }


    private record ShulkerCraftingTransferHandler(
            IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> vanilla,
            IRecipeTransferHandlerHelper helper)
            implements IRecipeTransferHandler<InventoryMenu, RecipeHolder<CraftingRecipe>> {
        @Override
        public Class<? extends InventoryMenu> getContainerClass() {
            return InventoryMenu.class;
        }

        @Override
        public Optional<MenuType<InventoryMenu>> getMenuType() {
            return vanilla.getMenuType();
        }

        @Override
        public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        public IRecipeTransferError transferRecipe(
                InventoryMenu menu, RecipeHolder<CraftingRecipe> recipe,
                IRecipeSlotsView slots, Player player,
                boolean maxTransfer, boolean doTransfer) {
            var placement = recipe.value().placementInfo();
            List<Ingredient> requirements = placement.ingredients();
            if (placement.isImpossibleToPlace() || requirements.size() > 4
                    || !helper.recipeTransferHasServerSupport()) {
                return vanilla.transferRecipe(menu, recipe, slots, player,
                        maxTransfer, doTransfer);
            }
            List<BundlePanelRenderer.FlatItem> missing =
                    RecipePreparationController.preview(requirements, maxTransfer);
            if (missing == null || missing.isEmpty()) {
                return vanilla.transferRecipe(menu, recipe, slots, player,
                        maxTransfer, doTransfer);
            }
            if (!doTransfer) return null;
            boolean started = RecipePreparationController.beginJei(requirements, maxTransfer,
                    () -> vanilla.transferRecipe(menu, recipe, slots, player, maxTransfer, true));
            return started ? null : helper.createUserErrorWithTooltip(
                    Component.translatable("message.better-shulker-hud.recipe_supply_failed"));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public List<Rect2i> getGuiExtraAreas() {
                if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen)) {
                    return List.of();
                }
                List<Rect2i> exclusions = new ArrayList<>();
                if (BundlePanelRenderer.shouldShowToggleButton()) {
                    exclusions.add(new Rect2i(
                            BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth) - 2,
                            BundlePanelRenderer.toggleY(screen.topPos) - 2,
                            BundlePanelRenderer.TOGGLE_WIDTH + 4,
                            BundlePanelRenderer.TOGGLE_HEIGHT + 4));
                }
                if (BundlePanelRenderer.hasRenderablePanel()) {
                    exclusions.add(new Rect2i(
                            BundlePanelRenderer.exclusionX(screen.leftPos),
                            BundlePanelRenderer.exclusionY(screen.topPos, screen.imageHeight),
                            BundlePanelRenderer.exclusionWidth(screen.leftPos),
                            BundlePanelRenderer.exclusionHeight(screen.topPos, screen.imageHeight)));
                }
                if (bettershulkerhud.gui.HudPanelLayoutEditor.isActive()) {
                    var controls = bettershulkerhud.gui.HudPanelLayoutEditor.controlsBounds();
                    exclusions.add(new Rect2i(controls.x(), controls.y(), controls.width(), controls.height()));
                }
                if (BundlePanelRenderer.isTogglePositionAdjustMode()) {
                    int[] reset = BundlePanelRenderer.adjustResetRect();
                    int[] done = BundlePanelRenderer.adjustDoneRect();
                    exclusions.add(new Rect2i(
                            reset[0] - 2, reset[1] - 2, reset[2] + 4, reset[3] + 4));
                    exclusions.add(new Rect2i(
                            done[0] - 2, done[1] - 2, done[2] + 4, done[3] + 4));
                }
                return exclusions;
            }
        });
    }
}
