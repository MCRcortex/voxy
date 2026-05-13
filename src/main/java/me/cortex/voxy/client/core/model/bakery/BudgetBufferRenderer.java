package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.common.Logger;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL31C.*;
import static org.lwjgl.opengl.GL33C.glBindSampler;

/**
 * Static helper that streams a quad mesh + texture into the bakery framebuffer.
 *
 * M13 chunk 1: every GPU resource here is allocated through raw GL against
 * MC's OpenGL context — Voxy's render backend (GL or Metal) is irrelevant.
 * The bakery's draw calls (`glDrawElements`, `glBindVertexArray`,
 * `glUseProgram`, `glBindBufferRange(GL_UNIFORM_BUFFER, ...)`) all run
 * inside MC's GL context which is current on the render thread regardless
 * of the Voxy backend. That removes the previous "M11 transitional"
 * Metal-skip gates and unblocks the bakery on Apple Silicon.
 *
 * Shader compile uses {@code glCreateShader} directly and binds the
 * sampler/UBO explicitly via {@code glUniform1i} and
 * {@code glUniformBlockBinding} so it works on Apple's GL 4.1 cap even if
 * the source's {@code layout(binding=N)} qualifiers are silently ignored
 * by the older GLSL profile.
 */
public class BudgetBufferRenderer {
    public static final int VERTEX_FORMAT_SIZE = 24;
    private static final int STRIDE = 24;

    /** UBO binding for position_tex.vsh's `Push { mat4 transform; }`. */
    private static final int PUSH_BINDING = 14;

    /** Sampler unit for position_tex.fsh's `tex`. */
    private static final int TEX_UNIT = 0;

    private static int bakeryProgram;
    private static int indexBufferGl;
    private static int vaoGl;
    private static int immediateBufferGl;
    private static long immediateBufferCapacity;
    private static int pushUbo;
    private static long pushUboCapacity;
    private static int quadCount;

    /**
     * Compile the bakery shader pair and allocate the GL-side resources. Must
     * be called on the render thread inside MC's GL context.
     */
    public static void init() {
        if (bakeryProgram != 0) return;

        // Compile shader pair through raw GL — independent of Voxy's backend.
        String vsh = ShaderLoader.parse("voxy:bakery/position_tex.vsh");
        String fsh = ShaderLoader.parse("voxy:bakery/position_tex.fsh");
        bakeryProgram = linkProgram("bakery", vsh, fsh);

        // Wire the sampler + UBO bindings explicitly so the program works on
        // GL profiles where layout(binding=N) is silently ignored.
        glUseProgram(bakeryProgram);
        int samplerLoc = glGetUniformLocation(bakeryProgram, "tex");
        if (samplerLoc != -1) {
            glUniform1i(samplerLoc, TEX_UNIT);
        }
        int pushBlock = glGetUniformBlockIndex(bakeryProgram, "Push");
        if (pushBlock != GL_INVALID_INDEX) {
            glUniformBlockBinding(bakeryProgram, pushBlock, PUSH_BINDING);
        }
        glUseProgram(0);

        // Index buffer — 4096 quads × 6 indices × 2 bytes = 48 KB.
        indexBufferGl = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBufferGl);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, 3L * 2 * 2 * 4096, GL_STATIC_DRAW);
        // Copy MC's sequential quad index buffer into it. MC's blaze3d gives
        // us the GL buffer handle directly; copy via GL 3.1's
        // glCopyBufferSubData (no DSA required — works on Apple GL 4.1).
        var seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        int srcId = ((com.mojang.blaze3d.opengl.GlBuffer) seq.getBuffer(4096 * 3 * 2)).handle;
        if (seq.type() != VertexFormat.IndexType.SHORT) {
            throw new IllegalStateException("Expected SHORT sequential quad indices");
        }
        glBindBuffer(GL_COPY_READ_BUFFER, srcId);
        glBindBuffer(GL_COPY_WRITE_BUFFER, indexBufferGl);
        glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, 0, 0, 3L * 2 * 2 * 4096);
        glBindBuffer(GL_COPY_READ_BUFFER, 0);
        glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        // VAO — vertex attribs get configured the first time setup() supplies
        // an immediate buffer.
        vaoGl = glGenVertexArrays();
    }

    public static void drawFast(MeshData buffer, GpuTexture tex, Matrix4f matrix) {
        if (buffer.drawState().mode() != VertexFormat.Mode.QUADS) {
            throw new IllegalStateException("Fast only supports quads");
        }
        var buff = buffer.vertexBuffer();
        int size = buff.remaining();
        if (size % STRIDE != 0) throw new IllegalStateException();
        size /= STRIDE;
        if (size % 4 != 0) throw new IllegalStateException();
        size /= 4;
        setup(MemoryUtil.memAddress(buff), size, ((com.mojang.blaze3d.opengl.GlTexture) tex).glId());
        buffer.close();
        render(matrix);
    }

    public static void setup(long dataPtr, int quads, int texId) {
        if (quads == 0) {
            throw new IllegalStateException();
        }
        if (bakeryProgram == 0) {
            init();
        }
        quadCount = quads;

        long size = (long) quads * 4L * STRIDE;
        if (immediateBufferGl == 0 || immediateBufferCapacity < size) {
            if (immediateBufferGl != 0) {
                glDeleteBuffers(immediateBufferGl);
            }
            immediateBufferGl = glGenBuffers();
            immediateBufferCapacity = size * 2L;
            glBindBuffer(GL_ARRAY_BUFFER, immediateBufferGl);
            glBufferData(GL_ARRAY_BUFFER, immediateBufferCapacity, GL_DYNAMIC_DRAW);
            // Configure the VAO to point at this buffer + the persistent index
            // buffer. Must rebind on resize because glVertexAttribPointer
            // captures the currently-bound GL_ARRAY_BUFFER.
            glBindVertexArray(vaoGl);
            glBindBuffer(GL_ARRAY_BUFFER, immediateBufferGl);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBufferGl);
            glEnableVertexAttribArray(0);
            glVertexAttribPointer(0, 4, GL_FLOAT, false, STRIDE, 0L);            // pos + metadata
            glEnableVertexAttribArray(1);
            glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, 4L * 4);        // UV
            glBindVertexArray(0);
        }

        glBindBuffer(GL_ARRAY_BUFFER, immediateBufferGl);
        nglBufferSubData(GL_ARRAY_BUFFER, 0L, size, dataPtr);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        glUseProgram(bakeryProgram);
        glBindVertexArray(vaoGl);
        glBindSampler(TEX_UNIT, 0);
        me.cortex.voxy.client.core.gl.GLCompat.bindTextureUnit(TEX_UNIT, texId);
    }

    public static void render(Matrix4f matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long addr = stack.nmalloc(64);
            matrix.getToAddress(addr);
            pushMatrix(addr, 64);
        }
        glDrawElements(GL_TRIANGLES, quadCount * 2 * 3, GL_UNSIGNED_SHORT, 0L);
    }

    /**
     * Release all GL state {@link #setup} left bound: program, VAO,
     * UBO/sampler bindings. Call this once after the bakery's last
     * {@link #render} per bake — without it, downstream GL consumers (Sodium's
     * chunk renderer, MC's UI) inherit our VAO + program and can hit
     * confusing failures (Apple GL has been observed to return null from
     * {@code glMapBufferRange} when our VAO is left bound, raising
     * "Failed to map buffer" deep inside Sodium).
     */
    public static void endRender() {
        glUseProgram(0);
        glBindVertexArray(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glBindBufferRange(GL_UNIFORM_BUFFER, PUSH_BINDING, 0, 0L, 0L);
        glBindSampler(TEX_UNIT, 0);
    }

    private static void pushMatrix(long addr, int size) {
        long needed = (size + 255L) & ~255L;
        if (pushUbo == 0) {
            pushUbo = glGenBuffers();
            pushUboCapacity = Math.max(needed, 256L);
            glBindBuffer(GL_UNIFORM_BUFFER, pushUbo);
            glBufferData(GL_UNIFORM_BUFFER, pushUboCapacity, GL_DYNAMIC_DRAW);
        } else if (needed > pushUboCapacity) {
            pushUboCapacity = needed;
            glBindBuffer(GL_UNIFORM_BUFFER, pushUbo);
            glBufferData(GL_UNIFORM_BUFFER, pushUboCapacity, GL_DYNAMIC_DRAW);
        } else {
            glBindBuffer(GL_UNIFORM_BUFFER, pushUbo);
        }
        nglBufferSubData(GL_UNIFORM_BUFFER, 0L, size, addr);
        glBindBufferRange(GL_UNIFORM_BUFFER, PUSH_BINDING, pushUbo, 0L, size);
    }

    public static void shutdown() {
        if (pushUbo != 0) {
            glDeleteBuffers(pushUbo);
            pushUbo = 0;
        }
        if (immediateBufferGl != 0) {
            glDeleteBuffers(immediateBufferGl);
            immediateBufferGl = 0;
        }
        if (indexBufferGl != 0) {
            glDeleteBuffers(indexBufferGl);
            indexBufferGl = 0;
        }
        if (vaoGl != 0) {
            glDeleteVertexArrays(vaoGl);
            vaoGl = 0;
        }
        if (bakeryProgram != 0) {
            glDeleteProgram(bakeryProgram);
            bakeryProgram = 0;
        }
    }

    private static int linkProgram(String label, String vshSrc, String fshSrc) {
        // Apple's GL caps GLSL at 4.10; the bakery shaders are written as 4.30
        // (they share source with Voxy's main pipeline which uses SPIRV-Cross).
        // Downgrade the version + enable the explicit-layout extensions the
        // sources rely on — works on both Apple GL 4.1 and Win/Linux GL 4.6.
        vshSrc = downgradeVersionForCompat(vshSrc);
        fshSrc = downgradeVersionForCompat(fshSrc);
        int vs = compileShader(label + ".vsh", GL_VERTEX_SHADER, vshSrc);
        int fs = compileShader(label + ".fsh", GL_FRAGMENT_SHADER, fshSrc);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        int status = glGetProgrami(prog, GL_LINK_STATUS);
        if (status == GL_FALSE) {
            String log = glGetProgramInfoLog(prog);
            glDeleteProgram(prog);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new RuntimeException("Bakery program " + label + " link failed:\n" + log);
        }
        glDetachShader(prog, vs);
        glDetachShader(prog, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static String downgradeVersionForCompat(String src) {
        // Apple's GL 4.1 driver does not expose `GL_ARB_shading_language_420pack`
        // or `GL_ARB_explicit_uniform_location`, so we can't rely on
        // `layout(binding=N)` qualifiers on UBOs/samplers. The bakery wires
        // those bindings explicitly after link via `glUniformBlockBinding`
        // and `glUniform1i`, so it's safe to strip the qualifiers from the
        // source.
        //
        // We also reorder `out flat` / `in flat` → `flat out` / `flat in`
        // because GLSL 4.10 requires the interpolation qualifier to precede
        // the storage qualifier (4.30 loosened this).
        //
        // Bakery shaders only — main Voxy pipelines go through SPIRV-Cross
        // which preserves the original 4.30 source.
        String s = src.replaceFirst("(?m)^\\s*#version\\s+\\d+(\\s+\\w+)?\\s*$", "#version 410 core");
        // Strip `binding=N` qualifiers from UBO and sampler uniform blocks.
        // Patterns covered:
        //   layout(binding = X, std140) uniform Push { ... };
        //   layout(binding = X) uniform sampler2D tex;
        //   layout(binding=X, std140) uniform Foo;
        s = s.replaceAll("layout\\s*\\(\\s*binding\\s*=\\s*[^,)]+\\s*,\\s*", "layout(");
        s = s.replaceAll("layout\\s*\\(\\s*binding\\s*=\\s*[^,)]+\\s*\\)\\s*uniform",
                "uniform");
        // Reorder interpolation/storage qualifiers.
        s = s.replaceAll("\\bout\\s+flat\\b", "flat out");
        s = s.replaceAll("\\bin\\s+flat\\b", "flat in");
        return s;
    }

    private static int compileShader(String label, int type, String src) {
        int sh = glCreateShader(type);
        glShaderSource(sh, src);
        glCompileShader(sh);
        int status = glGetShaderi(sh, GL_COMPILE_STATUS);
        if (status == GL_FALSE) {
            String log = glGetShaderInfoLog(sh);
            glDeleteShader(sh);
            Logger.error("Bakery shader compile failed: " + label + "\n" + log + "\n--- source ---\n" + src);
            throw new RuntimeException("Bakery shader compile failed: " + label + ": " + log);
        }
        return sh;
    }
}
