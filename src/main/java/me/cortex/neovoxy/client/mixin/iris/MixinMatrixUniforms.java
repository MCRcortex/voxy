package me.cortex.neovoxy.client.mixin.iris;

import me.cortex.neovoxy.client.config.NeoVoxyConfig;
import me.cortex.neovoxy.client.core.util.IrisUtil;
import me.cortex.neovoxy.client.iris.NeoVoxyUniforms;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.shaderpack.IdMap;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommonUniforms.class, remap = false)
public class MixinMatrixUniforms {
    @Inject(method = "addNonDynamicUniforms", at = @At("HEAD"))//Am so angry ims this is what IS REQUIRED TODO, because we need to override the uniforms of dh
    private static void neovoxy$InjectMatrixUniforms(UniformHolder uniforms, IdMap idMap, PackDirectives directives, FrameUpdateNotifier updateNotifier, CallbackInfo ci) {
        if (NeoVoxyConfig.CONFIG.isRenderingEnabled() && IrisUtil.SHADER_SUPPORT) {
            NeoVoxyUniforms.addUniforms(uniforms);
        }
    }
}
