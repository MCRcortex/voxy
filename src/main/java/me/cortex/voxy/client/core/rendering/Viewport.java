package me.cortex.voxy.client.core.rendering;

import org.joml.Matrix4f;

public abstract class Viewport {
    public int frameId;
    public Matrix4f projection;
    public Matrix4f modelView;
    public double cameraX;
    public double cameraY;
    public double cameraZ;

    public abstract void delete();

    public Viewport setProjection(Matrix4f projection) {
        this.projection = projection;
        return this;
    }

    public Viewport setModelView(Matrix4f modelView) {
        this.modelView = modelView;
        return this;
    }

    public Viewport setCamera(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
        return this;
    }
}
