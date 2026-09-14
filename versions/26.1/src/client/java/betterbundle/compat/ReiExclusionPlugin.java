package bettershulkerhud.compat;

import bettershulkerhud.gui.BundlePanelRenderer;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import java.util.ArrayList;
import java.util.List;

public final class ReiExclusionPlugin implements REIClientPlugin {
    @Override
    public void registerExclusionZones(ExclusionZones zones) {
        zones.register(AbstractContainerScreen.class, screen -> {
            List<Rectangle> exclusions = new ArrayList<>();
            if (BundlePanelRenderer.shouldShowToggleButton()) {
            exclusions.add(new Rectangle(
                    BundlePanelRenderer.toggleX(screen.leftPos, screen.imageWidth) - 2,
                    BundlePanelRenderer.toggleY(screen.topPos) - 2,
                    BundlePanelRenderer.TOGGLE_WIDTH + 4,
                    BundlePanelRenderer.TOGGLE_HEIGHT + 4));
            }
            if (BundlePanelRenderer.isEffectivelyVisible()) {
                exclusions.add(new Rectangle(
                        BundlePanelRenderer.exclusionX(screen.leftPos),
                        BundlePanelRenderer.exclusionY(screen.topPos, screen.imageHeight),
                        BundlePanelRenderer.exclusionWidth(screen.leftPos),
                        BundlePanelRenderer.exclusionHeight(screen.topPos, screen.imageHeight)));
            }
            if (BundlePanelRenderer.isTogglePositionAdjustMode()) {
                int[] reset = BundlePanelRenderer.adjustResetRect();
                int[] done = BundlePanelRenderer.adjustDoneRect();
                exclusions.add(new Rectangle(
                        reset[0] - 2, reset[1] - 2, reset[2] + 4, reset[3] + 4));
                exclusions.add(new Rectangle(
                        done[0] - 2, done[1] - 2, done[2] + 4, done[3] + 4));
            }
            return List.copyOf(exclusions);
        });
    }
}
