package me.cortex.voxy.api;

import me.cortex.voxy.common.world.WorldSection;

public class VoxyApi {
    private VoxyApi(String useIdentifier) {

    }

    public static VoxyApi makeAPI(String identifier) {
        return new VoxyApi(identifier);
    }

    public interface ArbitraryGenerateCallback {
        int ALL_DARK = -1;
        int OK = 0;
        int ALL_SKY = 1;
        int generate(int lodLevel, int x, int y, int z, long[] outputBuffer);
    }

    public void setArbitraryGenerator(ArbitraryGenerateCallback generator) {

    }

    public WorldSection acquireSection(int level, int x, int y, int z) {
        return null;
    }
}
