package me.cortex.voxy.client.core.model.bakery;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
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

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL45.glTextureBarrier;

public class ModelTextureBakery {
    //Note: the first bit of metadata is if alpha discard is enabled
    private static final Matrix4f[] VIEWS = new Matrix4f[6];

    private final GlViewCapture capture;
    private final ReuseVertexConsumer vc = new ReuseVertexConsumer();

    private final int width;
    private final int height;
    public ModelTextureBakery(int width, int height) {
        this.capture = new GlViewCapture(width, height);
        this.width = width;
        this.height = height;
    }

    public static int getMetaFromLayer(RenderLayer layer) {
        boolean hasDiscard = layer == RenderLayer.getCutout() ||
                layer == RenderLayer.getCutoutMipped() ||
                layer == RenderLayer.getTripwire();

        boolean isMipped = layer == RenderLayer.getCutoutMipped() ||
                layer == RenderLayer.getSolid() ||
                layer == RenderLayer.getTranslucent() ||
                layer == RenderLayer.getTripwire();

        int meta = hasDiscard?1:0;
        meta |= isMipped?2:0;
        return meta;
    }

    private void bakeBlockModel(BlockState state, RenderLayer layer) {
        var model = MinecraftClient.getInstance()
                .getBakedModelManager()
                .getBlockModels()
                .getModel(state);

        int meta = getMetaFromLayer(layer);

        for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
            for (var part : model.getParts(new LocalRandom(42L))) {
                var quads = part.getQuads(direction);
                for (var quad : quads) {
                    //TODO: add meta specifiying quad has a tint

                    this.vc.quad(quad, meta);
                }
            }
        }
    }


    private void bakeFluidState(BlockState state, RenderLayer layer, int face) {
        this.vc.setDefaultMeta(getMetaFromLayer(layer));//Set the meta while baking
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
                if (shouldReturnAirForFluid(pos, face)) {
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
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.getDefaultState().getFluidState();
                }

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
        }, this.vc, state, state.getFluidState());
        this.vc.setDefaultMeta(0);//Reset default meta
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var fv = Direction.byIndex(face).getVector();
        int dot = fv.getX()*pos.getX() + fv.getY()*pos.getY() + fv.getZ()*pos.getZ();
        return dot >= 1;
    }

    public void free() {
        this.capture.free();
        this.vc.free();
    }


    public void renderToStream(BlockState state, int streamBuffer, int streamOffset) {
        this.capture.clear();
        boolean isBlock = true;
        RenderLayer layer;
        if (state.getBlock() instanceof FluidBlock) {
            layer = RenderLayers.getFluidLayer(state.getFluidState());
            isBlock = false;
        } else {
            layer = RenderLayers.getBlockLayer(state);
        }

        //TODO: support block model entities
        BakedBlockEntityModel bbem = null;
        if (state.hasBlockEntity()) {
            bbem = BakedBlockEntityModel.bake(state);
        }

        //Setup GL state
        int[] viewdat = new int[4];
        int blockTextureId;

        {
            glEnable(GL_STENCIL_TEST);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            if (layer == RenderLayer.getTranslucent()) {
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_ONE_MINUS_DST_ALPHA, GL_DST_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            } else {
                glDisable(GL_BLEND);//FUCK YOU INTEL (screams), for _some reason_ discard or something... JUST DOESNT WORK??
                //glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_ONE, GL_ONE);
            }

            glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
            glStencilFunc(GL_ALWAYS, 1, 0xFF);
            glStencilMask(0xFF);

            glGetIntegerv(GL_VIEWPORT, viewdat);//TODO: faster way todo this, or just use main framebuffer resolution

            //Bind the capture framebuffer
            glBindFramebuffer(GL_FRAMEBUFFER, this.capture.framebuffer.id);

            var tex = MinecraftClient.getInstance().getTextureManager().getTexture(Identifier.of("minecraft", "textures/atlas/blocks.png")).getGlTexture();
            blockTextureId = ((net.minecraft.client.texture.GlTexture)tex).getGlId();
        }

        //TODO: fastpath for blocks
        if (isBlock) {
            this.vc.reset();
            this.bakeBlockModel(state, layer);
            if (!this.vc.isEmpty()) {//only render if there... is shit to render

                //Setup for continual emission
                BudgetBufferRenderer.setup(this.vc.getAddress(), this.vc.quadCount(), blockTextureId);//note: this.vc.buffer.address NOT this.vc.ptr

                var mat = new Matrix4f();
                for (int i = 0; i < VIEWS.length; i++) {
                    glViewport((i % 3) * this.width, (i / 3) * this.height, this.width, this.height);

                    //The projection matrix
                    mat.set(2, 0, 0, 0,
                            0, 2, 0, 0,
                            0, 0, -1f, 0,
                            -1, -1, 0, 1)
                            .mul(VIEWS[i]);

                    BudgetBufferRenderer.render(mat);
                }
            }
            glBindVertexArray(0);
        } else {//Is fluid, slow path :(
            if (!(state.getBlock() instanceof FluidBlock)) throw new IllegalStateException();

            var mat = new Matrix4f();
            for (int i = 0; i < VIEWS.length; i++) {
                this.vc.reset();
                this.bakeFluidState(state, layer, i);
                if (this.vc.isEmpty()) continue;
                BudgetBufferRenderer.setup(this.vc.getAddress(), this.vc.quadCount(), blockTextureId);

                glViewport((i % 3) * this.width, (i / 3) * this.height, this.width, this.height);

                //The projection matrix
                mat.set(2, 0, 0, 0,
                        0, 2, 0, 0,
                        0, 0, -1f, 0,
                        -1, -1, 0, 1)
                        .mul(VIEWS[i]);

                BudgetBufferRenderer.render(mat);
            }
            glBindVertexArray(0);
        }

        //Render block model entity data if it exists
        if (bbem != null) {
            //Rerender everything again ;-; but is ok (is not)

            var mat = new Matrix4f();
            for (int i = 0; i < VIEWS.length; i++) {
                glViewport((i % 3) * this.width, (i / 3) * this.height, this.width, this.height);

                //The projection matrix
                mat.set(2, 0, 0, 0,
                        0, 2, 0, 0,
                        0, 0, -1f, 0,
                        -1, -1, 0, 1)
                        .mul(VIEWS[i]);

                bbem.render(mat, blockTextureId);
            }
            glBindVertexArray(0);

            bbem.release();
        }


        //"Restore" gl state
        glViewport(viewdat[0], viewdat[1], viewdat[2], viewdat[3]);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_BLEND);

        //Finish and download
        glTextureBarrier();
        this.capture.emitToStream(streamBuffer, streamOffset);

        glBindFramebuffer(GL_FRAMEBUFFER, this.capture.framebuffer.id);
        glClearDepth(1);
        glClear(GL_DEPTH_BUFFER_BIT);
    }




    static  {
        //TODO: FIXME: need to bake in the correct orientation, HOWEVER some orientations require a flipped winding order!!!!

        addView(0, -90,0, 0, false);//Direction.DOWN
        addView(1, 90,0, 0, false);//Direction.UP
        addView(2, 0,180, 0, true);//Direction.NORTH
        addView(3, 0,0, 0, false);//Direction.SOUTH
        //TODO: check these arnt the wrong way round
        addView(4, 0,90, 270, false);//Direction.EAST
        addView(5, 0,270, 270, false);//Direction.WEST
    }

    private static void addView(int i, float pitch, float yaw, float rotation, boolean flipX) {
        var stack = new MatrixStack();
        stack.translate(0.5f,0.5f,0.5f);
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        stack.translate(-0.5f,-0.5f,-0.5f);
        VIEWS[i] = new Matrix4f(stack.peek().getPositionMatrix());
    }
}
