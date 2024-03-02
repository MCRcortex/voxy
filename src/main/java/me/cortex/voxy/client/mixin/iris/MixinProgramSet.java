package me.cortex.voxy.client.mixin.iris;

import me.cortex.voxy.client.compat.iris.IVoxyProgramGetters;
import net.coderbot.iris.gl.blending.BlendModeOverride;
import net.coderbot.iris.shaderpack.ProgramSet;
import net.coderbot.iris.shaderpack.ProgramSource;
import net.coderbot.iris.shaderpack.ShaderPack;
import net.coderbot.iris.shaderpack.ShaderProperties;
import net.coderbot.iris.shaderpack.include.AbsolutePackPath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

@Mixin(value = ProgramSet.class, remap = false)
public class MixinProgramSet implements IVoxyProgramGetters {
    @Shadow
    private static ProgramSource readProgramSource(AbsolutePackPath absolutePackPath, Function<AbsolutePackPath, String> function, String s, ProgramSet programSet, ShaderProperties shaderProperties){return null;};

    @Unique private ProgramSource voxyTerrain;

    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/coderbot/iris/shaderpack/ProgramSet;readProgramSource(Lnet/coderbot/iris/shaderpack/include/AbsolutePackPath;Ljava/util/function/Function;Ljava/lang/String;Lnet/coderbot/iris/shaderpack/ProgramSet;Lnet/coderbot/iris/shaderpack/ShaderProperties;)Lnet/coderbot/iris/shaderpack/ProgramSource;", ordinal = 0))
    private void injectShaderGet(AbsolutePackPath directory, Function<AbsolutePackPath, String> sourceProvider,
                                 ShaderProperties shaderProperties, ShaderPack pack, CallbackInfo ci) {
        this.voxyTerrain = readProgramSource(directory, sourceProvider, "voxy_terrain", (ProgramSet)(Object)this, shaderProperties);
    }

    @Override
    public ProgramSource getVoxyTerrainSource() {
        return this.voxyTerrain;
    }
}
