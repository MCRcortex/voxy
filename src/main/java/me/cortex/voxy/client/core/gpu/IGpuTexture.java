package me.cortex.voxy.client.core.gpu;

/**
 * Abstraction over a GPU texture resource.
 */
public interface IGpuTexture extends IGpuResource {
    int id();
    int getWidth();
    int getHeight();
    int getLevels();
    int getFormat();
    int getType();

    IGpuTexture store(int format, int levels, int width, int height);
    IGpuTexture createView();
    IGpuTexture name(String name);
    void assertAllocated();
}
