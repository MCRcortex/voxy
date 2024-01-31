package me.cortex.voxy.client.mixin.joml;

import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = FrustumIntersection.class, remap = false)
public interface AccessFrustumIntersection {
    @Accessor Vector4f[] getPlanes();
}
