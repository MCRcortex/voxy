package me.cortex.voxy.client.mixin.iris;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Set;

@Mixin(value = PackRenderTargetDirectives.class, remap = false)
public class MixinPackRenderTargetDirectives {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableSet$Builder;build()Lcom/google/common/collect/ImmutableSet;"))
    private static ImmutableSet<Integer> voxy$injectExtraColourTex(ImmutableSet.Builder<Integer> builder) {
        int limit = System.getProperty("voxy.IrisExtremeColourTexOverride", "false").equalsIgnoreCase("true")?200:20;
        for (int i = 16; i < limit; i++) {
            builder.add(i);
        }
        return builder.build();
    }
}
