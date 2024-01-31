package me.cortex.voxy.client.core.gl.shader;


import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.NVMeshShader.GL_MESH_SHADER_NV;
import static org.lwjgl.opengl.NVMeshShader.GL_TASK_SHADER_NV;

public enum ShaderType {
    VERTEX(GL_VERTEX_SHADER),
    FRAGMENT(GL_FRAGMENT_SHADER),
    COMPUTE(GL_COMPUTE_SHADER),
    MESH(GL_MESH_SHADER_NV),
    TASK(GL_TASK_SHADER_NV);
    public final int gl;
    ShaderType(int glEnum) {
        gl = glEnum;
    }
}
