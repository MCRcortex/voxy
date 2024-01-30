package me.cortex.zenith.client.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRender {
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true)
    private void cancelRender(RenderLayer renderLayer, MatrixStack matrixStack, double x, double y, double z, CallbackInfo ci) {
        //ci.cancel();
    }
}
