package me.cortex.voxy.client.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cortex.voxy.client.config.VoxyConfig;
import net.caffeinemc.mods.sodium.client.render.immediate.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {CloudRenderer.class}, remap = false, priority = 1100)
public class MixinCloudRenderer {
    @WrapMethod(method = {"getCloudRenderDistance"})
    private static int voxy$cloudRenderDistance(Operation<Integer> original) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled())
            return original.call();
        if (VoxyConfig.CONFIG.adaptCloudDistance) {
            return Math.clamp((int)(VoxyConfig.CONFIG.sectionRenderDistance * 32F) + 9, original.call(), 265);
        }
        return VoxyConfig.CONFIG.cloudDistance < 1 ? original.call() : VoxyConfig.CONFIG.cloudDistance + 9;
    }
}
