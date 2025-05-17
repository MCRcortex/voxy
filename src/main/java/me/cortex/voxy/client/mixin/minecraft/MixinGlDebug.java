package me.cortex.voxy.client.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gl.GlDebug;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

@Mixin(GlDebug.class)
public class MixinGlDebug {
    @WrapOperation(method = "onDebugMessage", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"))
    private void voxy$wrapDebug(Logger instance, String base, Object msgObj, Operation<Void> original) {
        if (msgObj instanceof GlDebug.DebugMessage msg) {
            var throwable = new Throwable(msg.toString());
            if (isCausedByVoxy(throwable.getStackTrace())) {
                original.call(instance, base, throwable);
            } else {
                original.call(instance, base, msg);
            }
        } else {
            original.call(instance, base, msgObj);
        }
    }

    @Unique
    private boolean isCausedByVoxy(StackTraceElement[] trace) {
        for (var elem : trace) {
            if (elem.getClassName().startsWith("me.cortex.voxy")) {
                return true;
            }
        }
        return false;
    }
}
