package me.cortex.voxy.client.core.model;

import java.util.Arrays;

public record ColourDepthTextureData(int[] colour, int[] depth, int width, int height) {
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        var other = ((ColourDepthTextureData)obj);
        return Arrays.equals(other.colour, this.colour) && Arrays.equals(other.depth, this.depth);
    }

    @Override
    public int hashCode() {
        return (this.width * 312337173 * (Arrays.hashCode(this.colour) ^ Arrays.hashCode(this.depth))) ^ this.height;
    }

    @Override
    public ColourDepthTextureData clone() {
        return new ColourDepthTextureData(Arrays.copyOf(this.colour, this.colour.length), Arrays.copyOf(this.depth, this.depth.length), this.width, this.height);
    }
}
