package me.cortex.voxy.client.core.rendering;

import org.joml.Matrix4f;

public abstract class Viewport <A extends Viewport<A>> {
    public int width;
    public int height;
    int frameId;
    public Matrix4f projection;
    public Matrix4f modelView;
    public double cameraX;
    public double cameraY;
    public double cameraZ;

    protected Viewport() {
    }

    public final void delete() {
        this.delete0();
    }

    protected abstract void delete0();

    public A setProjection(Matrix4f projection) {
        this.projection = projection;
        return (A) this;
    }

    public A setModelView(Matrix4f modelView) {
        this.modelView = modelView;
        return (A) this;
    }

    public A setCamera(double x, double y, double z) {
        this.cameraX = x;
        this.cameraY = y;
        this.cameraZ = z;
        return (A) this;
    }

    public A setScreenSize(int width, int height) {
        this.width = width;
        this.height = height;
        return (A) this;
    }
}
