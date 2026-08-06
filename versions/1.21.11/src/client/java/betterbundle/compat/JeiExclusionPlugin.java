package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class JeiExclusionPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.parse("better-shulker-hud:gui_exclusions");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(AbstractContainerScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(AbstractContainerScreen screen) {
                List<Rect2i> exclusions = new ArrayList<>();
                if (BundlePanelRenderer.shouldShowToggleButton()) {
                    exclusions.add(new Rect2i(
                            BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth) - 2,
                            BundlePanelRenderer.toggleY(screen.topPos) - 2,
                            BundlePanelRenderer.TOGGLE_WIDTH + 4,
                            BundlePanelRenderer.TOGGLE_HEIGHT + 4));
                }
                if (BundlePanelRenderer.isEffectivelyVisible()) {
                    exclusions.add(new Rect2i(
                            BundlePanelRenderer.exclusionX(screen.leftPos),
                            BundlePanelRenderer.exclusionY(screen.topPos, screen.imageHeight),
                            BundlePanelRenderer.exclusionWidth(screen.leftPos),
                            BundlePanelRenderer.exclusionHeight(screen.topPos, screen.imageHeight)));
                }
                return exclusions;
            }
        });
    }
}
