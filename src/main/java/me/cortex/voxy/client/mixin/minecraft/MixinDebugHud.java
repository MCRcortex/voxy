package me.cortex.voxy.client.mixin.minecraft;

import me.cortex.voxy.client.core.IGetVoxelCore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class MixinDebugHud {
    @Inject(method = "getRightText", at = @At("TAIL"))
    private void injectDebug(CallbackInfoReturnable<List<String>> cir) {
        var ret = cir.getReturnValue();
        var core = ((IGetVoxelCore) MinecraftClient.getInstance().worldRenderer).getVoxelCore();
        if (core != null) {
            core.addDebugInfo(ret);
        }
    }
}
