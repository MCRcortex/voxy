package me.cortex.neovoxy.client.mixin.iris;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.client.iris.IrisShaderPatch;
import net.irisshaders.iris.gl.shader.StandardMacros;
import net.irisshaders.iris.helpers.StringPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.List;

@Mixin(value = StandardMacros.class, remap = false)
public abstract class MixinStandardMacros {
    @Shadow
    private static void define(List<StringPair> defines, String key){}

    @WrapOperation(method = "createStandardEnvironmentDefines", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/ImmutableList;copyOf(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<StringPair> neovoxy$injectNeoVoxyDefine(Collection<StringPair> list, Operation<ImmutableList<StringPair>> original) {
        if (NeoVoxyConfig.CONFIG.isRenderingEnabled() && IrisUtil.SHADER_SUPPORT) {
            define((List<StringPair>) list, "VOXY");
            if (IrisShaderPatch.IMPERSONATE_DISTANT_HORIZONS) {
                define((List<StringPair>) list, "DISTANT_HORIZONS");
            }
        }
        return ImmutableList.copyOf(list);
    }
}
