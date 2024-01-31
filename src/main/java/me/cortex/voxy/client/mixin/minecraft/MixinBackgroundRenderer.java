package me.cortex.voxy.client.mixin.minecraft;

import net.minecraft.client.render.BackgroundRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BackgroundRenderer.class)
public class MixinBackgroundRenderer {
    @ModifyConstant(method = "applyFog", constant = @Constant(floatValue = 192.0F), require = 0)
    private static float changeFog(float fog) {
        return 9999999f;
    }
}
