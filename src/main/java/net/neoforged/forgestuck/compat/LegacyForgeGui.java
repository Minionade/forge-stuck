package net.neoforged.forgestuck.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;

/** Compatibility helpers for ForgeGui methods removed with the legacy overlay renderer. */
public final class LegacyForgeGui {
    private LegacyForgeGui() {}

    public static boolean shouldDrawSurvivalElements(Gui gui) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null
                && minecraft.gameMode.canHurtPlayer()
                && minecraft.getCameraEntity() instanceof Player;
    }

    public static void setupOverlayRenderState(Gui gui, boolean blend, boolean depthTest) {
        if (blend) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }

        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
    }
}
