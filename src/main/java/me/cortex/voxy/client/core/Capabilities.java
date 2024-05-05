package me.cortex.voxy.client.core;

import org.lwjgl.opengl.GL;

public class Capabilities {

    public static final Capabilities INSTANCE = new Capabilities();

    public final boolean meshShaders;
    public final boolean INT64_t;
    public Capabilities() {
        var cap = GL.getCapabilities();
        this.meshShaders = cap.GL_NV_mesh_shader && cap.GL_NV_representative_fragment_test;
        this.INT64_t = cap.GL_ARB_gpu_shader_int64 || cap.GL_AMD_gpu_shader_int64;
    }
}
