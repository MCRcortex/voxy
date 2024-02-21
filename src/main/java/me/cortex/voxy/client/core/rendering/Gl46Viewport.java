package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.UploadStream;
import me.cortex.voxy.client.mixin.joml.AccessFrustumIntersection;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
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

public class Gl46Viewport extends Viewport {
    GlBuffer visibilityBuffer;
    public Gl46Viewport(int maxSections) {
        this.visibilityBuffer = new GlBuffer(maxSections*4L);
        glClearNamedBufferData(this.visibilityBuffer.id, GL_R8UI, GL_RED_INTEGER, GL_UNSIGNED_BYTE, new int[1]);
    }

    @Override
    public void delete() {
        this.visibilityBuffer.free();
    }
}
