package me.cortex.voxy.client.core.rendering.util;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.glActiveTexture;

public class GlStateCapture {
    private final int[] capabilityIds;
    private final boolean[] enabledCaps;


    private final int[] textureUnits;
    private final int[] textures;
    private GlStateCapture(int[] caps, int[] textureUnits) {
        this.capabilityIds = caps;
        this.enabledCaps = new boolean[caps.length];

        this.textureUnits = textureUnits;
        this.textures = new int[textureUnits.length];
    }

    public void capture() {
        this.textureUnits[0] = glGetInteger(GL_ACTIVE_TEXTURE);
        //Capture all the texture data
        for (int i = 0; i < this.textures.length; i++) {
            glActiveTexture(this.textureUnits[i]);
            this.textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
        }
        //Reset the original active texture
        glActiveTexture(this.textureUnits[0]);

        for (int i = 0; i < this.capabilityIds.length; i++) {
            this.enabledCaps[i] = glIsEnabled(this.capabilityIds[i]);
        }
    }

    public void restore() {
        //Capture all the texture data
        for (int i = 1; i < this.textures.length; i++) {
            glActiveTexture(this.textureUnits[i]);
            glBindTexture(GL_TEXTURE_2D, this.textures[i]);
        }
        //Reset the original active texture
        glActiveTexture(this.textureUnits[0]);
        glBindTexture(GL_TEXTURE_2D, this.textures[0]);

        for (int i = 0; i < this.capabilityIds.length; i++) {
            if (this.enabledCaps[i]) {
                glEnable(this.capabilityIds[i]);
            } else {
                glDisable(this.capabilityIds[i]);
            }
        }
    }

    public static Builder make() {
        return new Builder();
    }

    public static class Builder {
        private final IntArrayList caps = new IntArrayList();
        private final IntArrayList textures = new IntArrayList();

        private Builder() {
            this.addTexture(-1);//Special texture unit, used to capture the current texture unit
        }

        public Builder addCapability(int cap) {
            this.caps.add(cap);
            return this;
        }

        public Builder addTexture(int unit) {
            this.textures.add(unit);
            return this;
        }

        public GlStateCapture build() {
            return new GlStateCapture(this.caps.toIntArray(), this.textures.toIntArray());
        }
    }
}
