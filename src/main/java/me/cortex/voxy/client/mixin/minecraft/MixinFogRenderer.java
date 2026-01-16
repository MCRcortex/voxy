package me.cortex.voxy.client.mixin.minecraft;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.sugar.Local;
import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.FogRenderer.FogMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

@Mixin(value = FogRenderer.class,remap = true)
public class MixinFogRenderer {
    @Inject(
            method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V",
            at = @At("TAIL"),
            cancellable = true
    )
    private static void voxy$overrideFog(
            Camera camera,
            FogMode fogMode,
            float viewDistance,
            boolean thickFog,
            float tickDelta,
            CallbackInfo ci
    ) {
        var vrs = (IGetVoxyRenderSystem) Minecraft.getInstance().levelRenderer;

        if (VoxyConfig.CONFIG.renderVanillaFog || vrs == null || vrs.getVoxyRenderSystem() == null) {
            RenderSystem.setShaderFogEnd(viewDistance);
        } else {
            RenderSystem.setShaderFogStart(999999999);
            RenderSystem.setShaderFogEnd(999999999);
        }
    }
}