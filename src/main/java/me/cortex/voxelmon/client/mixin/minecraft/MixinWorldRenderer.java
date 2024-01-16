package me.cortex.voxelmon.client.mixin.minecraft;

import me.cortex.voxelmon.client.IGetVoxelCore;
import me.cortex.voxelmon.client.core.VoxelCore;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer implements IGetVoxelCore {
    @Shadow protected abstract void renderLayer(RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix);

    @Shadow protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator);

    @Shadow private Frustum frustum;

    @Shadow private @Nullable ClientWorld world;
    @Unique private VoxelCore core;

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", shift = At.Shift.AFTER))
    private void injectSetup(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        this.core.renderSetup(this.frustum, camera);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V", ordinal = 2, shift = At.Shift.AFTER))
    private void injectOpaqueRender(MatrixStack matrices, float tickDelta, long limitTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix, CallbackInfo ci) {
        var cam = camera.getPos();
        this.core.renderOpaque(matrices, cam.x, cam.y, cam.z);
    }

    public VoxelCore getVoxelCore() {
        return this.core;
    }

    @Inject(method = "reload()V", at = @At("TAIL"))
    private void resetVoxelCore(CallbackInfo ci) {
        if (this.world != null && this.core != null) {
            this.core.shutdown();
            this.core = new VoxelCore();
        }
    }

    @Inject(method = "setWorld", at = @At("TAIL"))
    private void initVoxelCore(ClientWorld world, CallbackInfo ci) {
        if (world == null) {
            if (this.core != null) {
                this.core.shutdown();
                this.core = null;
            }
            return;
        }
        if (this.core != null) {
            throw new IllegalStateException("Core is not null");
        }
        this.core = new VoxelCore();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void injectClose(CallbackInfo ci) {
        if (this.core != null) {
            this.core.shutdown();
            this.core = null;
        }
    }



    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"), require = 0)
    private float redirectMax(float a, float b) {
        return a;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;getViewDistance()F"), require = 0)
    private float changeRD(GameRenderer instance) {
        float viewDistance = instance.getViewDistance();
        return 16*1512;
    }
}
