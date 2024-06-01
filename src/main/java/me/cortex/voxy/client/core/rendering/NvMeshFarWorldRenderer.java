package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
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
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;
import static org.lwjgl.opengl.GL31.glDrawElementsInstanced;
import static org.lwjgl.opengl.GL33.glBindSampler;
import static org.lwjgl.opengl.GL40C.GL_DRAW_INDIRECT_BUFFER;
import static org.lwjgl.opengl.GL42.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.GL42.glMemoryBarrier;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BARRIER_BIT;
import static org.lwjgl.opengl.GL43.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL45.glBindTextureUnit;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;

public class NvMeshFarWorldRenderer extends AbstractFarWorldRenderer<NvMeshViewport> {
    private final Shader terrain = Shader.make()
            .add(ShaderType.TASK, "voxy:lod/nvmesh/primary.task")
            .add(ShaderType.MESH, "voxy:lod/nvmesh/primary.mesh")
            .add(ShaderType.FRAGMENT, "voxy:lod/nvmesh/primary.frag")
            .compile();

    private final Shader translucent = Shader.make()
            .add(ShaderType.TASK, "voxy:lod/nvmesh/translucent.task")
            .add(ShaderType.MESH, "voxy:lod/nvmesh/translucent.mesh")
            .add(ShaderType.FRAGMENT, "voxy:lod/nvmesh/primary.frag")
            .compile();

    private final Shader cull = Shader.make()
            .add(ShaderType.VERTEX, "voxy:lod/nvmesh/cull.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/nvmesh/cull.frag")
            .compile();

    public NvMeshFarWorldRenderer(int geometrySize, int maxSections) {
        super(geometrySize, maxSections);
    }


    private void updateUniform(NvMeshViewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, this.uniformBuffer.size());

        var mat = new Matrix4f(viewport.projection).mul(viewport.modelView);
        mat.getToAddress(ptr); ptr += 4*4*4;
        var innerTranslation = new Vector3f((float) (viewport.cameraX-(this.sx<<5)), (float) (viewport.cameraY-(this.sy<<5)), (float) (viewport.cameraZ-(this.sz<<5)));
        MemoryUtil.memPutInt(ptr, this.sx); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sy); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.sz); ptr += 4;
        MemoryUtil.memPutInt(ptr, this.geometry.getSectionCount()); ptr += 4;
        innerTranslation.getToAddress(ptr); ptr += 4*3;
        MemoryUtil.memPutInt(ptr, viewport.frameId++); ptr += 4;
    }

    private void bindResources(NvMeshViewport viewport) {
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE.id());
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometry.geometryId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, this.geometry.metaId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, viewport.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, this.models.getBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, this.models.getColourBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.lightDataBuffer.id);//Lighting LUT

        //Bind the texture atlas
        glBindSampler(0, this.models.getSamplerId());
        glBindTextureUnit(0, this.models.getTextureId());
    }

    @Override
    public void renderFarAwayOpaque(NvMeshViewport viewport) {
        {//TODO: move all this code into a common super method renderFarAwayTranslucent and make the current method renderFarAwayTranslucent0
            if (this.geometry.getSectionCount() == 0) {
                return;
            }

            {//Mark all of the updated sections as being visible from last frame
                for (int id : this.updatedSectionIds) {
                    long ptr = UploadStream.INSTANCE.upload(viewport.visibilityBuffer, id * 4L, 4);
                    MemoryUtil.memPutInt(ptr, viewport.frameId - 1);//(visible from last frame)
                }
            }
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);

        //Update and upload data
        this.updateUniform(viewport);
        UploadStream.INSTANCE.commit();


        this.terrain.bind();

        RenderLayer.getCutoutMipped().startDrawing();

        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);
        this.bindResources(viewport);

        glDisable(GL_CULL_FACE);
        glDrawMeshTasksNV(0, this.geometry.getSectionCount());
        glEnable(GL_CULL_FACE);

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_SHADER_STORAGE_BARRIER_BIT);

        this.cull.bind();
        this.bindResources(viewport);
        glColorMask(false, false, false, false);
        glDepthMask(false);
        glEnable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glDrawElementsInstanced(GL_TRIANGLES, 6 * 2 * 3, GL_UNSIGNED_BYTE, (1 << 16) * 6 * 2, this.geometry.getSectionCount());
        glDisable(GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        glDepthMask(true);
        glColorMask(true, true, true, true);

        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        RenderLayer.getCutoutMipped().endDrawing();
    }

    @Override
    public void renderFarAwayTranslucent(NvMeshViewport viewport) {
        if (this.geometry.getSectionCount()==0) {
            return;
        }
        RenderLayer.getTranslucent().startDrawing();
        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);

        //TODO: maybe change this so the alpha isnt applied in the same way or something?? since atm the texture bakery uses a very hacky
        // blend equation to make it avoid double applying translucency
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);


        glBindSampler(0, this.models.getSamplerId());
        glBindTextureUnit(0, this.models.getTextureId());

        this.translucent.bind();
        this.bindResources(viewport);

        glDrawMeshTasksNV(0, this.geometry.getSectionCount());

        glEnable(GL_CULL_FACE);
        glBindVertexArray(0);


        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        glDisable(GL_BLEND);

        RenderLayer.getTranslucent().endDrawing();
    }

    @Override
    protected NvMeshViewport createViewport0() {
        return new NvMeshViewport(this);
    }


    @Override
    public void shutdown() {
        super.shutdown();
        this.terrain.free();
        this.translucent.free();
        this.cull.free();
    }
}
