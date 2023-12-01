package me.cortex.voxelmon.mixin.minecraft;

import me.cortex.voxelmon.Voxelmon;
import me.cortex.voxelmon.core.VoxelCore;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer {
    @Shadow protected abstract void renderLayer(RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix);

    @Shadow protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator);

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V"))
    private void injectSetup(WorldRenderer instance, Camera camera, Frustum frustum, boolean hasForcedFrustum, boolean spectator) {
        //Call the actual terrain setup method
        this.setupTerrain(camera, frustum, hasForcedFrustum, spectator);
        //Call our setup method
        VoxelCore.INSTANCE.renderSetup(frustum, camera);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V", ordinal = 2))
    private void injectOpaqueRender(WorldRenderer instance, RenderLayer renderLayer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ, Matrix4f positionMatrix) {
        //Call the actual render method
        this.renderLayer(renderLayer, matrices, cameraX, cameraY, cameraZ, positionMatrix);
        VoxelCore.INSTANCE.renderOpaque(matrices, cameraX, cameraY, cameraZ);
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
