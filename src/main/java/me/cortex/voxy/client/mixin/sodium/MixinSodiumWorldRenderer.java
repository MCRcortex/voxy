package me.cortex.voxy.client.mixin.sodium;

import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRenderer {
    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private void voxy$injectThreadUpdate(CommandList cl, CallbackInfo ci) {
        var vi = VoxyCommon.getInstance();
        if (vi != null) vi.updateDedicatedThreads();
    }
}
