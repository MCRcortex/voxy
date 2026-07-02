package me.cortex.voxy.client.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRendererVS {
    
    @Unique
    private ChunkRenderMatrices voxy$capturedMatrices;

    @Inject(method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDD)V", at = @At("HEAD"))
    private void voxy$captureMatrices(RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
        this.voxy$capturedMatrices = matrices;
    }

    @Inject(method = "drawChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDD)V", at = @At("TAIL"))
    private void injectRender(RenderType renderLayer, ChunkRenderMatrices matrices, double x, double y, double z, CallbackInfo ci) {
        this.doRender(this.voxy$capturedMatrices, renderLayer, x, y, z);
    }
    
    @Unique
    private void doRender(ChunkRenderMatrices matrices, RenderType renderLayer, double x, double y, double z) {
        if (renderLayer == RenderType.solid()) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).voxy$getRenderSystem();
            if (renderer != null) {
                Viewport<?> viewport = null;
                if (IrisUtil.irisShaderPackEnabled()) {
                    viewport = renderer.getViewport();
                } else {
                    viewport = renderer.setupViewport(matrices.projection(), matrices.modelView(), x, y, z);
                }
                renderer.renderOpaque(viewport);
            }
        }
    }
}
