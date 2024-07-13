package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40.glDrawElementsIndirect;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;
import static org.lwjgl.opengl.GL45.nglClearNamedBufferSubData;

public class Gl46HierarchicalRenderer implements IRenderInterface {
    @Override
    public Viewport createViewport() {
        return null;
    }

    @Override
    public void setupRender(Frustum frustum, Camera camera) {

    }

    @Override
    public void renderFarAwayOpaque(Viewport viewport) {

    }

    @Override
    public void renderFarAwayTranslucent(Viewport viewport) {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void addBlockState(Mapper.StateEntry stateEntry) {

    }

    @Override
    public void addBiome(Mapper.BiomeEntry biomeEntry) {

    }

    @Override
    public boolean generateMeshlets() {
        return false;
    }

    @Override
    public void addDebugData(List debug) {

    }
}
