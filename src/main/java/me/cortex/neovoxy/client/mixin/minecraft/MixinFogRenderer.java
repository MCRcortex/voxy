package me.cortex.neovoxy.client.mixin.minecraft;

import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * MC 1.21.1 compatible fog mixin.
 *
 * This is a placeholder mixin that documents fog handling for NeoForge port.
 * The actual fog manipulation (save-restore pattern) is done in NeoVoxyRenderSystem.renderOpaque()
 * to ensure vanilla terrain renders with normal fog, while NeoVoxy LODs render with fog pushed to infinity.
 *
 * Render order:
 * 1. setupFog(FOG_TERRAIN) called - fog set to vanilla render distance
 * 2. Vanilla terrain renders - with normal fog
 * 3. NeoVoxy renderOpaque() called:
 *    a. Save current fog values
 *    b. Push fog to infinity (999999.0f)
 *    c. Render LODs without fog wall
 *    d. Restore original fog values
 * 4. Clouds/other elements render - with correct fog
 */
@Mixin(FogRenderer.class)
public class MixinFogRenderer {
    // No injections needed - NeoVoxyRenderSystem handles fog save-restore directly
}
