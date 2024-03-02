package me.cortex.voxy.client.core.rendering.shaders;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.AbstractFarWorldRenderer;
import me.cortex.voxy.client.core.rendering.Gl46Viewport;
import me.cortex.voxy.client.core.rendering.SharedIndexBuffer;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.ARBIndirectParameters.glMultiDrawElementsIndirectCountARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.GL_R8UI;
import static org.lwjgl.opengl.GL30C.GL_RED_INTEGER;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_BLEND;
import static org.lwjgl.opengl.GL42.GL_CULL_FACE;
import static org.lwjgl.opengl.GL42.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL42.GL_ONE;
import static org.lwjgl.opengl.GL42.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL42.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL42.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL42.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL42.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL42.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL42.glBindTexture;
import static org.lwjgl.opengl.GL42.glColorMask;
import static org.lwjgl.opengl.GL42.glDepthMask;
import static org.lwjgl.opengl.GL42.glDisable;
import static org.lwjgl.opengl.GL42.glEnable;
import static org.lwjgl.opengl.GL42.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45C.glClearNamedBufferData;

public class Gl46ShaderWorldRenderer extends AbstractFarWorldRenderer<Gl46Viewport> {
    private final Shader commandGen = Shader.make()
            .add(ShaderType.COMPUTE, "voxy:lod/gl46/cmdgen.comp")
            .compile();
    private final Shader cullShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/gl46/cull/raster.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46/cull/raster.frag")
            .compile();

    private final GlBuffer glCommandBuffer;
    private final GlBuffer glCommandCountBuffer;

    public Gl46ShaderWorldRenderer(int geometryBuffer, int maxSections) {
        super(geometryBuffer, maxSections);
        this.glCommandBuffer = new GlBuffer(maxSections*5L*4 * 6);
        this.glCommandCountBuffer = new GlBuffer(4*2);
        glClearNamedBufferData(this.glCommandBuffer.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, new int[1]);
        setupVao();
    }

    @Override
    protected void setupVao() {
        glBindVertexArray(this.vao);
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, this.glCommandBuffer.id);
        glBindBuffer(GL_PARAMETER_BUFFER_ARB, this.glCommandCountBuffer.id);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometry.geometryId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.glCommandBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.glCommandCountBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, this.geometry.metaId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.models.getBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, this.models.getColourBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, this.lightDataBuffer.id);//Lighting LUT
        glBindVertexArray(0);
    }

    private void updateUniformBuffer(Gl46Viewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, this.uniformBuffer.size());

        var mat = new Matrix4f(viewport.projection).mul(viewport.modelView);
        var innerTranslation = new Vector3f((float) (viewport.cameraX-(this.sx<<5)), (float) (viewport.cameraY-(this.sy<<5)), (float) (viewport.cameraZ-(this.sz<<5)));
        mat.translate(-innerTranslation.x, -innerTranslation.y, -innerTranslation.z);
        mat.getToAddress(ptr); ptr += 4*4*4;
        MemoryUtil.memPutInt(ptr, this.sx); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sy); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sz); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.geometry.getSectionCount()); ptr += 4;
        var planes = ((AccessFrustumIntersection)this.frustum).getPlanes();
        for (var plane : planes) {
            plane.getToAddress(ptr); ptr += 4*4;
        }
        innerTranslation.getToAddress(ptr); ptr += 4*3;
        MemoryUtil.memPutInt(ptr, viewport.frameId++); ptr += 4;
    }

    public void renderFarAwayOpaque(Gl46Viewport viewport) {
    }

    @Override
    public void renderFarAwayTranslucent() {
    }

    @Override
    public Gl46Viewport createViewport() {
        return new Gl46Viewport(this.maxSections);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.commandGen.free();
        this.cullShader.free();
        this.glCommandBuffer.free();
        this.glCommandCountBuffer.free();
    }

    @Override
    public void addDebugData(List<String> debug) {
        super.addDebugData(debug);
        debug.add("Geometry buffer usage: " + ((float)Math.round((this.geometry.getGeometryBufferUsage()*100000))/1000) + "%");
        debug.add("Render Sections: " + this.geometry.getSectionCount());
    }
}
