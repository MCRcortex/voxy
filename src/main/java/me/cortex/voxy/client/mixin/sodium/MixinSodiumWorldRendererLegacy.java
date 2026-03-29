package me.cortex.voxy.client.mixin.sodium;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import me.cortex.voxy.client.core.util.IrisUtil;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRendererLegacy {
    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private void voxy$injectThreadUpdate(CommandList cl, CallbackInfo ci) {
        var vi = VoxyCommon.getInstance();
        if (vi != null) vi.updateDedicatedThreads();
    }

    @Unique
    private ChunkRenderMatrices voxy$capturedMatrices;

    @Dynamic
    @Inject(method = "drawChunkLayer(Lnet/minecraft/class_1921;Lnet/minecraft/class_4587;DDD)V", at = @At("HEAD"))
    private void voxy$captureMatrices(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, CallbackInfo ci) {
        this.voxy$capturedMatrices = ChunkRenderMatrices.from(matrixStack);
    }

    @Dynamic
    @Inject(method = "drawChunkLayer(Lnet/minecraft/class_1921;Lnet/minecraft/class_4587;DDD)V", at = @At("TAIL"))
    private void injectRender(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, CallbackInfo ci) {
        this.doRender(this.voxy$capturedMatrices, renderLayer, x, y, z);
    }
    
    @Unique
    private void doRender(ChunkRenderMatrices matrices, RenderType renderLayer, double x, double y, double z) {
        if (renderLayer == RenderType.solid()) {
            var renderer = ((IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer).getVoxyRenderSystem();
            if (renderer != null) {
                Viewport<?> viewport = null;
                if (IrisUtil.irisShaderPackEnabled()) {
                    viewport = renderer.getViewport();
                } else {
                    viewport = renderer.setupViewport(matrices, x, y, z);
                }
                renderer.renderOpaque(viewport);
            }
        }
    }
}