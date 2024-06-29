package me.cortex.voxy.client.core.model;

import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import me.cortex.voxy.client.core.gl.GlFramebuffer;
import me.cortex.voxy.client.core.gl.GlTexture;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.util.GlStateCapture;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.LocalRandom;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.LightType;
import net.minecraft.world.biome.ColorResolver;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.ARBFramebufferObject.*;
import static org.lwjgl.opengl.ARBImaging.GL_FUNC_ADD;
import static org.lwjgl.opengl.ARBImaging.glBlendEquation;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.GL_FRAMEBUFFER_BARRIER_BIT;
import static org.lwjgl.opengl.ARBShaderImageLoadStore.glMemoryBarrier;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL45C.glBlitNamedFramebuffer;
import static org.lwjgl.opengl.GL45C.glGetTextureImage;

//Builds a texture for each face of a model
public class ModelTextureBakery {
    private final int width;
    private final int height;
    private final GlTexture colourTex;
    private final GlTexture depthTex;
    private final GlFramebuffer framebuffer;
    private final GlStateCapture glState = GlStateCapture.make()
            .addCapability(GL_DEPTH_TEST)
            .addCapability(GL_STENCIL_TEST)
            .addCapability(GL_BLEND)
            .addCapability(GL_CULL_FACE)
            .addTexture(GL_TEXTURE0)
            .addTexture(GL_TEXTURE1)
            .build()
            ;
    private final Shader rasterShader = Shader.make()
            .add(ShaderType.VERTEX, "voxy:bakery/position_tex.vsh")
            .add(ShaderType.FRAGMENT, "voxy:bakery/position_tex.fsh")
            .compile();

    private static final List<MatrixStack> FACE_VIEWS = new ArrayList<>();


    public ModelTextureBakery(int width, int height) {
        //TODO: Make this run in a seperate opengl context so that it can run in a seperate thread


        this.width = width;
        this.height = height;
        this.colourTex = new GlTexture().store(GL_RGBA8, 1, width, height);
        this.depthTex = new GlTexture().store(GL_DEPTH24_STENCIL8, 1, width, height);
        this.framebuffer = new GlFramebuffer().bind(GL_COLOR_ATTACHMENT0, this.colourTex).bind(GL_DEPTH_STENCIL_ATTACHMENT, this.depthTex).verify();

        //This is done to help make debugging easier
        FACE_VIEWS.clear();
        AddViews();
    }

    private static void AddViews() {
        addView(-90,0, 0, false);//Direction.DOWN
        addView(90,0, 0, false);//Direction.UP
        addView(0,180, 0, true);//Direction.NORTH
        addView(0,0, 0, false);//Direction.SOUTH
        //TODO: check these arnt the wrong way round
        addView(0,90, 270, false);//Direction.EAST
        addView(0,270, 270, false);//Direction.WEST
    }

    private static void addView(float pitch, float yaw, float rotation, boolean flipX) {
        var stack = new MatrixStack();
        stack.translate(0.5f,0.5f,0.5f);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.translate(-0.5f,-0.5f,-0.5f);
        FACE_VIEWS.add(stack);
    }



    //TODO: For block entities, also somehow attempt to render the default block entity, e.g. chests and stuff
    // cause that will result in ok looking micro details in the terrain
    public ColourDepthTextureData[] renderFaces(BlockState state, long randomValue, boolean renderFluid) {
        this.glState.capture();
        var model = MinecraftClient.getInstance()
                .getBakedModelManager()
                .getBlockModels()
                .getModel(state);

        var entityModel = state.hasBlockEntity()?BakedBlockEntityModel.bake(state):null;

        int oldFB = GlStateManager.getBoundFramebuffer();
        var oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        GL11C.glViewport(0, 0, this.width, this.height);

        RenderSystem.setProjectionMatrix(new Matrix4f().identity().set(new float[]{
                2,0,0,0,
                0, 2,0,0,
                0,0, -1f,0,
                -1,-1,0,1,
        }), VertexSorter.BY_Z);



        RenderLayer renderLayer = null;
        if (!renderFluid) {
            renderLayer = RenderLayers.getBlockLayer(state);
        } else {
            renderLayer = RenderLayers.getFluidLayer(state.getFluidState());
        }


        //TODO: figure out why calling this makes minecraft render black
        //renderLayer.startDrawing();
        glClearColor(0,0,0,0);
        glClearDepth(1);
        glBindFramebuffer(GL_FRAMEBUFFER, this.framebuffer.id);

        glEnable(GL_STENCIL_TEST);
        glDepthRange(0, 1);
        glDepthMask(true);
        glEnable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        //glDepthFunc(GL_LESS);


        //TODO: Find a better solution
        if (renderLayer == RenderLayer.getTranslucent()) {
            //Very hacky blend function to retain the effect of the applied alpha since we dont really want to apply alpha
            // this is because we apply the alpha again when rendering the terrain meaning the alpha is being double applied
            glBlendFuncSeparate(GL_ONE_MINUS_DST_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        }


        //glBlendFunc(GL_ONE, GL_ONE);

        glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilMask(0xFF);

        this.rasterShader.bind();
        glActiveTexture(GL_TEXTURE0);
        int texId = MinecraftClient.getInstance().getTextureManager().getTexture(Identifier.of("minecraft", "textures/atlas/blocks.png")).getGlId();
        GlUniform.uniform1(0, 0);

        var faces = new ColourDepthTextureData[FACE_VIEWS.size()];
        for (int i = 0; i < faces.length; i++) {
            faces[i] = captureView(state, model, entityModel, FACE_VIEWS.get(i), randomValue, i, renderFluid, texId);
            //glBlitNamedFramebuffer(this.framebuffer.id, oldFB, 0,0,16,16,300*(i>>1),300*(i&1),300*(i>>1)+256,300*(i&1)+256, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        }

        renderLayer.endDrawing();
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_BLEND);

        RenderSystem.setProjectionMatrix(oldProjection, VertexSorter.BY_DISTANCE);
        glBindFramebuffer(GL_FRAMEBUFFER, oldFB);
        GL11C.glViewport(GlStateManager.Viewport.getX(), GlStateManager.Viewport.getY(), GlStateManager.Viewport.getWidth(), GlStateManager.Viewport.getHeight());

        //TODO: FIXME: fully revert the state of opengl

        this.glState.restore();
        return faces;
    }


    private ColourDepthTextureData captureView(BlockState state, BakedModel model, BakedBlockEntityModel blockEntityModel, MatrixStack stack, long randomValue, int face, boolean renderFluid, int textureId) {
        var vc = Tessellator.getInstance();
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        float[] mat = new float[4*4];
        new Matrix4f(RenderSystem.getProjectionMatrix()).mul(stack.peek().getPositionMatrix()).get(mat);
        glUniformMatrix4fv(1, false, mat);


        if (blockEntityModel != null && !renderFluid) {
            blockEntityModel.renderOut();
        }

        var bb = vc.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        if (!renderFluid) {
            renderQuads(bb, state, model, new MatrixStack(), randomValue);
        } else {
            MinecraftClient.getInstance().getBlockRenderManager().renderFluid(BlockPos.ORIGIN, new BlockRenderView() {
                @Override
                public float getBrightness(Direction direction, boolean shaded) {
                    return 0;
                }

                @Override
                public LightingProvider getLightingProvider() {
                    return null;
                }

                @Override
                public int getLightLevel(LightType type, BlockPos pos) {
                    return 0;
                }

                @Override
                public int getColor(BlockPos pos, ColorResolver colorResolver) {
                    return 0;
                }

                @Nullable
                @Override
                public BlockEntity getBlockEntity(BlockPos pos) {
                    return null;
                }

                @Override
                public BlockState getBlockState(BlockPos pos) {
                    if (pos.equals(Direction.byId(face).getVector())) {
                        return Blocks.AIR.getDefaultState();
                    }

                    //Fixme:
                    // This makes it so that the top face of water is always air, if this is commented out
                    //  the up block will be a liquid state which makes the sides full
                    // if this is uncommented, that issue is fixed but e.g. stacking water layers ontop of eachother
                    //  doesnt fill the side of the block

                    //if (pos.getY() == 1) {
                    //    return Blocks.AIR.getDefaultState();
                    //}
                    return state;
                }

                @Override
                public FluidState getFluidState(BlockPos pos) {
                    if (pos.equals(Direction.byId(face).getVector())) {
                        return Blocks.AIR.getDefaultState().getFluidState();
                    }
                    //if (pos.getY() == 1) {
                    //    return Blocks.AIR.getDefaultState().getFluidState();
                    //}
                    return state.getFluidState();
                }

                @Override
                public int getHeight() {
                    return 0;
                }

                @Override
                public int getBottomY() {
                    return 0;
                }
            }, bb, state, state.getFluidState());
        }

        glBindTexture(GL_TEXTURE_2D, textureId);
        try {
            BufferRenderer.draw(bb.end());
        } catch (IllegalStateException e) {
            System.err.println("Got empty buffer builder! for block " + state);
        }

        glMemoryBarrier(GL_FRAMEBUFFER_BARRIER_BIT);
        int[] colourData = new int[this.width*this.height];
        int[] depthData = new int[this.width*this.height];
        glGetTextureImage(this.colourTex.id, 0, GL_RGBA, GL_UNSIGNED_BYTE, colourData);
        glGetTextureImage(this.depthTex.id, 0, GL_DEPTH_STENCIL, GL_UNSIGNED_INT_24_8, depthData);
        return new ColourDepthTextureData(colourData, depthData, this.width, this.height);
    }

    private static void renderQuads(BufferBuilder builder, BlockState state, BakedModel model, MatrixStack stack, long randomValue) {
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            var quads = model.getQuads(state, direction, new LocalRandom(randomValue));
            for (var quad : quads) {
                //TODO: mark pixels that have
                int meta = quad.hasColor()?1:0;
                builder.quad(stack.peek(), quad, 255f/((meta>>16)&0xff), 255f/((meta>>8)&0xff), 255f/(meta&0xff), 1.0f, 0, 0);
            }
        }
    }

    public void free() {
        this.framebuffer.free();
        this.colourTex.free();
        this.depthTex.free();
        this.rasterShader.free();
    }
}
