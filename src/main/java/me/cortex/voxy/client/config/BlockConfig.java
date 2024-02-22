package me.cortex.voxy.client.config;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class BlockConfig {
    public Block block = Blocks.AIR;
    public boolean ignoreBlock = false;
    public final Face[] faces = new Face[6];
    public float colourMultiplier = 1.0f;

    public BlockConfig() {
        for (int i = 0; i < this.faces.length; i++) {
            this.faces[i] = new Face();
        }
    }

    public static class Face {
        public BooleanChoice occludes = BooleanChoice.DEFAULT;
        public BooleanChoice canBeOccluded = BooleanChoice.DEFAULT;
    }

    public enum BooleanChoice {
        DEFAULT,
        TRUE,
        FALSE
    }
}
