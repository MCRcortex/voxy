package me.cortex.voxy.client.core.model.bakery;

import me.cortex.voxy.client.core.model.ARGB;
import me.cortex.voxy.client.core.model.ModelFactory;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;

public class SoftwareRasterizer {
    public static final int TARGET_SIZE = ModelFactory.MODEL_TEXTURE_SIZE;

    private final Vector4f scratch = new Vector4f();

    private final Vector3f scratch1 = new Vector3f();
    private final Vector3f scratch2 = new Vector3f();
    private final Vector3f scratch3 = new Vector3f();
    private final Vector3f scratch4 = new Vector3f();
    //quad meta uv
    private final Vector3f qmuv1 = new Vector3f();
    private final Vector3f qmuv2 = new Vector3f();
    private final Vector3f qmuv3 = new Vector3f();
    private final Vector3f qmuv4 = new Vector3f();


    private final Vector3f scratchR1 = new Vector3f();
    private final Vector3f scratchR2 = new Vector3f();
    private final Vector3f scratchR3 = new Vector3f();
    //Attributes (meta, u, v)
    private final Vector3f a1 = new Vector3f();
    private final Vector3f a2 = new Vector3f();
    private final Vector3f a3 = new Vector3f();

    private static final long DEPTH_MASK = ((1L<<24)-1)<<(64-24);
    private static final long CLEAR_VALUE = DEPTH_MASK;//set the depth to max value and rest of bits to 0
    private final long[] framebuffer = new long[TARGET_SIZE*TARGET_SIZE];

    private boolean cullBackFace;
    private boolean doTheBlending;

    private int samplerWidth;
    private int samplerHeight;
    private int[] samplerTexture;

    public SoftwareRasterizer() {
    }

    public void setFaceCull(boolean isBackFaceCulling) {
        this.cullBackFace = isBackFaceCulling;
    }

    public void setBlending(boolean blending) {
        this.doTheBlending = blending;
    }

    public void setSamplerTexture(int[] texture, int width, int height) {
        if (texture.length != width*height) throw new IllegalArgumentException();
        this.samplerTexture = texture;
        this.samplerWidth = width;
        this.samplerHeight = height;
    }

    private int sampleTexture(float u, float v) {
        int pu = ARGB.clamp(Math.round(u*this.samplerWidth-0.5f), 0, this.samplerWidth-1);
        int pv = ARGB.clamp(Math.round(v*this.samplerHeight-0.5f), 0, this.samplerHeight-1);
        return this.samplerTexture[this.samplerWidth*pv+pu];
    }

    public void raster(Matrix4f mvp, ReuseVertexConsumer vertices) {
        Arrays.fill(this.framebuffer, CLEAR_VALUE);

        int qc = vertices.quadCount();
        for (int i = 0; i < qc; i++) {
            this.rasterQuad(mvp, vertices.getAddress()+ReuseVertexConsumer.VERTEX_FORMAT_SIZE*4*i);
        }
        //Arrays.fill(this.framebuffer, -1);
    }

    private void rasterQuad(Matrix4f transform, long addr) {
        loadTransformPos(transform, addr, 0, this.scratch1, this.qmuv1);
        loadTransformPos(transform, addr, 1, this.scratch2, this.qmuv2);
        loadTransformPos(transform, addr, 2, this.scratch3, this.qmuv3);
        loadTransformPos(transform, addr, 3, this.scratch4, this.qmuv4);


        //0,1,2 | 2,3,0
        this.scratchR1.set(this.scratch1);
        this.scratchR2.set(this.scratch2);
        this.scratchR3.set(this.scratch3);
        this.a1.set(this.qmuv1);
        this.a2.set(this.qmuv2);
        this.a3.set(this.qmuv3);
        this.rasterTriangle();
        this.scratchR1.set(this.scratch3);
        this.scratchR2.set(this.scratch4);
        this.scratchR3.set(this.scratch1);
        this.a1.set(this.qmuv3);
        this.a2.set(this.qmuv4);
        this.a3.set(this.qmuv1);
        this.rasterTriangle();

    }

    private void rasterTriangle() {
        Vector3f v1 = this.scratchR1;
        Vector3f v2 = this.scratchR2;
        Vector3f v3 = this.scratchR3;


        float area = edge(v1, v2, v3);

        //Pretty sure this is how you check for winding order aswell (if area is negative its counterclockwise)
        if (area<0 == this.cullBackFace) {
            return;
        }

        if (Math.abs(area)<0.001) {
            return;//Degenerate triangle
        }

        //TODO: check this is right?
        /*
        if (area < 0) {
            var t = v1;
            v1 = v2;
            v2 = t;
            area = -area;
        }*/

        int minX = Math.max((int) Math.floor(Math.min(Math.min(v1.x, v2.x), v3.x)), 0);
        int maxX = Math.min((int) Math.ceil(Math.max(Math.max(v1.x, v2.x), v3.x)), TARGET_SIZE-1);
        int minY = Math.max((int) Math.floor(Math.min(Math.min(v1.y, v2.y), v3.y)), 0);
        int maxY = Math.min((int) Math.ceil(Math.max(Math.max(v1.y, v2.y), v3.y)), TARGET_SIZE-1);

        float invArea = 1.0f/area;
        for (int py = minY; py<=maxY; py++) {
            for (int px = minX; px<=maxX; px++) {
                float cx = px+0.5f;
                float cy = py+0.5f;
                float w1 = edge(v2, v3, cx, cy)*invArea;
                float w2 = edge(v3, v1, cx, cy)*invArea;
                float w3 = 1.0f-w1-w2;
                if (w1>=0.0f&&w2>=0.0f&&w3>=0.0f) {
                    //Dont need to worry about perspective correction afak as it should already be all correct

                    //pixel is inside the triangle
                    this.rasterPixel(px+py*TARGET_SIZE, w1, w2, w3);
                }
            }
        }
    }

    private void rasterPixel(int index, float b1, float b2, float b3) {//Barry coords
        float z = Math.fma(b1, this.scratchR1.z, Math.fma(b2, this.scratchR2.z, b3 * this.scratchR3.z));
        z = Math.fma(z,0.5f,0.5f);
        if (z<0.0f && -0.000001f<=z) z = 0;//Clamp to 0 if its really small negative
        if (z<0.0f||z>1.0f)
            return;//TODO: check this



        int meta = Float.floatToRawIntBits(this.a1.x);
        float u = Math.fma(b1, this.a1.y, Math.fma(b2, this.a2.y, b3 * this.a3.y));
        float v = Math.fma(b1, this.a1.z, Math.fma(b2, this.a2.z, b3 * this.a3.z));

        int colour = this.sampleTexture(u,v);//The ABGR colour of this pixel


        final int ALPHA_CUTOFF_THRESHOLD = 0;
        if ((meta&1)!=0 && (colour>>>24)<=ALPHA_CUTOFF_THRESHOLD) {//Discard on small alpha
            return;
        }

        //Stencil increment first
        this.framebuffer[index] += (1L<<32);

        //Funny jank depth test
        long depthVal = ((long) (((double)z)*((1<<24)-1)))<<(64-24);
        if (depthVal == DEPTH_MASK) depthVal--;//We wanto render _something_ at least
        if (Long.compareUnsigned(this.framebuffer[index],depthVal)<=0) {
            return;//Depth test failed, (using a strictly LESS_THAN comparison)
        }
        //Set the pixels depth value
        this.framebuffer[index] &= ~DEPTH_MASK;
        this.framebuffer[index] |= depthVal;

        //set the metadata bit
        this.framebuffer[index] &= ~(1L<<39);
        this.framebuffer[index] |= ((long)(meta&4))<<37;

        int srcColour = (int) this.framebuffer[index];
        this.framebuffer[index] &= ~Integer.toUnsignedLong(-1);

        //When blending is enabled do this
        // ARBDrawBuffersBlend.glBlendFuncSeparateiARB(0, GL_ONE_MINUS_DST_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        if (this.doTheBlending) {//Blending
            //mutate colour var
        }


        //Remember ABGR FORMAT
        this.framebuffer[index] |= Integer.toUnsignedLong(colour);
    }

    private static float edge(Vector3f a, Vector3f b, Vector3f c) {
        return (c.x-a.x)*(b.y-a.y) - (c.y-a.y) * (b.x-a.x);
    }

    private static float edge(Vector3f a, Vector3f b, float cx, float cy) {
        return (cx-a.x)*(b.y-a.y) - (cy-a.y) * (b.x-a.x);
    }


    private void loadTransformPos(Matrix4f transform, long addr, int vert, Vector3f out, Vector3f otherAttributesOut) {
        this.scratch.setFromAddress(addr+vert*ReuseVertexConsumer.VERTEX_FORMAT_SIZE);
        otherAttributesOut.setFromAddress(addr+vert*ReuseVertexConsumer.VERTEX_FORMAT_SIZE+3*4);
        this.scratch.w = 1.0f;
        var vec = transform.transformProject(this.scratch);
        if (Math.abs(this.scratch.w-1.0f)>0.000001f)
            throw new IllegalStateException();
        out.set(maintainPrecision(Math.fma(vec.x, 0.5f, 0.5f)*TARGET_SIZE), maintainPrecision(Math.fma(vec.y, 0.5f, 0.5f)*TARGET_SIZE), vec.z);//TODO: dont know if z transform is correct
    }


    private static float maintainPrecision(float x) {
        return x;//TODO: value snapping in screenspace if needed
    }


    public long[] getRawFramebuffer() {
        return this.framebuffer;
    }
}
