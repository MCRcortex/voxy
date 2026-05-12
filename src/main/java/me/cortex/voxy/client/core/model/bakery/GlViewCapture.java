package me.cortex.voxy.client.core.model.bakery;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.gpu.IGpuFramebuffer;
import me.cortex.voxy.client.core.gpu.IGpuTexture;
import me.cortex.voxy.client.core.gpu.RenderBackendFactory;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.ARBDirectStateAccess.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_PIXEL_BUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_TEXTURE_UPDATE_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glMemoryBarrier;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL43.*;
import static org.lwjgl.opengl.GL45.glClearNamedFramebufferfi;

/**
 * Model texture bakery capture target — OpenGL-only.
 *
 * Although resources (framebuffer, textures) are created via the GPU abstraction,
 * this class issues direct OpenGL DSA calls (glClearNamedFramebufferfi,
 * glBindBufferRange, glDispatchCompute, glMemoryBarrier) that are not portable
 * to Metal. A parallel MetalViewCapture implementation with blit/compute
 * encoders would be required for the Metal backend.
 *
 * For now, callers on the Metal backend must use an alternative path or stub
 * this subsystem out.
 */
public class GlViewCapture {
    private final int width;
    private final int height;
    private final IGpuTexture colourTex;
    private final IGpuTexture depthTex;
    private final IGpuTexture stencilTex;
    private final IGpuTexture metaTex;
    final IGpuFramebuffer framebuffer;
    private final Shader copyOutShader;

    public GlViewCapture(int width, int height) {
        this.width = width;
        this.height = height;
        // M11 transitional: GlViewCapture's constructor allocates textures + a
        // legacy AutoBindingShader. On non-OpenGL backends the
        // AutoBindingShader.compile path returns a program=0 stub, and the
        // subsequent .texture("BINDING", 0, tex) calls would inspect that
        // program's resource indices — NPE territory. Skip allocation entirely
        // on non-OpenGL; emitToStream() + clear() already early-return so the
        // bakery is silent on Metal until ModelTextureBakery itself gets a
        // proper Metal-native replacement (parallel MetalViewCapture). This
        // unblocks VoxyRenderSystem construction so the higher-level Metal
        // render path can boot.
        if (RenderBackendFactory.get().getType() != me.cortex.voxy.client.core.gpu.BackendType.OPENGL) {
            this.metaTex = null;
            this.colourTex = null;
            this.depthTex = null;
            this.stencilTex = null;
            this.framebuffer = null;
            this.copyOutShader = null;
            return;
        }
        this.metaTex = RenderBackendFactory.get().createTexture().store(GL_R32UI, 1, width*3, height*2).name("ModelBakeryMetadata");
        this.colourTex = RenderBackendFactory.get().createTexture().store(GL_RGBA8, 1, width*3, height*2).name("ModelBakeryColour");
        this.depthTex = RenderBackendFactory.get().createTexture().store(GL_DEPTH24_STENCIL8, 1, width*3, height*2).name("ModelBakeryDepth");
        //TODO: FIXME: Mesa is broken when trying to read from a sampler of GL_STENCIL_INDEX
        // it seems to just ignore the value set in GL_DEPTH_STENCIL_TEXTURE_MODE
        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.depthTex.id(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        this.stencilTex = this.depthTex.createView();
        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.depthTex.id(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);

        this.framebuffer = RenderBackendFactory.get().createFramebuffer().bind(GL_COLOR_ATTACHMENT0, this.colourTex).bind(GL_COLOR_ATTACHMENT1, this.metaTex).setDrawBuffers(GL_COLOR_ATTACHMENT0,GL_COLOR_ATTACHMENT1).bind(GL_DEPTH_STENCIL_ATTACHMENT, this.depthTex).verify().name("ModelFramebuffer");

        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.stencilTex.id(), GL_DEPTH_STENCIL_TEXTURE_MODE, GL_STENCIL_INDEX);
        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.stencilTex.id(), GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.stencilTex.id(), GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.metaTex.id(), GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        me.cortex.voxy.client.core.gl.GLCompat.textureParameteri(this.metaTex.id(), GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        this.copyOutShader = Shader.makeAuto()
                .define("WIDTH", width)
                .define("HEIGHT", height)
                .define("COLOUR_IN_BINDING", 0)
                .define("DEPTH_IN_BINDING", 1)
                .define("STENCIL_IN_BINDING", 2)
                .define("META_IN_BINDING", 3)
                .define("BUFFER_OUT_BINDING", 4)
                .add(ShaderType.COMPUTE, "voxy:bakery/bufferreorder.comp")
                .compile()
                .name("ModelBakeryOut")
                .texture("META_IN_BINDING", 0, this.metaTex)
                .texture("COLOUR_IN_BINDING", 0, this.colourTex)
                .texture("DEPTH_IN_BINDING", 0, this.depthTex)
                .texture("STENCIL_IN_BINDING", 0, this.stencilTex);
    }

    public void emitToStream(int buffer, int offset) {
        // M9 transitional: GL-only path (uses raw glBindBufferRange / glDispatchCompute /
        // glMemoryBarrier on MC's GL context). On Metal/Vulkan the bakery output stream
        // would receive zeroed data; the bakery stays "running" so the boot sequence
        // doesn't abort, but model textures will be blank until this class is migrated
        // to the abstraction (compute pass via ComputeEncoder + SSBO bindings).
        if (RenderBackendFactory.get().getType() != me.cortex.voxy.client.core.gpu.BackendType.OPENGL) {
            return;
        }
        this.copyOutShader.bind();
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 4, buffer, offset, (this.width*3L)*(this.height*2L)*4L*2);//its 2*4 because colour + depth stencil
        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT|GL_TEXTURE_UPDATE_BARRIER_BIT|GL_PIXEL_BUFFER_BARRIER_BIT|GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);//Am not sure if barriers are right
        glDispatchCompute(3, 2, 1);
        glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 4, 0, 0, 4);//WHY DOES THIS FIX FUCKING BINDING ISSUES HERE WHEN DOING THIS IN THE RENDER SYSTEM DOESNT
    }

    public void clear() {
        // M9 transitional: ditto — glClearNamedFramebuffer* are GL DSA calls that
        // don't exist on Apple's frozen GL 4.1 driver. Skip on non-OpenGL backend.
        if (RenderBackendFactory.get().getType() != me.cortex.voxy.client.core.gpu.BackendType.OPENGL) {
            return;
        }
        try (var stack = MemoryStack.stackPush()) {
            long ptr = stack.nmalloc(4*4);
            MemoryUtil.memPutLong(ptr, 0);
            MemoryUtil.memPutLong(ptr+8, 0);
            nglClearNamedFramebufferfv(this.framebuffer.id(), GL_COLOR, 0, ptr);
            nglClearNamedFramebufferuiv(this.framebuffer.id(), GL_COLOR, 1, ptr);
            //TODO: fix the draw buffer thing maybe? it might need todo multiple clears
            //nglClearNamedFramebufferfv(this.framebuffer.id(), GL_COLOR, 0, ptr);
        }
        glClearNamedFramebufferfi(this.framebuffer.id(), GL_DEPTH_STENCIL, 0, 1.0f, 0);
    }

    public void free() {
        if (this.framebuffer != null) this.framebuffer.free();
        if (this.colourTex != null) this.colourTex.free();
        if (this.stencilTex != null) this.stencilTex.free();
        if (this.depthTex != null) this.depthTex.free();
        if (this.metaTex != null) this.metaTex.free();
        if (this.copyOutShader != null) this.copyOutShader.free();
    }
}
