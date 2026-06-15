package me.cortex.neovoxy.client.core.gl;

import me.cortex.neovoxy.client.core.gl.shader.ShaderType;
import me.cortex.neovoxy.common.Logger;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.util.Locale;
import java.util.Random;

import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL;
import static org.lwjgl.opengl.GL30C.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL32.glGetInteger64;
import static org.lwjgl.opengl.GL43C.GL_MAX_SHADER_STORAGE_BLOCK_SIZE;
import static org.lwjgl.opengl.GL44.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL44.GL_MAP_COHERENT_BIT;
import static org.lwjgl.opengl.GL45.glClearNamedFramebufferfi;
import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.opengl.GL45C.glCreateFramebuffers;
import static org.lwjgl.opengl.NVXGPUMemoryInfo.*;

public class Capabilities {

    public static final Capabilities INSTANCE = new Capabilities();

    public final boolean repFragTest;
    public final boolean meshShaders;
    public final boolean INT64_t;
    public final long ssboMaxSize;
    public final boolean isMesa;
    public final boolean canQueryGpuMemory;
    public final long totalDedicatedMemory;//Bytes, dedicated memory
    public final long totalDynamicMemory;//Bytes, total allocation memory - dedicated memory
    public final boolean compute;
    public final boolean indirectParameters;
    public final boolean isIntel;
    public final boolean subgroup;
    public final boolean sparseBuffer;
    public final boolean isNvidia;
    public final boolean isAmd;
    public final boolean nvBarryCoords;
    public final boolean hasBrokenDepthSampler;

    public Capabilities() {
        var cap = GL.getCapabilities();
        this.sparseBuffer = cap.GL_ARB_sparse_buffer;
        this.compute = cap.glDispatchComputeIndirect != 0;
        this.indirectParameters = cap.glMultiDrawElementsIndirectCountARB != 0;
        this.repFragTest = cap.GL_NV_representative_fragment_test;
        this.meshShaders = cap.GL_NV_mesh_shader;
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
        if (cap.GL_KHR_shader_subgroup) {
            this.subgroup = testShaderCompilesOk(ShaderType.COMPUTE, """
                #version 430
                #extension GL_KHR_shader_subgroup_basic : require
                #extension GL_KHR_shader_subgroup_arithmetic : require
                layout(local_size_x=32) in;
                void main() {
                    uint a = subgroupExclusiveAdd(gl_LocalInvocationIndex);
                }
                """);
        } else {
            this.subgroup = false;
        }

        this.ssboMaxSize = glGetInteger64(GL_MAX_SHADER_STORAGE_BLOCK_SIZE);

        this.isMesa = glGetString(GL_VERSION).toLowerCase(Locale.ROOT).contains("mesa");
        var vendor = glGetString(GL_VENDOR).toLowerCase(Locale.ROOT);
        this.isIntel = vendor.contains("intel");
        this.isNvidia = vendor.contains("nvidia");
        this.isAmd = vendor.contains("amd")||vendor.contains("radeon");

        if (this.canQueryGpuMemory) {
            this.totalDedicatedMemory = glGetInteger64(GL_GPU_MEMORY_INFO_DEDICATED_VIDMEM_NVX)*1024;//Since its in Kb
            this.totalDynamicMemory = (glGetInteger64(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX)*1024) - this.totalDedicatedMemory;//Since its in Kb
        } else {
            this.totalDedicatedMemory = -1;
            this.totalDynamicMemory = -1;
        }

        this.nvBarryCoords = cap.GL_NV_fragment_shader_barycentric;

        if (this.compute&&this.isAmd) {
            this.hasBrokenDepthSampler = testDepthSampler();
            // Boolean flag is sufficient for graceful degradation in NeoVoxyClient.initNeoVoxyClient()
            // Throwing exception here bypasses the graceful error handling
        } else {
            this.hasBrokenDepthSampler = false;
        }
    }

    public static void init() {
    }

    private static boolean testDepthSampler() {
        String src = """
                #version 460 core
                layout(local_size_x=16,local_size_y=16) in;
                
                layout(binding = 0) uniform sampler2D depthSampler;
                layout(binding = 1) buffer OutData {
                    float[] outData;
                };
                
                layout(location = 2) uniform int dynamicSampleThing;
                layout(location = 3) uniform float sampleData;
                
                void main() {
                    if (abs(texelFetch(depthSampler, ivec2(gl_GlobalInvocationID.xy), dynamicSampleThing).r-sampleData)>0.000001f) {
                        outData[0] = 1.0;
                    }
                }
                """;
        int program = GL20C.glCreateProgram();
        {
            int shader = GL20C.glCreateShader(ShaderType.COMPUTE.gl);
            GL20C.glShaderSource(shader, src);
            GL20C.glCompileShader(shader);
            if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != 1) {
                GL20C.glDeleteShader(shader);
                throw new IllegalStateException("Shader compile fail");
            }
            GL20C.glAttachShader(program, shader);
            GL20C.glLinkProgram(program);
            glDeleteShader(shader);
        }

        int buffer = glCreateBuffers();
        glNamedBufferStorage(buffer, 4096, GL_DYNAMIC_STORAGE_BIT|GL_MAP_READ_BIT);

        int tex = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(tex, 2, GL_DEPTH24_STENCIL8, 256, 256);
        glTextureParameteri(tex, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(tex, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        int fb = glCreateFramebuffers();
        boolean isCorrect = true;
        for (int lvl = 0; lvl <= 1; lvl++) {
            glNamedFramebufferTexture(fb, GL_DEPTH_STENCIL_ATTACHMENT, tex, lvl);

            for (int i = 0; i <= 10; i++) {
                float value = (float) (i / 10.0);

                nglClearNamedBufferSubData(buffer, GL_R32F, 0, 4096, GL_RED, GL_FLOAT, 0);//Zero the buffer
                glClearNamedFramebufferfi(fb, GL_DEPTH_STENCIL, 0, value, 1);//Set the depth texture

                glUseProgram(program);
                glUniform1i(2, lvl);
                glUniform1f(3, value);
                glBindTextureUnit(0, tex);
                GL30.glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 1, buffer);

                glDispatchCompute(256>>(lvl+4), 256>>(lvl+4), 1);
                glFinish();

                long ptr = nglMapNamedBuffer(buffer, GL_READ_ONLY);
                float gottenValue = MemoryUtil.memGetFloat(ptr);
                glUnmapNamedBuffer(buffer);

                glUseProgram(0);
                glBindTextureUnit(0, 0);
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

                boolean localCorrect = gottenValue==0.0f;
                if (!localCorrect) {
                    Logger.error("Depth read test failed at value: " + value);
                }
                isCorrect &= localCorrect;
            }
        }

        glDeleteFramebuffers(fb);
        glDeleteTextures(tex);
        glDeleteBuffers(buffer);
        glDeleteProgram(program);
        return !isCorrect;
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
