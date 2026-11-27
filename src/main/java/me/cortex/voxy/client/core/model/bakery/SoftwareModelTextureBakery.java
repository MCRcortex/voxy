package me.cortex.voxy.client.core.model.bakery;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.GlTexture;
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

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.ARBDirectStateAccess.glGetTextureImage;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11C.GL_RGBA;
import static org.lwjgl.opengl.GL12.GL_PACK_IMAGE_HEIGHT;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL30C.glBindFramebuffer;

public class SoftwareModelTextureBakery {
    //Note: the first bit of metadata is if alpha discard is enabled
    private static final Matrix4f[] VIEWS = new Matrix4f[6];

    private final ReuseVertexConsumer opaqueVC = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC = new ReuseVertexConsumer(1/*has discard*/);
    private final ReuseVertexConsumer opaqueVC2 = new ReuseVertexConsumer();
    private final ReuseVertexConsumer translucentVC2 = new ReuseVertexConsumer(1/*has discard*/);
    private final SoftwareRasterizer rasterizer = new SoftwareRasterizer(ModelFactory.MODEL_TEXTURE_SIZE);

    private final FluidRenderer fr;
    public SoftwareModelTextureBakery() {
        this.fr = new FluidRenderer(Minecraft.getInstance().getModelManager().getFluidStateModelSet());
    }

    public void setupTexture() {
        var tex = Minecraft.getInstance().getTextureManager().getTexture(Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/blocks.png")).getTexture();
        if (tex.getFormat() != GpuFormat.RGBA8_UNORM) {
            throw new IllegalStateException("Block atlas not rgba8: " + tex.getFormat());
        }

        int targetMipLevel = 0;// Math.min(tex.getMipLevels(), 4)-1;//todo: we want to target the mip layer that has the 16x16 sized textures

        int width = tex.getWidth(targetMipLevel);
        int height = tex.getHeight(targetMipLevel);

        //Just do it ourselves as doing it with b3d has some issues, (doing it ourselves is also just much much much shorter)
        var texture = new int[width * height];

        glFlush();
        glFinish();
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        glPixelStorei(GL_PACK_ROW_LENGTH, width);
        glPixelStorei(GL_PACK_IMAGE_HEIGHT, 0);
        glPixelStorei(GL_PACK_SKIP_ROWS, 0);
        glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_PACK_ALIGNMENT, 4);
        glGetTextureImage(((GlTexture) tex).glId(), 0, GL_RGBA, GL_UNSIGNED_BYTE, texture);
        this.rasterizer.setSamplerTexture(texture, width, height);
    }

    private static int classifyCrossQuad(BakedQuad quad) {
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            var pos = quad.position(i);
            minX = Math.min(minX, pos.x()); maxX = Math.max(maxX, pos.x());
            minY = Math.min(minY, pos.y()); maxY = Math.max(maxY, pos.y());
            minZ = Math.min(minZ, pos.z()); maxZ = Math.max(maxZ, pos.z());
        }
        float dx = maxX - minX;
        float dy = maxY - minY;
        float dz = maxZ - minZ;
        if (dx <= 0.15f || dy <= 0.15f || dz <= 0.15f || Math.abs(dx - dz) >= 0.2f) {
            return 0;
        }
        boolean diag1 = true;
        boolean diag2 = true;
        for (int i = 0; i < 4; i++) {
            var pos = quad.position(i);
            float tx = (pos.x() - minX) / dx;
            float tz = (pos.z() - minZ) / dz;
            if (Math.abs(tx - tz) > 0.15f) {
                diag1 = false;
            }
            if (Math.abs(tx - (1.0f - tz)) > 0.15f) {
                diag2 = false;
            }
        }
        if (diag1) return 1;
        if (diag2) return 2;
        return 0;
    }

    private boolean bakeBlockModel(BlockState state) {
        if (state.getRenderShape() == RenderShape.INVISIBLE) {
            return false;//Dont bake if invisible
        }
        var model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(state);

        List<BlockStateModelPart> out = new ArrayList<>();
        model.collectParts(new SingleThreadedRandomSource(42L), out);

        boolean canBeCross = !state.is(BlockTags.LEAVES);
        boolean hasDiag1 = false;
        boolean hasDiag2 = false;
        if (canBeCross) {
            outer:
            for (var part : out) {
                for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    if (!part.getQuads(direction).isEmpty()) {
                        canBeCross = false;
                        break outer;
                    }
                }
                var quads = part.getQuads(null);
                for (var quad : quads) {
                    int diag = classifyCrossQuad(quad);
                    if (diag == 1) {
                        hasDiag1 = true;
                    } else if (diag == 2) {
                        hasDiag2 = true;
                    } else {
                        canBeCross = false;
                        break outer;
                    }
                }
            }
        }

        boolean isCross = canBeCross && hasDiag1 && hasDiag2;
        boolean isLeaves = state.is(BlockTags.LEAVES);

        for (var part : out) {
            if (isCross) {
                for (var quad : part.getQuads(null)) {
                    int diag = classifyCrossQuad(quad);
                    boolean translucent = quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
                    if (diag == 1) {
                        (translucent ? this.translucentVC : this.opaqueVC).quad(quad, isLeaves);
                    } else {
                        (translucent ? this.translucentVC2 : this.opaqueVC2).quad(quad, isLeaves);
                    }
                }
            } else {
                for (Direction direction : new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null}) {
                    var quads = part.getQuads(direction);
                    for (var quad : quads) {
                        (quad.materialInfo().layer()==ChunkSectionLayer.TRANSLUCENT?this.translucentVC:this.opaqueVC)
                                .quad(quad, isLeaves);
                    }
                }
            }
        }
        return isCross;
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
        this.opaqueVC2.free();
        this.translucentVC2.free();
    }

    private static final long SINGLE_FACE_OUTPUT_SIZE = (ModelFactory.MODEL_TEXTURE_SIZE * ModelFactory.MODEL_TEXTURE_SIZE)*8;
    //The outputBuffer layout is different from the non software rasterized ModelTextureBakery
    // in this version the values are simply appended (0,0),(1,0),(2,0),(0,1),(1,1),(2,1)

    public int renderToOutput(BlockState state, long outputBuffer) {
        return renderToOutput(state, outputBuffer, false);
    }

    public int renderToOutput(BlockState state, long outputBuffer, boolean rasterAsUV) {
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
        boolean isCrossModel = false;
        if (isBlock) {
            this.opaqueVC.reset();
            this.translucentVC.reset();
            this.opaqueVC2.reset();
            this.translucentVC2.reset();
            isCrossModel = this.bakeBlockModel(state);
            isAnyShaded |= this.opaqueVC.anyShaded|this.translucentVC.anyShaded|this.opaqueVC2.anyShaded|this.translucentVC2.anyShaded;
            isAnyDarkend |= this.opaqueVC.anyDarkendTex|this.translucentVC.anyDarkendTex|this.opaqueVC2.anyDarkendTex|this.translucentVC2.anyDarkendTex;
            anyTranslucent |= !this.translucentVC.isEmpty()|!this.translucentVC2.isEmpty();
            anyDiscard |= this.opaqueVC.anyDiscard|this.opaqueVC2.anyDiscard;
            if (isCrossModel) {
                // Render Diagonal 1 into face 2 (NORTH) using VIEWS[3] (same projection/culling so it is rotated 90 deg, not mirrored)
                this.rasterizer.setFaceCull(false);
                this.rasterizer.clear();
                this.rasterizer.setUVRaster(rasterAsUV);
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[3], this.opaqueVC);
                this.rasterizer.setBlending(!rasterAsUV);
                this.rasterizer.raster(VIEWS[3], this.translucentVC);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*2));

                // Render Diagonal 2 into face 3 (SOUTH) using VIEWS[3]
                this.rasterizer.setFaceCull(false);
                this.rasterizer.clear();
                this.rasterizer.setUVRaster(rasterAsUV);
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[3], this.opaqueVC2);
                this.rasterizer.setBlending(!rasterAsUV);
                this.rasterizer.raster(VIEWS[3], this.translucentVC2);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*3));
            } else if (!(this.opaqueVC.isEmpty()&&this.translucentVC.isEmpty())) {//only render if there... is shit to render
                for (int i = 0; i < VIEWS.length; i++) {
                    this.rasterizer.setFaceCull(i==1||i==2||i==4);
                    this.rasterizer.clear();
                    this.rasterizer.setUVRaster(rasterAsUV);
                    this.rasterizer.setBlending(false);
                    this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                    this.rasterizer.setBlending(!rasterAsUV);
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
                this.rasterizer.setUVRaster(rasterAsUV);
                this.rasterizer.setBlending(false);
                this.rasterizer.raster(VIEWS[i], this.opaqueVC);
                this.rasterizer.setBlending(!rasterAsUV);
                this.rasterizer.raster(VIEWS[i], this.translucentVC);
                UnsafeUtil.memcpy(this.rasterizer.getRawFramebuffer(), outputBuffer+(SINGLE_FACE_OUTPUT_SIZE*i));
            }
        }

        return (isAnyShaded?1:0)|(isAnyDarkend?2:0)|(anyTranslucent?4:0)|(anyDiscard?8:0)|(isCrossModel?16:0);
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
        stack.rotate((makeQuatFromAxisExact(new Vector3f(0,0,1), rotation)));
        stack.rotate(makeQuatFromAxisExact(new Vector3f(1,0,0), pitch));
        stack.rotate(makeQuatFromAxisExact(new Vector3f(0,1,0), yaw));
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
