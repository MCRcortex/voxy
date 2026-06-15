package me.cortex.neovoxy.client.mixin.sodium;

import me.cortex.neovoxy.client.NeoVoxyClient;
import me.cortex.neovoxy.client.NeoVoxyClientEvents;
import me.cortex.neovoxy.client.core.IGetNeoVoxyRenderSystem;
import me.cortex.neovoxy.client.core.rendering.Viewport;
// MC 1.21.1 NeoForge: Iris shader integration excluded
// import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
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

    // Sodium 0.8.12-beta.1 for MC 1.21.1 adds indexed rendering, but still uses
    // the pre-GpuSampler/pre-FogParameters render path.
    @Inject(method = "render", at = @At(value = "HEAD"), cancellable = true)
    private void neovoxy$cancelThingie(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, boolean indexedRenderingEnabled, CallbackInfo ci) {
        if (NeoVoxyClient.disableSodiumChunkRender()) {
            super.begin(renderPass);
            this.doRender(matrices, renderPass, camera);
            super.end(renderPass);
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/ShaderChunkRenderer;end(Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/TerrainRenderPass;)V", shift = At.Shift.BEFORE))
    private void neovoxy$injectRender(ChunkRenderMatrices matrices, CommandList commandList, ChunkRenderListIterable renderLists, TerrainRenderPass renderPass, CameraTransform camera, boolean indexedRenderingEnabled, CallbackInfo ci) {
        this.doRender(matrices, renderPass, camera);
    }

    @Unique
    private void doRender(ChunkRenderMatrices matrices, TerrainRenderPass renderPass, CameraTransform camera) {
        if (renderPass == DefaultTerrainRenderPasses.CUTOUT && !NeoVoxyClientEvents.shouldSuppressLodRenderingForFog()) {
            var renderer = ((IGetNeoVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getNeoVoxyRenderSystem();
            if (renderer != null) {
                Viewport<?> viewport = null;
                // MC 1.21.1 NeoForge: Iris shader integration excluded - irisShaderPackEnabled() returns false
                if (false) {
                    viewport = renderer.getViewport();
                } else {
                    // Sodium 0.6.x: setupViewport no longer takes FogParameters
                    viewport = renderer.setupViewport(matrices, camera.x, camera.y, camera.z);
                }
                renderer.renderOpaque(viewport);
            }
        }
    }
}
