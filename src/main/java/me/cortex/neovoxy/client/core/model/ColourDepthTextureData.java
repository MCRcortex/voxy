package me.cortex.neovoxy.client.core.model;

import java.util.Arrays;

public record ColourDepthTextureData(int[] colour, int[] depth, int width, int height, int hash) {
    public ColourDepthTextureData(int[] colour, int[] depth, int width, int height) {
        this(colour, depth, width, height, width * 312337173 * (Arrays.hashCode(colour) ^ Arrays.hashCode(depth)) ^ height);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        var other = ((ColourDepthTextureData)obj);
        return this.hash == other.hash && Arrays.equals(other.colour, this.colour) && Arrays.equals(other.depth, this.depth);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public ColourDepthTextureData clone() {
        return new ColourDepthTextureData(Arrays.copyOf(this.colour, this.colour.length), Arrays.copyOf(this.depth, this.depth.length), this.width, this.height, this.hash);
    }
}
