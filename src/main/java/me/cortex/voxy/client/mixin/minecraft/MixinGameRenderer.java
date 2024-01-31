package me.cortex.voxy.client.mixin.minecraft;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Inject(method = "getFarPlaneDistance", at = @At("HEAD"), cancellable = true, require = 0)
    public void method_32796(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(16 * 3000f);
        cir.cancel();
    }
}