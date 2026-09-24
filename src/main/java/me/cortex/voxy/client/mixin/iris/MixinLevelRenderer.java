package me.cortex.voxy.client.mixin.iris;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import me.cortex.voxy.client.core.IVoxyRenderSystemHolder;
import me.cortex.voxy.client.core.util.IrisUtil;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.util.GameRendererStorage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.opengl.GL11C.glViewport;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Shadow
    @Final
    private GameRenderer gameRenderer;

    @Inject(method = "render", at = @At("HEAD"), order = 100)
    private void voxy$injectIrisCompat(GraphicsResourceAllocator resourceAllocator, boolean renderOutline, CameraRenderState cameraState, GpuBufferSlice terrainFog, Vector4f fogColor, boolean shouldRenderSky, boolean flag2, CallbackInfo ci) {
        if (IrisUtil.irisShaderPackEnabled()) {
            var renderer = IVoxyRenderSystemHolder.getNullableHolder();
            if (renderer != null) {
                //Fixthe fucking viewport dims, fuck iris
                glViewport(0,0, Minecraft.getInstance().gameRenderer.mainRenderTarget().width, Minecraft.getInstance().gameRenderer.mainRenderTarget().height);

                var pos = cameraState.pos;
                IrisUtil.CAPTURED_VIEWPORT_PARAMETERS = new IrisUtil.CapturedViewportParameters(new ChunkRenderMatrices(cameraState.projectionMatrix, cameraState.viewRotationMatrix), ((GameRendererStorage)this.gameRenderer).sodium$getFogParameters(), Minecraft.getInstance().gameRenderer.mainRenderTarget().width, Minecraft.getInstance().gameRenderer.mainRenderTarget().height, pos.x, pos.y, pos.z);
            }
        }
    }
}
