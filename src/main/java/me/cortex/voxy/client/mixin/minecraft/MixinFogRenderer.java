package me.cortex.voxy.client.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FogRenderer.class,remap = true)
public class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void voxy$modifyFog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (!VoxyConfig.CONFIG.isRenderingEnabled()) return;

        var vrs = IGetVoxyRenderSystem.getNullable();
        if (vrs == null) return;
        var data = cir.getReturnValue();
        boolean fogIsDamnClose = data.environmentalEnd<10;
        if (!VoxyConfig.CONFIG.useEnvironmentalFog && !fogIsDamnClose) {
            data.environmentalStart = 99999999;
            data.environmentalEnd = 99999999;
        }

        data.renderDistanceStart = 999999999;
        data.renderDistanceEnd = 999999999;
    }
}
