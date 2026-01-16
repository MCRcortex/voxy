package me.cortex.voxy.client.core.model.bakery;

import me.cortex.voxy.common.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.RenderType;
// import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BakedBlockEntityModel {
    private record LayerConsumer(RenderType layer, ReuseVertexConsumer consumer) {}
    private final List<LayerConsumer> layers;
    private BakedBlockEntityModel(List<LayerConsumer> layers) {
        this.layers = layers;
    }

    public void render(Matrix4f matrix, int texId) {
        for (var layer : this.layers) {
            if (layer.consumer.isEmpty()) continue;
            if (layer.layer instanceof RenderType.CompositeRenderType mp) {
                ResourceLocation textureId = mp.state.textureState.cutoutTexture().orElse(null);
                if (textureId == null) {
                    Logger.error("ERROR: Empty texture id for layer: " + layer);
                } else {
                    texId = Minecraft.getInstance().getTextureManager().getTexture(textureId).getId();
                }
            }
            if (texId == 0) continue;
            BudgetBufferRenderer.setup(layer.consumer.getAddress(), layer.consumer.quadCount(), texId);
            BudgetBufferRenderer.render(matrix);
        }
    }

    public void release() {
        this.layers.forEach(layer->layer.consumer.free());
    }

    private static int getMetaFromLayer(RenderType layer) {
        boolean hasDiscard = layer == RenderType.cutout() ||
                layer == RenderType.cutoutMipped() ||
                layer == RenderType.tripwire();

        boolean isMipped = layer == RenderType.cutoutMipped() ||
                layer == RenderType.solid() ||
                layer.sortOnUpload() ||
                layer == RenderType.tripwire();

        int meta = hasDiscard?1:0;
        meta |= isMipped?2:0;
        return meta;
    }

    public static BakedBlockEntityModel bake(BlockState state) {
        Map<RenderType, LayerConsumer> map = new HashMap<>();
        var entity = ((EntityBlock)state.getBlock()).newBlockEntity(BlockPos.ZERO, state);
        if (entity == null) {
            return null;
        }
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        entity.setLevel(Minecraft.getInstance().level);
        if (renderer != null) {
            try {
                /*
                var rt = renderer.createRenderState();
                renderer.extractRenderState(entity, rt, 0.0f, new Vec3d(0,0,0), null);

                //TODO: FIXME: FINISH
                var cstate = new CameraRenderState();
                var queue = new SubmitNodeStorage();
                renderer.submit(rt, new MatrixStack(), queue, cstate);
                var qq = queue.order(0);
                qq.
                */
                //renderer.render(entity, 0.0f, new MatrixStack(), layer->map.computeIfAbsent(layer, rl -> new LayerConsumer(rl, new ReuseVertexConsumer().setDefaultMeta(getMetaFromLayer(rl)))).consumer, 0, 0, new Vec3d(0,0,0));
            } catch (Exception e) {
                Logger.error("Unable to bake block entity: " + entity, e);
            }
        }
        entity.setRemoved();
        if (map.isEmpty()) {
            return null;
        }
        for (var i : new ArrayList<>(map.values())) {
            if (i.consumer.isEmpty()) {
                map.remove(i.layer);
                i.consumer.free();
            }
        }
        if (map.isEmpty()) {
            return null;
        }
        return new BakedBlockEntityModel(new ArrayList<>(map.values()));
    }
}
