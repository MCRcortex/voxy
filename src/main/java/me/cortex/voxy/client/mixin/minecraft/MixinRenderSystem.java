package me.cortex.voxy.client.mixin.minecraft;


import com.mojang.blaze3d.systems.RenderSystem;
import me.cortex.voxy.client.VoxyClient;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

//Thanks iris for making me need todo this ;-; _irritater_
@Mixin(RenderSystem.class)
public class MixinRenderSystem {
    //We need to inject before iris to initalize our systems
    @Inject(method = "initRenderer", order = 900, remap = false, at = @At("RETURN"))
    private static void voxy$injectInit(int debugVerbosity, boolean sync, CallbackInfo ci) {
        VoxyClient.initVoxyClient();
    }
}
