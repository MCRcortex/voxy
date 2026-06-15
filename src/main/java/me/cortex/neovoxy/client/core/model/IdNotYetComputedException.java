package me.cortex.neovoxy.client.core.model;

public class IdNotYetComputedException extends RuntimeException {
    public final int id;
    public final boolean isIdBlockId;
    public int auxBitMsk;
    public long[] auxData;
    public IdNotYetComputedException(int id, boolean isIdBlockId) {
        super(null, null, false, false);
        this.id = id;
        this.isIdBlockId = isIdBlockId;
    }
}
