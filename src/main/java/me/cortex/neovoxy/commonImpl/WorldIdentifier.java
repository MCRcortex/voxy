package me.cortex.neovoxy.commonImpl;

import me.cortex.neovoxy.common.world.WorldEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class WorldIdentifier {
    private static final ResourceKey<DimensionType> NULL_DIM_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, ResourceLocation.parse("neovoxy:null_dimension_id"));

    public final ResourceKey<Level> key;
    public final long biomeSeed;
    public final ResourceKey<DimensionType> dimension;//Maybe?
    private final transient long hashCode;
    @Nullable transient WeakReference<WorldEngine> cachedEngineObject;

    public WorldIdentifier(@NotNull ResourceKey<Level> key, long biomeSeed, @Nullable ResourceKey<DimensionType> dimension) {
        if (key == null) {
            throw new IllegalStateException("Key cannot be null");
        }
        dimension = dimension==null?NULL_DIM_KEY:dimension;
        this.key = key;
        this.biomeSeed = biomeSeed;
        this.dimension = dimension;
        this.hashCode = mixStafford13(registryKeyHashCode(key))^mixStafford13(registryKeyHashCode(dimension))^mixStafford13(biomeSeed);
    }

    @Override
    public int hashCode() {
        return (int) this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof WorldIdentifier other) {
            return other.hashCode == this.hashCode &&
                    other.biomeSeed == this.biomeSeed &&
                    equal(other.key, this.key) &&//other.key.equals(this.key) &&
                    equal(other.dimension, this.dimension)//other.dimension.equals(this.dimension)
                    ;
        }
        return false;
    }

    private static <T> boolean equal(ResourceKey<T> a, ResourceKey<T> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        // MC 1.21.1: ResourceKey.identifier() → location()
        return a.registry().equals(b.registry()) && a.location().equals(b.location());
    }

    //Quick access utility method to get or create a world object in the current instance
    public WorldEngine getOrCreateEngine() {
        var instance = NeoVoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        var engine = instance.getOrCreate(this);
        if (engine==null) {
            throw new IllegalStateException("Engine null on creation");
        }
        return engine;
    }

    public WorldEngine getNullable() {
        var instance = NeoVoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        return instance.getNullable(this);
    }

    public static WorldIdentifier of(Level level) {
        //Gets or makes an identifier for world
        if (level == null) {
            return null;
        }
        return ((IWorldGetIdentifier)level).neovoxy$getIdentifier();
    }

    //Common utility function to get or create a world engine
    public static WorldEngine ofEngine(Level level) {
        var id = of(level);
        if (id == null) {
            return null;
        }
        return id.getOrCreateEngine();
    }

    public static WorldEngine ofEngineNullable(Level level) {
        var id = of(level);
        if (id == null) {
            return null;
        }
        return id.getNullable();
    }

    public static long mixStafford13(long seed) {
        seed += 918759875987111L;
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public long getLongHash() {
        return this.hashCode;
    }

    private static long registryKeyHashCode(ResourceKey<?> key) {
        var A = key.registry();
        // MC 1.21.1: ResourceKey.identifier() → location()
        var B = key.location();
        int a = A==null?0:A.hashCode();
        int b = B==null?0:B.hashCode();
        return (Integer.toUnsignedLong(a)<<32)|Integer.toUnsignedLong(b);
    }


    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public String getWorldId() {
        return getWorldId(this);
    }

    public static String getWorldId(WorldIdentifier identifier) {
        String data = identifier.biomeSeed + identifier.key.toString();
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32);
        } catch (
                NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
