package me.cortex.voxy.client.mixin.sodium;

import com.mojang.blaze3d.textures.GpuSampler;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.commonImpl.VoxyCommon;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.gl.device.RenderDevice;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer {

    public MixinDefaultChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        super(device, vertexType);
    }

    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    private void cancelThingie(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters fogParameters, boolean indexedRenderingEnabled, GpuSampler terrainSampler, CallbackInfo ci) {
        if (VoxyClient.disableSodiumChunkRender()) {
            super.begin(renderPass, fogParameters, terrainSampler);
            this.doRender(matrices, renderPass, camera, fogParameters);
            super.end(renderPass);
            ci.cancel();
        }
    }

    // M12 close: moved the Voxy hook from BEFORE-Sodium-end on the CUTOUT
    // pass to HEAD of the SOLID pass. With the IOSurfaceBridgeCompositor now
    // blitting full-screen, doing this BEFORE Sodium SOLID lets MC's depth
    // buffer accumulate Sodium's near terrain ON TOP of Voxy's distant LOD
    // — so the user sees Voxy LOD behind Sodium chunks rather than as a
    // diagnostic strip. MC's main RT is bound throughout Sodium's render
    // call, so HEAD is a fine inject point (the original "FBO 0 after
    // LevelRenderer return" gotcha that drove the BEFORE-end position is
    // about LevelRenderer's RETURN, not Sodium's).
    @Inject(method = "render", at = @At(value = "HEAD"))
    private void injectRender(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters fogParameters, boolean indexedRenderingEnabled, GpuSampler terrainSampler, CallbackInfo ci) {
        this.doRender(matrices, renderPass, camera, fogParameters);
    }

    @Unique
    private void doRender(ChunkRenderMatrices matrices, TerrainRenderPass renderPass, CameraTransform camera, FogParameters fogParameters) {
        if (renderPass == DefaultTerrainRenderPasses.SOLID) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getVoxyRenderSystem();
            if (renderer != null) {
                Viewport<?> viewport = null;
                if (IrisUtil.irisShaderPackEnabled()) {
                    viewport = renderer.getViewport();
                } else {
                    viewport = renderer.setupViewport(matrices, fogParameters, camera.x, camera.y, camera.z);
                }
                renderer.renderOpaque(viewport);

                // M12 close: if Voxy rendered into a Metal-side IOSurface
                // bridge, composite it into MC's main RT NOW — at HEAD of
                // Sodium's SOLID pass — so the full-screen blit lands first
                // and Sodium's subsequent solid/cutout/translucent draws
                // overdraw the near terrain on top via depth.
                var pipeline = renderer.getPipeline();
                if (pipeline != null && pipeline.metalBridge() != null) {
                    me.cortex.voxy.client.core.interop.IOSurfaceBridgeCompositor
                            .composite(pipeline.metalBridge());
                }
            }
        }
    }
}
