package me.cortex.voxy.client.core.rendering;

import org.joml.Matrix4f;

public abstract class Viewport {
    int frameId;
    Matrix4f projection;
    Matrix4f modelView;
    double cameraX;
    double cameraY;
    double cameraZ;

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
