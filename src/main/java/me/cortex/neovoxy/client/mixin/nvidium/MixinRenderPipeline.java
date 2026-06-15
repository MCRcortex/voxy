package me.cortex.neovoxy.client.mixin.nvidium;

import me.cortex.nvidium.RenderPipeline;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderPipeline.class, remap = false)
public class MixinRenderPipeline {
    // Sodium 0.6.x: FogParameters removed
    @Inject(method = "renderFrame", at = @At("RETURN"))
    private void neovoxy$injectRender(TerrainRenderPass pass, Viewport frustum, ChunkRenderMatrices crm, double px, double py, double pz, CallbackInfo ci) {
        var renderer = ((IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getNeoVoxyRenderSystem();
        if (renderer != null) {
            renderer.renderOpaque(renderer.setupViewport(crm, px, py, pz));
        }
    }
}
