package me.cortex.neovoxy.client.mixin.iris;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.shaderpack.include.ShaderPackSourceNames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ShaderPackSourceNames.class, remap = false)
public class MixinShaderPackSourceNames {
    @WrapOperation(method = "findPotentialStarts", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;builder()Lcom/google/common/collect/ImmutableList$Builder;"))
    private static ImmutableList.Builder<String> neovoxy$injectNeoVoxyShaderPatch(Operation<ImmutableList.Builder<String>> original){
        var builder = original.call();
        builder.add("neovoxy.json");
        builder.add("neovoxy_opaque.glsl");
        builder.add("neovoxy_translucent.glsl");
        return builder;
    }
}
