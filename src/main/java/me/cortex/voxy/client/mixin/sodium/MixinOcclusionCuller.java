package me.cortex.voxy.client.mixin.sodium;

import me.jellysquid.mods.sodium.client.render.chunk.occlusion.OcclusionCuller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = OcclusionCuller.class, remap = false)
public class MixinOcclusionCuller {
    @Redirect(method = "isOutsideRenderDistance", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(F)F"))
    private static float redirectAbs(float a) {
        return 0;
    }
}
