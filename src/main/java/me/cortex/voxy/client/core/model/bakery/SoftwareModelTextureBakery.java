package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cortex.voxy.client.core.model.ModelFactory;
import me.cortex.voxy.common.util.UnsafeUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL11.glFinish;

public class SoftwareModelTextureBakery {
    //Note: the first bit of metadata is if alpha discard is enabled
    private static final Matrix4f[] VIEWS = new Matrix4f[6];

    private final ReuseVertexConsumer opaqueVC = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC = new ReuseVertexConsumer(1/*has discard*/);
    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer();

    private final FluidRenderer fr;
    public SoftwareModelTextureBakery() {
        this.fr = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
    }

    public void setupTexture() {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")).getTexture();
        if (tex.getFormat() != TextureFormat.RGBA8) {
            throw new IllegalStateException("Block atlas not rgba8");
        }

        int targetMipLevel = 0;// Math.min(tex.getMipLevels(), 4)-1;//todo: we want to target the mip layer that has the 16x16 sized textures

        int width = tex.getWidth(targetMipLevel);
        int height = tex.getHeight(targetMipLevel);
        GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture output buffer", 9, (4L*width)*height);//USAGE_COPY_SRC|USAGE_MAP_READ
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        boolean[] done = new boolean[1];
        Runnable runnable = () -> {
            var texture = new int[width*height];
            final long BATCH_SIZE = (1L<<31)-(1L<<20);
            for (long offset = 0; offset < (4L*width)*height; offset += BATCH_SIZE) {
                long size = Math.min((4L*width)*height-offset, BATCH_SIZE);
                try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(gpuBuffer.slice(offset, size), true, false)) {
                    mappedView.data().asIntBuffer().get(texture, (int) (offset/4), (int) (size/4));
                }
            }
            this.rasterizer.setSamplerTexture(texture, width, height);
            gpuBuffer.close();
            done[0] = true;
        };

        commandEncoder.copyTextureToBuffer(tex, gpuBuffer, 0, runnable, targetMipLevel);
        glFinish();//Required for intel since they dont insert there own flush in, causing this loop to never exit
        while (!done[0]) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            RenderSystem.executePendingTasks();
        }
    }

    private void bakeBlockModel(BlockState state) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return;//Dont bake if invisible
        }
        var model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);

        List<BlockStateModelPart> out = new ArrayList<>();
        model.collectParts(new SingleThreadedRandomSource(42L), out);
        for (var part : out) {
            for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
                var quads = part.getQuads(direction);
                for (var quad : quads) {
                    (quad.materialInfo().layer()==ChunkSectionLayer.TRANSLUCENT?this.translucentVC:this.opaqueVC)
                            .quad(quad, state.is(BlockTags.LEAVES));
                }
            }
        }
    }


    private void bakeFluidState(BlockState state, int face) {
        this.fr.tesselate(new BlockAndTintGetter() {
            @Override
            public LevelLightEngine getLightEngine() {
                return LevelLightEngine.EMPTY;
            }

            @Override
            public int getBrightness(LightLayer type, BlockPos pos) {
                return 0;
            }

            @Override
            public CardinalLighting cardinalLighting() {
                return CardinalLighting.DEFAULT;
            }

            @Override
            public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
                //This is such a stupid and bad hack, we can inject tinting state here since this is called
                // before the quad is added
                //TODO: need to make a quad once tinting thing
                translucentVC.setDefaultMeta(translucentVC.getDefaultMeta()|4);//Tinting
                opaqueVC.setDefaultMeta(opaqueVC.getDefaultMeta()|4);//Tinting
                return -1;
            }

            @Nullable
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                if (shouldReturnAirForFluid(pos, face)) {
                    return Blocks.AIR.defaultBlockState();
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
                    return Blocks.AIR.defaultBlockState().getFluidState();
                }

                return state.getFluidState();
            }

            @Override
            public int getHeight() {
                return 0;
            }

            @Override
            public int getMinY() {
                return 0;
            }
        }, BlockPos.ZERO, layer->{
            if (layer == ChunkSectionLayer.TRANSLUCENT) return this.translucentVC;
            if (layer == ChunkSectionLayer.CUTOUT) {
                this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta()|1);//set discard
            } else {
                this.opaqueVC.setDefaultMeta(this.opaqueVC.getDefaultMeta()&~1);//remove discard
            }
            return this.opaqueVC;
        }, state, state.getFluidState());
        this.translucentVC.setDefaultMeta(0);//Reset default meta
        this.opaqueVC.setDefaultMeta(0);//Reset default meta
    }

    private static boolean shouldReturnAirForFluid(BlockPos pos, int face) {
        var fv = Direction.from3DDataValue(face).getUnitVec3i();
        int dot = fv.getX()*pos.getX() + fv.getY()*pos.getY() + fv.getZ()*pos.getZ();
        return dot >= 1;
    }

    public void free() {
        this.opaqueVC.free();
        this.translucentVC.free();
    }

    private static final long SINGLE_FACE_OUTPUT_SIZE = (ModelFactory.MODEL_TEXTURE_SIZE * ModelFactory.MODEL_TEXTURE_SIZE)*8;
    //The outputBuffer layout is different from the non software rasterized ModelTextureBakery
    // in this version the values are simply appended (0,0),(1,0),(2,0),(0,1),(1,1),(2,1)

    public int renderToOutput(BlockState state, long outputBuffer) {
        MemoryUtil.memSet(outputBuffer,0,16*16*8*6);


        boolean isBlock = true;
        if (state.getBlock() instanceof LiquidBlock) {
            isBlock = false;
        }

        //TODO: support block model entities
        //BakedBlockEntityModel bbem = null;
        if (state.hasBlockEntity()) {
            //bbem = BakedBlockEntityModel.bake(state);
        }

        boolean isAnyShaded = false;
        boolean isAnyDarkend = false;
        boolean anyTranslucent = false;
        boolean anyDiscard = false;
        if (isBlock) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            this.bakeBlockModel(state);
            isAnyShaded |= this.opaqueVC.anyShaded|this.translucentVC.anyShaded;
            isAnyDarkend |= this.opaqueVC.anyDarkendTex|this.translucentVC.anyDarkendTex;
            anyTranslucent |= !this.translucentVC.isEmpty();
            anyDiscard |= this.opaqueVC.anyDiscard;
            if (!(this.opaqueVC.isEmpty()&&this.translucentVC.isEmpty())) {//only render if there... is shit to render
                for (int i = 0; i < VIEWS.length; i++) {
                    this.rasterizer.setFaceCull(i==1||i==2||i==4);
                    this.rasterizer.clear();
                    this.rasterizer.setBlending(false);
                    this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                    this.rasterizer.setBlending(true);
                    this.rasterizer.raster(VIEWS[i], this.translucentVC);
                    UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*i));
                }
            }
        } else {//Is fluid, slow path :(

            if (!(state.getBlock() instanceof LiquidBlock)) throw new IllegalStateException();
            for (int i = 0; i < VIEWS.length; i++) {
                this.opaqueVC.reset();
                this.translucentVC.reset();
                this.bakeFluidState(state, i);
                if (this.opaqueVC.isEmpty()&&this.translucentVC.isEmpty()) continue;
                isAnyShaded |= this.opaqueVC.anyShaded|this.translucentVC.anyShaded;
                isAnyDarkend |= this.opaqueVC.anyDarkendTex|this.translucentVC.anyDarkendTex;
                anyTranslucent |= !this.translucentVC.isEmpty();
                anyDiscard |= this.opaqueVC.anyDiscard;

                this.rasterizer.setFaceCull(i==1||i==2||i==4);

                //The projection matrix
                this.rasterizer.clear();
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                this.rasterizer.setBlending(true);
                this.rasterizer.raster(VIEWS[i], this.translucentVC);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*i));
            }
        }

        return (isAnyShaded?1:0)|(isAnyDarkend?2:0)|(anyTranslucent?4:0)|(anyDiscard?8:0);
    }




    static {
        //the face/direction is the face (e.g. down is the down face)
        addView(0, -90,0, 0, 0);//Direction.DOWN
        addView(1, 90,0, 0, 0b100);//Direction.UP

        addView(2, 0,180, 0, 0b001);//Direction.NORTH
        addView(3, 0,0, 0, 0);//Direction.SOUTH

        addView(4, 0,90, 270, 0b100);//Direction.WEST
        addView(5, 0,270, 270, 0);//Direction.EAST
    }

    private static void addView(int i, float pitch, float yaw, float rotation, int flip) {
        var stack = new PoseStack();
        stack.translate(0.5f,0.5f,0.5f);
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0,0,1), rotation));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(1,0,0), pitch));
        stack.mulPose(makeQuatFromAxisExact(new Vector3f(0,1,0), yaw));
        stack.mulPose(new Matrix4f().scale(1-2*(flip&1), 1-(flip&2), 1-((flip>>1)&2)));
        stack.translate(-0.5f,-0.5f,-0.5f);
        var mat = new Matrix4f(stack.last().pose());

        mat = new Matrix4f().set(
                        2,0,0,0,
                        0,2,0,0,
                        0,0,-2,0,
                        -1,-1,1,1)
                .mul(mat);
        VIEWS[i] = mat;
    }

    private static Quaternionf makeQuatFromAxisExact(Vector3f vec, float angle) {
        angle = (float) Math.toRadians(angle);
        float hangle = angle / 2.0f;
        float sinAngle = (float) Math.sin(hangle);
        float invVLength = (float) (1/Math.sqrt(vec.lengthSquared()));
        return new Quaternionf(vec.x * invVLength * sinAngle,
                vec.y * invVLength * sinAngle,
                vec.z * invVLength * sinAngle,
                Math.cos(hangle));
    }
}
