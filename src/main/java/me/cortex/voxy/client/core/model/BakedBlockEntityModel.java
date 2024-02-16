package me.cortex.voxy.client.core.model;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBindTexture;

public class BakedBlockEntityModel {
    private static final class BakedVertices implements VertexConsumer {
        public final RenderLayer layer;
        private float cX, cY, cZ;
        private int cR, cG, cB, cA;
        private float cU, cV;

        private final List<int[]> vertices = new ArrayList<>();

        private BakedVertices(RenderLayer layer) {
            this.layer = layer;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.cX = (float) x;
            this.cY = (float) y;
            this.cZ = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            this.cR = 0;//red;
            this.cG = 0;//green;
            this.cB = 0;//blue;
            this.cA = alpha;
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.cU = u;
            this.cV = v;
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {

        }

        @Override
        public void unfixColor() {

        }

        @Override
        public void next() {
            this.vertices.add(new int[]{
                    Float.floatToIntBits(this.cX), Float.floatToIntBits(this.cY), Float.floatToIntBits(this.cZ),
                    this.cR, this.cG, this.cB, this.cA,
                    Float.floatToIntBits(this.cU), Float.floatToIntBits(this.cV)});
        }

        public void putInto(VertexConsumer vc) {
            for (var vert : this.vertices) {
                vc.vertex(Float.intBitsToFloat(vert[0]), Float.intBitsToFloat(vert[1]), Float.intBitsToFloat(vert[2]))
                        .color(vert[3], vert[4], vert[5], vert[6])
                        .texture(Float.intBitsToFloat(vert[7]), Float.intBitsToFloat(vert[8]))
                        .next();
            }
        }
    }

    private final List<BakedVertices> layers;
    private BakedBlockEntityModel(List<BakedVertices> layers) {
        this.layers = layers;
    }

    public void renderOut() {
        var vc = Tessellator.getInstance().getBuffer();
        for (var layer : this.layers) {
            vc.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR_TEXTURE);
            if (layer.layer instanceof RenderLayer.MultiPhase mp) {
                Identifier textureId = mp.phases.texture.getId().orElse(null);
                if (textureId == null) {
                    System.err.println("ERROR: Empty texture id for layer: " + layer);
                } else {
                    var texture = MinecraftClient.getInstance().getTextureManager().getTexture(textureId);
                    glBindTexture(GL_TEXTURE_2D, texture.getGlId());
                }
            }
            layer.putInto(vc);
            BufferRenderer.draw(vc.end());
        }
    }

    public static BakedBlockEntityModel bake(BlockState state) {
        Map<RenderLayer, BakedVertices> map = new HashMap<>();
        var entity = ((BlockEntityProvider)state.getBlock()).createBlockEntity(BlockPos.ORIGIN, state);
        if (entity == null) {
            return null;
        }
        var renderer = MinecraftClient.getInstance().getBlockEntityRenderDispatcher().get(entity);
        if (renderer != null) {
            entity.setWorld(MinecraftClient.getInstance().world);
            try {
                renderer.render(entity, 0.0f, new MatrixStack(), layer->map.computeIfAbsent(layer, BakedVertices::new), 0, 0);
            } catch (Exception e) {
                System.err.println("Unable to bake block entity: " + entity);
                e.printStackTrace();
            }
        }
        entity.markRemoved();
        if (map.isEmpty()) {
            return null;
        }
        return new BakedBlockEntityModel(map.values().stream().toList());
    }
}
