package me.cortex.voxy.client.mixin.minecraft;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.cortex.voxy.client.core.interop.IOSurfaceBridgeDemo;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * IOSurface bridge demo wiring (M10/M11). Gated on
 * {@code -Dvoxy.bridgeDemo=true}. Per-frame after MC has finished
 * rendering the world, this mixin asks the demo to paint the next
 * frame on the Metal side and blits the IOSurface-backed GL texture
 * into a small corner of the screen so the user can confirm the
 * cross-API zero-copy bridge works inside an actual MC context.
 *
 * If the property isn't set, the demo's static methods short-circuit
 * and this mixin is effectively dormant — no GL work, no Metal work.
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRendererBridgeDemo {

    @Inject(method = "renderLevel", at = @At("RETURN"), order = 9000)
    private void voxy$bridgeDemo(
            GraphicsResourceAllocator allocator,
            DeltaTracker tickCounter,
            boolean renderBlockOutline,
            Camera camera,
            Matrix4f positionMatrix,
            Matrix4f projectionMatrix,
            Matrix4f basicProjectionMatrix,
            GpuBufferSlice fogBuffer,
            Vector4f fogColor,
            boolean renderSky,
            CallbackInfo ci) {
        if (!IOSurfaceBridgeDemo.ENABLED) return;
        IOSurfaceBridgeDemo.tickAndPaint();
        if (!IOSurfaceBridgeDemo.isReady()) return;
        var mc = Minecraft.getInstance();
        int fbw = mc.getMainRenderTarget().width;
        int fbh = mc.getMainRenderTarget().height;
        // Place the 256x256 demo overlay in the bottom-right corner so it
        // doesn't obscure crosshair / UI.
        int margin = 16;
        int x1 = fbw - margin;
        int x0 = x1 - IOSurfaceBridgeDemo.width();
        int y0 = margin;
        int y1 = y0 + IOSurfaceBridgeDemo.height();
        IOSurfaceBridgeDemo.blitToBoundFramebuffer(x0, y0, x1, y1);
    }
}
