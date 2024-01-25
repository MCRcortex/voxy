package me.cortex.zenith.client.core.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import me.cortex.zenith.client.core.gl.GlFramebuffer;
import me.cortex.zenith.client.core.gl.GlTexture;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.LocalRandom;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glMemoryBarrier;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL45C.glBlitNamedFramebuffer;
import static org.lwjgl.opengl.GL45C.glGetTextureImage;

//Builds a texture for each face of a model
public class ModelTextureBakery {
    private final int width;
    private final int height;
    private final GlTexture colourTex;
    private final GlTexture depthTex;
    private final GlFramebuffer framebuffer;

    private static final List<MatrixStack> FACE_VIEWS = new ArrayList<>();
    static {
        addView(-90,0, 0);//Direction.DOWN
        addView(90,0, 0);//Direction.UP
        addView(0,180, 0);//Direction.NORTH
        addView(0,0, 0);//Direction.SOUTH
        //TODO: check these arnt the wrong way round
        addView(0,90, -90);//Direction.EAST
        addView(0,270, -90);//Direction.WEST
    }

    public ModelTextureBakery(int width, int height) {
        this.width = width;
        this.height = height;
        this.colourTex = new GlTexture().store(GL_RGBA8, 1, width, height);
        this.depthTex = new GlTexture().store(GL_DEPTH24_STENCIL8, 1, width, height);
        this.framebuffer = new GlFramebuffer().bind(GL_COLOR_ATTACHMENT0, this.colourTex).bind(GL_DEPTH_STENCIL_ATTACHMENT, this.depthTex).verify();
    }

    private static void addView(float pitch, float yaw, float rotation) {
        var stack = new MatrixStack();
        stack.translate(0.5f,0.5f,0.5f);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.translate(-0.5f,-0.5f,-0.5f);
        FACE_VIEWS.add(stack);
    }

    public ColourDepthTextureData[] renderFaces(BlockState state, long randomValue) {
        var model = MinecraftClient.getInstance()
                .getBakedModelManager()
                .getBlockModels()
                .getModel(state);

        int oldFB = GlStateManager.getBoundFramebuffer();
        var oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        GL11C.glViewport(0, 0, this.width, this.height);

        RenderSystem.setProjectionMatrix(new Matrix4f().identity().scale(2,2,-1f).translate(-0.5f, -0.5f, 0.0f), VertexSorter.BY_Z);

        glClearColor(0,0,0,0);
        glClearDepth(1);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.id);


        var renderLayer = RenderLayers.getBlockLayer(state);
        if (renderLayer == RenderLayer.getTranslucent()) {
            //TODO: TRANSLUCENT, must sort the quad first
        }
        renderLayer.startDrawing();
        RenderSystem.depthMask(true);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.depthFunc(GL_LESS);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);


        if (!state.getFluidState().isEmpty()) {
            //TODO: render fluid
        }

        var faces = new ColourDepthTextureData[FACE_VIEWS.size()];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = captureView(state, model, FACE_VIEWS.get(i), randomValue);
        }

        renderLayer.endDrawing();

        RenderSystem.setProjectionMatrix(oldProjection, VertexSorter.BY_DISTANCE);
        glBindFramebuffer(GL_FRAMEBUFFER, oldFB);
        GL11C.glViewport(GlStateManager.Viewport.getX(), GlStateManager.Viewport.getY(), GlStateManager.Viewport.getWidth(), GlStateManager.Viewport.getHeight());
        //glBlitNamedFramebuffer(this.framebuffer.id, oldFB, 0,0,16,16,0,0,256,256, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        return faces;
    }

    private ColourDepthTextureData captureView(BlockState state, BakedModel model, MatrixStack stack, long randomValue) {
        var vc = Tessellator.getInstance().getBuffer();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        vc.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        renderQuads(vc, state, model, stack, randomValue);
        BufferRenderer.drawWithGlobalProgram(vc.end());

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT);
        int[] colourData = new int[this.width*this.height];
        int[] depthData = new int[this.width*this.height];
        glGetTextureImage(this.colourTex.id, 0, GL_RGBA, GL_UNSIGNED_BYTE, colourData);
        glGetTextureImage(this.depthTex.id, 0, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, depthData);
        return new ColourDepthTextureData(colourData, depthData);
    }

    private static void renderQuads(BufferBuilder builder, BlockState state, BakedModel model, MatrixStack stack, long randomValue) {
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            var quads = model.getQuads(state, direction, new LocalRandom(randomValue));
            for (var quad : quads) {
                builder.quad(stack.peek(), quad, 0, 0, 0, 0, 0);
            }
        }
    }

    public void free() {
        this.framebuffer.free();
        this.colourTex.free();
        this.depthTex.free();
    }
}
