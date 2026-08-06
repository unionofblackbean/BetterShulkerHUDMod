package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class EmiExclusionPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addExclusionArea(AbstractContainerScreen.class, (screen, consumer) -> {
            if (BundlePanelRenderer.shouldShowToggleButton()) {
                consumer.accept(new Bounds(
                        BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth) - 2,
                        BundlePanelRenderer.toggleY(screen.topPos) - 2,
                        BundlePanelRenderer.TOGGLE_WIDTH + 4,
                        BundlePanelRenderer.TOGGLE_HEIGHT + 4));
            }
            if (BundlePanelRenderer.isEffectivelyVisible()) {
                consumer.accept(new Bounds(
                        BundlePanelRenderer.exclusionX(screen.leftPos),
                        BundlePanelRenderer.exclusionY(screen.topPos, screen.imageHeight),
                        BundlePanelRenderer.exclusionWidth(screen.leftPos),
                        BundlePanelRenderer.exclusionHeight(screen.topPos, screen.imageHeight)));
            }
        });
    }
}
