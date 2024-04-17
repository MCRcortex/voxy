package me.cortex.voxy.client.core.rendering;

import org.joml.Matrix4f;

public abstract class Viewport <A extends Viewport<A>> {
    private final AbstractFarWorldRenderer renderer;
    int width;
    int height;
    int frameId;
    Matrix4f projection;
    Matrix4f modelView;
    double cameraX;
    double cameraY;
    double cameraZ;

    protected Viewport(AbstractFarWorldRenderer renderer) {
        this.renderer = renderer;
    }

    public final void delete() {
        this.delete0();
        this.renderer.removeViewport((A) this);
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
