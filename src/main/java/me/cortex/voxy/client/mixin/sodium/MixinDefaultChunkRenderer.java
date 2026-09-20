package me.cortex.voxy.client.mixin.sodium;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.backend.opengl.GlTextureView;
import me.cortex.voxy.client.VoxyClient;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer extends ShaderChunkRenderer {

    @Shadow
    @Final
    private boolean[] shouldDraw;

    public MixinDefaultChunkRenderer(ChunkVertexType vertexType) {
        super(vertexType);
    }

    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    private void voxy$cancelThingie(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters parameters, boolean indexedRenderingEnabled, RenderPass pass, GpuSampler terrainSampler, GpuBufferSlice uniformData, GpuBuffer sectionTimeInfo, OitStage stage, CallbackInfo ci) {
        if (VoxyClient.disableSodiumChunkRender()) {
            super.begin(renderPass, parameters, terrainSampler, stage);
            this.doRender(matrices, renderPass, camera, parameters);
            super.end(renderPass);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.BEFORE))
    private void voxy$injectRender(ChunkRenderMatrices matrices, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, FogParameters parameters, boolean indexedRenderingEnabled, RenderPass pass, GpuSampler terrainSampler, GpuBufferSlice uniformData, GpuBuffer sectionTimeInfo, OitStage stage, CallbackInfo ci) {
        this.doRender(matrices, renderPass, camera, parameters);
    }

    @Inject(method = "prepare", at = @At(value = "TAIL"))
    private void voxy$forceOpaque(ChunkRenderListIterable renderLists, CameraTransform cameraTransform, boolean indexedRenderingEnabled, CallbackInfo ci) {
        var renderer = IVoxyRenderSystemHolder.getNullable();
        if (renderer != null) {
            this.shouldDraw[DefaultTerrainRenderPasses.getPassIndex(DefaultTerrainRenderPasses.CUTOUT)] = true;
        }
    }

    @Unique
    private void doRender(ChunkRenderMatrices matrices, TerrainRenderPass renderPass, CameraTransform camera, FogParameters fogParameters) {
        if (renderPass == DefaultTerrainRenderPasses.CUTOUT) {
            var renderer = IVoxyRenderSystemHolder.getNullable();
            if (renderer != null) {
                Viewport<?> viewport = null;
                var target = renderPass.getTarget();
                if (IrisUtil.USED_IRIS_VIEWPORT) {
                    viewport = renderer.getViewport();
                    IrisUtil.USED_IRIS_VIEWPORT = false;
                } else {
                    viewport = renderer.setupViewport(matrices.projection(), matrices.modelView(), fogParameters, target.width, target.height, camera.x, camera.y, camera.z);
                }
                renderer.renderOpaque(viewport, ((GlTextureView)target.getDepthTextureView()).glId(), ((GlTextureView)target.getColorTextureView()).glId());
            }
        }
    }
}
