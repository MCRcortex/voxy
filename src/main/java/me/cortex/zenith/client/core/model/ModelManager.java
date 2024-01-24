package me.cortex.zenith.client.core.model;

import me.cortex.zenith.client.core.gl.GlBuffer;
import net.minecraft.block.BlockState;

//Manages the storage and updating of model states, textures and colours

//Also has a fast long[] based metadata lookup for when the terrain mesher needs to look up the face occlusion data
public class ModelManager {
    public static final int MODEL_SIZE = 64;
    private final ModelTextureBakery bakery = new ModelTextureBakery(16, 16);
    private final GlBuffer modelBuffer;
    private final long[] metadataCache;

    public ModelManager() {
        this.modelBuffer = new GlBuffer(MODEL_SIZE * (1<<16));
        this.metadataCache = new long[1<<16];
    }

    public void updateEntry(int id, BlockState blockState) {
        //This also checks if there is a block colour resolver for the given blockstate and marks that the block has a resolver
        var textureData = this.bakery.renderFaces(blockState, 123456);
    }

    public long getModelMetadata(int id) {
        return this.metadataCache[id];
    }

    public int getBufferId() {
        return this.modelBuffer.id;
    }

    public void free() {
        this.bakery.free();
        this.modelBuffer.free();
    }
}
