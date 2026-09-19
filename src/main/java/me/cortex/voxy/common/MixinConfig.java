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
    public void acceptTargets(Set<String> self, Set<String> other) {
        var iter = self.iterator();
        while (iter.hasNext()) {
            var mixin = iter.next();
            if (mixin.startsWith(this.mixinPackage)) {
                var pth = mixin.substring(this.mixinPackage.length());
                if (!pth.contains(".")) continue;
                var targetMod = pth.substring(0, pth.indexOf('.'));
                //filter out fallthrough and if the mod is actually loaded
                if (this.fallThrough.contains(targetMod)) continue;
                if (FabricLoader.getInstance().isModLoaded(targetMod)) continue;
                //need to remove it cause it shouldnt be applied
                iter.remove();
            }
        }
    }
}
