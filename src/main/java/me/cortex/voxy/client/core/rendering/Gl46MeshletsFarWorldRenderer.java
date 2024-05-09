package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.building.RenderDataFactory;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetNamedFramebufferAttachmentParameteriv;
import static org.lwjgl.opengl.ARBDirectStateAccess.glTextureParameteri;
import static org.lwjgl.opengl.ARBIndirectParameters.GL_PARAMETER_BUFFER_ARB;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
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
import static org.lwjgl.opengl.GL45C.nglClearNamedBufferData;
import static org.lwjgl.opengl.NVMeshShader.glDrawMeshTasksNV;
import static org.lwjgl.opengl.NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV;

//TODO: NOTE
// can do "meshlet compaction"
// that is, meshlets are refered not by the meshlet id but by an 8 byte alligned index, (the quad index)
// this means that non full meshlets can have the tailing data be truncated and used in the next meshlet
// as long as the number of quads in the meshlet is stored in the header
// the shader can cull the verticies of any quad that has its index over the expected quuad count
// this could potentially result in a fair bit of memory savings (especially if used in normal mc terrain rendering)
public class Gl46MeshletsFarWorldRenderer extends AbstractFarWorldRenderer<Gl46MeshletViewport, DefaultGeometryManager> {
    private final Shader lodShader = Shader.make()
            .define("QUADS_PER_MESHLET", RenderDataFactory.QUADS_PER_MESHLET)
            .add(ShaderType.VERTEX, "voxy:lod/gl46mesh/quads.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46mesh/quads.frag")
            .compile();

    private final Shader cullShader = Shader.make()
            .define("QUADS_PER_MESHLET", RenderDataFactory.QUADS_PER_MESHLET)
            .add(ShaderType.VERTEX, "voxy:lod/gl46mesh/cull.vert")
            .add(ShaderType.FRAGMENT, "voxy:lod/gl46mesh/cull.frag")
            .compile();

    private final Shader meshletGenerator = Shader.make()
            .define("QUADS_PER_MESHLET", RenderDataFactory.QUADS_PER_MESHLET)
            .add(ShaderType.COMPUTE, "voxy:lod/gl46mesh/cmdgen.comp")
            .compile();

    private final Shader meshletCuller = Shader.make()
            .define("QUADS_PER_MESHLET", RenderDataFactory.QUADS_PER_MESHLET)
            .add(ShaderType.COMPUTE, "voxy:lod/gl46mesh/meshletculler.comp")
            .compile();

    private final GlBuffer glDrawIndirect;
    private final GlBuffer meshletBuffer;
    private final HiZBuffer hiZBuffer = new HiZBuffer();
    private final int hizSampler = glGenSamplers();

    public Gl46MeshletsFarWorldRenderer(int geometrySize, int maxSections) {
        super(new DefaultGeometryManager(alignUp(geometrySize*8L, 8* (RenderDataFactory.QUADS_PER_MESHLET+2)), maxSections, 8*(RenderDataFactory.QUADS_PER_MESHLET+2)));
        this.glDrawIndirect = new GlBuffer(4*(4+5));
        this.meshletBuffer = new GlBuffer(4*1000000);//TODO: Make max meshlet count configurable, not just 1 million (even tho thats a max of 126 million quads per frame)

        glSamplerParameteri(this.hizSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_NEAREST);//This is so that using the shadow sampler works correctly
        glTextureParameteri(this.hizSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(this.hizSampler, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTextureParameteri(this.hizSampler, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        glTextureParameteri(this.hizSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(this.hizSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        nglClearNamedBufferData(this.meshletBuffer.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
        nglClearNamedBufferData(this.glDrawIndirect.id, GL_R32UI, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);
    }

    protected void bindResources(Gl46MeshletViewport viewport, boolean bindToDrawIndirect, boolean bindToDispatchIndirect, boolean bindHiz) {
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, this.uniformBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, this.geometry.geometryId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 2, bindToDrawIndirect?0:this.glDrawIndirect.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 3, this.meshletBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 4, this.geometry.metaId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 5, viewport.visibilityBuffer.id);
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 6, this.models.getBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 7, this.models.getColourBufferId());
        glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 8, this.lightDataBuffer.id);//Lighting LUT
        glBindBuffer(GL_DRAW_INDIRECT_BUFFER, bindToDrawIndirect?this.glDrawIndirect.id:0);
        glBindBuffer(GL_DISPATCH_INDIRECT_BUFFER, bindToDispatchIndirect?this.glDrawIndirect.id:0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BYTE.id());

        if (!bindHiz) {
            //Bind the texture atlas
            glBindSampler(0, this.models.getSamplerId());
            glBindTextureUnit(0, this.models.getTextureId());
        } else {
            //Bind the hiz buffer
            glBindSampler(0, this.hizSampler);
            glBindTextureUnit(0, this.hiZBuffer.getHizTextureId());
        }
    }

    private void updateUniformBuffer(Gl46MeshletViewport viewport) {
        long ptr = UploadStream.INSTANCE.upload(this.uniformBuffer, 0, this.uniformBuffer.size());

        //TODO:FIXME remove this.sx/sy/sz and make it based on the viewport!!!

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
        MemoryUtil.memPutInt(ptr, viewport.width); ptr += 4;
        MemoryUtil.memPutInt(ptr, viewport.height); ptr += 4;
    }

    @Override
    public void renderFarAwayOpaque(Gl46MeshletViewport viewport) {
        if (this.geometry.getSectionCount() == 0) {
            return;
        }

        {//Mark all of the updated sections as being visible from last frame
            for (int id : this.updatedSectionIds) {
                long ptr = UploadStream.INSTANCE.upload(viewport.visibilityBuffer, id * 4L, 4);
                MemoryUtil.memPutInt(ptr, viewport.frameId - 1);//(visible from last frame)
            }
        }

        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        RenderLayer.getCutoutMipped().startDrawing();

        this.updateUniformBuffer(viewport);
        UploadStream.INSTANCE.commit();
        glBindVertexArray(AbstractFarWorldRenderer.STATIC_VAO);

        nglClearNamedBufferSubData(this.glDrawIndirect.id, GL_R32UI, 0, 4*4, GL_RED_INTEGER, GL_UNSIGNED_INT, 0);

        this.lodShader.bind();
        this.bindResources(viewport, true, false, false);
        glDisable(GL_CULL_FACE);
        glDrawElementsIndirect(GL_TRIANGLES, GL_UNSIGNED_BYTE, 4*4);
        glEnable(GL_CULL_FACE);

        glMemoryBarrier(GL_PIXEL_BUFFER_BARRIER_BIT | GL_FRAMEBUFFER_BARRIER_BIT);

        this.cullShader.bind();
        this.bindResources(viewport, false, false, false);
        glDepthMask(false);
        glColorMask(false, false, false, false);
        glDrawElementsInstanced(GL_TRIANGLES, 6 * 2 * 3, GL_UNSIGNED_BYTE, (1 << 8) * 6, this.geometry.getSectionCount());
        glColorMask(true, true, true, true);
        glDepthMask(true);

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT);

        this.meshletGenerator.bind();
        this.bindResources(viewport, false, false, false);
        glDispatchCompute((this.geometry.getSectionCount()+63)/64, 1, 1);

        glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_COMMAND_BARRIER_BIT);

        var i = new int[1];
        glGetFramebufferAttachmentParameteriv(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, i);
        this.hiZBuffer.buildMipChain(i[0], MinecraftClient.getInstance().getFramebuffer().textureWidth, MinecraftClient.getInstance().getFramebuffer().textureHeight);

        this.meshletCuller.bind();
        this.bindResources(viewport, false, true, true);
        glDispatchComputeIndirect(0);



        glBindVertexArray(0);
        glBindSampler(0, 0);
        glBindTextureUnit(0, 0);
        RenderLayer.getCutoutMipped().endDrawing();
    }


    @Override
    public void renderFarAwayTranslucent(Gl46MeshletViewport viewport) {

    }

    @Override
    protected Gl46MeshletViewport createViewport0() {
        return new Gl46MeshletViewport(this);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        this.lodShader.free();
        this.cullShader.free();
        this.meshletGenerator.free();
        this.meshletCuller.free();

        this.glDrawIndirect.free();
        this.meshletBuffer.free();

        this.hiZBuffer.free();
        glDeleteSamplers(this.hizSampler);
    }

    public static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    @Override
    public boolean usesMeshlets() {
        return true;
    }
}
