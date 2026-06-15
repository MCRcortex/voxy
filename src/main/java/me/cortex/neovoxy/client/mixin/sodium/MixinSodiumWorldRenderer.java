package me.cortex.neovoxy.client.mixin.sodium;

import me.cortex.neovoxy.commonImpl.NeoVoxyCommon;
import me.cortex.neovoxy.commonImpl.NeoVoxyInstance;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class MixinSodiumWorldRenderer {
    @Inject(method = "initRenderer", at = @At("TAIL"), remap = false)
    private void neovoxy$injectThreadUpdate(CommandList cl, CallbackInfo ci) {
        var vi = NeoVoxyCommon.getInstance();
        if (vi != null) vi.updateDedicatedThreads();
    }
}
