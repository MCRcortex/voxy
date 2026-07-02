package me.cortex.voxy.client.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cortex.voxy.client.config.VoxyConfig;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {GameRenderer.class}, priority = 1100)
public class MixinGameRenderer {
    @WrapMethod(method = "getDepthFar()F")
    public float getDepthFar(Operation<Float> original) {
        if (VoxyConfig.CONFIG.isRenderingEnabled()) {
            return Math.max(original.call(), VoxyConfig.CONFIG.sectionRenderDistance * 32F * 4F);
        }
        return original.call();
    }
}
