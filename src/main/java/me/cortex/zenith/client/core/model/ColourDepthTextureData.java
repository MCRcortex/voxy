package me.cortex.zenith.client.core.model;

import java.util.Arrays;

public record ColourDepthTextureData(int[] colour, int[] depth) {
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        var other = ((ColourDepthTextureData)obj);
        return Arrays.equals(other.colour, this.colour) && Arrays.equals(other.depth, this.depth);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(this.colour) ^ Arrays.hashCode(this.depth);
    }
}
