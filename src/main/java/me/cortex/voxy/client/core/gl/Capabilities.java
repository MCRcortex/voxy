package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.client.core.gl.shader.ShaderType;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20C;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL32.glGetInteger64;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BLOCK_SIZE;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.*;

public class Capabilities {

    public static final Capabilities INSTANCE = new Capabilities();

    public final boolean meshShaders;
    public final boolean INT64_t;
    public final long ssboMaxSize;
    public final boolean isMesa;
    public final boolean canQueryGpuMemory;
    public final long totalDedicatedMemory;//Bytes, dedicated memory
    public final long totalDynamicMemory;//Bytes, total allocation memory - dedicated memory
    public final boolean compute;
    public final boolean indirectParameters;
    public Capabilities() {
        var cap = GL.getCapabilities();
        this.compute = cap.glDispatchComputeIndirect != 0;
        this.indirectParameters = cap.glMultiDrawElementsIndirectCountARB != 0;
        this.meshShaders = cap.GL_NV_mesh_shader && cap.GL_NV_representative_fragment_test;
        this.canQueryGpuMemory = cap.GL_NVX_gpu_memory_info;
        //this.INT64_t = cap.GL_ARB_gpu_shader_int64 || cap.GL_AMD_gpu_shader_int64;
        //The only reliable way to test for int64 support is to try compile a shader
        this.INT64_t = testShaderCompilesOk(ShaderType.COMPUTE, """
                #version 430
                #extension GL_ARB_gpu_shader_int64 : require
                layout(local_size_x=32) in;
                void main() {
                    uint64_t a = 1234;
                }
                """);

        this.ssboMaxSize = glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);

        this.isMesa = glGetString(GL_VERSION).toLowerCase().contains("mesa");

        if (this.canQueryGpuMemory) {
            this.totalDedicatedMemory = glGetInteger64(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX)*1024;//Since its in Kb
            this.totalDynamicMemory = (glGetInteger64(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX)*1024) - this.totalDedicatedMemory;//Since its in Kb
        } else {
            this.totalDedicatedMemory = -1;
            this.totalDynamicMemory = -1;
        }
    }

    public static void init() {
    }

    private static boolean testShaderCompilesOk(ShaderType type, String src) {
        int shader = GL20C.glCreateShader(type.gl);
        GL20C.glShaderSource(shader, src);
        GL20C.glCompileShader(shader);
        int result = GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS);
        GL20C.glDeleteShader(shader);

        return result == GL20C.GL_TRUE;
    }

    public long getFreeDedicatedGpuMemory() {
        if (!this.canQueryGpuMemory) {
            throw new IllegalStateException("Cannot query gpu memory, missing extension");
        }
        return glGetInteger64(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX)*1024;//Since its in Kb
    }

    //TODO: add gpu eviction tracking
}
