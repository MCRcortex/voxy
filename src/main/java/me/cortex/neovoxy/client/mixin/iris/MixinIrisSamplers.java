package me.cortex.neovoxy.client.mixin.iris;

import com.google.common.collect.ImmutableSet;
import me.cortex.neovoxy.client.iris.NeoVoxySamplers;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.targets.RenderTargets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(value = IrisSamplers.class, remap = false)
public class MixinIrisSamplers {
    @Inject(method = "addRenderTargetSamplers", at = @At("TAIL"))
    private static void neovoxy$injectSamplers(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped, RenderTargets renderTargets, boolean isFullscreenPass, WorldRenderingPipeline pipeline, CallbackInfo ci) {
        if (pipeline instanceof IrisRenderingPipeline ipipe) {
            NeoVoxySamplers.addSamplers(ipipe, samplers);
        }
    }
}
