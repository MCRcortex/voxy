package me.cortex.zenith.client.config;

public class ZenithConfig {
    int qualityScale;
    int ingestThreads;
    int savingThreads;
    int renderThreads;
    int savingCompressionLevel;
    StorageConfig storageConfig;

    public static abstract class StorageConfig { }
    public static class FragmentedStorageConfig extends StorageConfig { }
    public static class LmdbStorageConfig extends StorageConfig { }

}
