package me.cortex.voxy.client.mixin;

import me.cortex.voxy.common.NeoForgePlatform;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClientVoxyMixinPlugin implements IMixinConfigPlugin {
    private static boolean valkyrienSkiesInstalled;
    private static boolean nvidiumInstalled;
    private static boolean connectorInstalled;
    private static boolean irisInstalled;

    @Override
    public void onLoad(String mixinPackage) {
        valkyrienSkiesInstalled = isModLoaded("valkyrienskies");
        nvidiumInstalled = isModLoaded("nvidium");
        connectorInstalled = isModLoaded("connector");
        irisInstalled = isModLoaded("iris");
    }

    private static boolean isModLoaded(String id) {
        try {
            return NeoForgePlatform.isModLoaded(id);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }

    @Override public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        if (valkyrienSkiesInstalled && !nvidiumInstalled) {
            mixins.add("sodium.MixinSodiumWorldRendererVS");
        } else {
            mixins.add("sodium.MixinDefaultChunkRenderer");
        }

        if (irisInstalled) {
            mixins.add("iris.CustomUniformsAccessor");
            mixins.add("iris.IrisRenderingPipelineAccessor");
            mixins.add("iris.MixinIris");
            mixins.add("iris.MixinIrisRenderingPipeline");
            mixins.add("iris.MixinIrisSamplers");
            mixins.add("iris.MixinLevelRenderer");
            mixins.add("iris.MixinMatrixUniforms");
            mixins.add("iris.MixinPackRenderTargetDirectives");
            mixins.add("iris.MixinProgramSet");
            mixins.add("iris.MixinShaderPackSourceNames");
            mixins.add("iris.MixinStandardMacros");
        }

        return mixins;
    }

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}