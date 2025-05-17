package me.cortex.voxy.client.core.gl;

import me.cortex.voxy.common.util.TrackedObject;

import static org.lwjgl.opengl.ARBFramebufferObject.glDeleteFramebuffers;
import static org.lwjgl.opengl.ARBFramebufferObject.glGenFramebuffers;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL30.GL_DEPTH24_STENCIL8;
import static org.lwjgl.opengl.GL45C.*;

public class GlTexture extends TrackedObject {
    public final int id;
    private final int type;
    private int format;
    private int width;
    private int height;
    private int levels;
    private boolean hasAllocated;

    private static int COUNT;
    private static long ESTIMATED_TOTAL_SIZE;

    public GlTexture() {
        this(GL_TEXTURE_2D);
    }

    public GlTexture(int type) {
        this.id = glCreateTextures(type);
        this.type = type;
        COUNT++;
    }

    private GlTexture(int type, boolean useGenTypes) {
        if (useGenTypes) {
            this.id = glGenTextures();
        } else {
            this.id = glCreateTextures(type);
        }
        this.type = type;
        COUNT++;
    }

    public GlTexture store(int format, int levels, int width, int height) {
        if (this.hasAllocated) {
            throw new IllegalStateException("Texture already allocated");
        }
        this.hasAllocated = true;

        this.format = format;
        if (this.type == GL_TEXTURE_2D) {
            glTextureStorage2D(this.id, levels, format, width, height);
            this.width = width;
            this.height = height;
            this.levels = levels;
        } else {
            throw new IllegalStateException("Unknown texture type");
        }
        ESTIMATED_TOTAL_SIZE += this.getEstimatedSize();
        return this;
    }

    public GlTexture createView() {
        this.assertAllocated();
        var view = new GlTexture(this.type, true);
        glTextureView(view.id, this.type, this.id, this.format, 0, 1, 0, 1);
        return view;
    }

    @Override
    public void free() {
        if (this.hasAllocated) {
            ESTIMATED_TOTAL_SIZE -= this.getEstimatedSize();
        }
        COUNT--;
        this.hasAllocated = false;
        super.free0();
        glDeleteTextures(this.id);
    }

    public GlTexture name(String name) {
        this.assertAllocated();
        return GlDebug.name(name, this);
    }

    public int getWidth() {
        this.assertAllocated();
        return this.width;
    }

    public int getHeight() {
        this.assertAllocated();
        return this.height;
    }

    public int getLevels() {
        this.assertAllocated();
        return this.levels;
    }

    private long getEstimatedSize() {
        this.assertAllocated();
        long elemSize = switch (this.format) {
            case GL_RGBA8, GL_DEPTH24_STENCIL8 -> 4;
            case GL_DEPTH_COMPONENT24 -> 4;//TODO: check this is right????

            default -> throw new IllegalStateException("Unknown element size");
        };

        long size = 0;
        for (int lvl = 0; lvl < this.levels; lvl++) {
            size += Math.max((((long)this.width)>>lvl), 1) * Math.max((((long)this.height)>>lvl), 1) * elemSize;
        }
        return size;
    }

    public void assertAllocated() {
        if (!this.hasAllocated) {
            throw new IllegalStateException("Texture not yet allocated");
        }
    }


    public static int getCount() {
        return COUNT;
    }

    public static long getEstimatedTotalSize() {
        return ESTIMATED_TOTAL_SIZE;
    }
}
