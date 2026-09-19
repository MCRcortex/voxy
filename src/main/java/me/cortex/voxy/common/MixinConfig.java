package me.cortex.voxy.common;

import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

import java.util.Set;

public class MixinConfig implements IMixinConfigPlugin {
    private String mixinPackage;
    private final Set<String> fallThrough;

    public MixinConfig() {
        this.fallThrough = Set.of();
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage + ".";//we want to get rid of the .
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixin) {
        if (mixin.startsWith(this.mixinPackage)) {
            var pth = mixin.substring(this.mixinPackage.length());
            if (!pth.contains(".")) return true;
            var targetMod = pth.substring(0, pth.indexOf('.'));
            //filter out fallthrough and if the mod is actually loaded
            if (this.fallThrough.contains(targetMod)) return true;
            if (FabricLoader.getInstance().isModLoaded(targetMod)) return true;
            //need to remove it cause it shouldnt be applied
            return false;
        }
        return true;
    }
}
