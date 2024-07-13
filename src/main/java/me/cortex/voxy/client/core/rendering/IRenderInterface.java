package me.cortex.voxy.client.core.rendering;


import me.cortex.voxy.client.core.rendering.hierarchical.HierarchicalOcclusionRenderer;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;

import java.util.List;

import static org.lwjgl.opengl.GL30.*;

public interface IRenderInterface <T extends Viewport> {

    T createViewport();

    void setupRender(Frustum frustum, Camera camera);

    void renderFarAwayOpaque(T viewport);

    void renderFarAwayTranslucent(T viewport);

    void addDebugData(List<String> debug);

    void shutdown();

    void addBlockState(Mapper.StateEntry stateEntry);

    void addBiome(Mapper.BiomeEntry biomeEntry);

    boolean generateMeshlets();
}
