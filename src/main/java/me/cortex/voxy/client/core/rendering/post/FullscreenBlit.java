package me.cortex.voxy.client.core.rendering.post;

import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.SharedIndexBuffer;

import java.util.function.Function;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL15C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL30C.glBindVertexArray;
import static org.lwjgl.opengl.GL30C.glGenVertexArrays;

public class FullscreenBlit {
    private static final int EMPTY_VAO = glGenVertexArrays();

    private final Shader shader;
    public FullscreenBlit(String fragId) {
        this(fragId, (a)->a);
    }

    public FullscreenBlit(String vertId, String fragId) {
        this(vertId, fragId, (a)->a);
    }

    public <T extends Shader> FullscreenBlit(String fragId, Function<Shader.Builder<T>, Shader.Builder<T>> builder) {
        this("voxy:post/fullscreen.vert", fragId, builder);
    }

    public <T extends Shader> FullscreenBlit(String vertId, String fragId, Function<Shader.Builder<T>, Shader.Builder<T>> builder) {
        this.shader = builder.apply((Shader.Builder<T>) Shader.make()
                .add(ShaderType.VERTEX, vertId)
                .add(ShaderType.FRAGMENT, fragId))
                .compile();
    }

    public void bind() {
        this.shader.bind();
    }

    public void blit() {
        glBindVertexArray(EMPTY_VAO);
        this.shader.bind();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, SharedIndexBuffer.INSTANCE_BYTE.id());
        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_BYTE, 0);
        glBindVertexArray(0);
    }

    public void delete() {
        this.shader.free();
    }
}
