package me.cortex.voxy.commonImpl;

import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class WorldIdentifier {
    private static final RegistryKey<DimensionType> NULL_DIM_KEY = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, Identifier.of("voxy:null_dimension_id"));

    public final RegistryKey<World> key;
    public final long biomeSeed;
    public final RegistryKey<DimensionType> dimension;//Maybe?
    private final transient long hashCode;
    @Nullable transient WeakReference<WorldEngine> cachedEngineObject;

    public WorldIdentifier(RegistryKey<World> key, long biomeSeed, @Nullable RegistryKey<DimensionType> dimension) {
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
                    other.key == this.key &&//other.key.equals(this.key) &&
                    other.dimension == this.dimension//other.dimension.equals(this.dimension)
                    ;
        }
        return false;
    }

    //Quick access utility method to get or create a world object in the current instance
    public WorldEngine getOrCreateEngine() {
        var instance = VoxyCommon.getInstance();
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
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        return instance.getNullable(this);
    }

    public static WorldIdentifier of(World world) {
        //Gets or makes an identifier for world
        if (world == null) {
            return null;
        }
        return ((IWorldGetIdentifier)world).voxy$getIdentifier();
    }

    //Common utility function to get or create a world engine
    public static WorldEngine ofEngine(World world) {
        var id = of(world);
        if (id == null) {
            return null;
        }
        return id.getOrCreateEngine();
    }

    public static WorldEngine ofEngineNullable(World world) {
        var id = of(world);
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

    private static long registryKeyHashCode(RegistryKey<?> key) {
        var A = key.getRegistry();
        var B = key.getValue();
        int a = A==null?0:A.hashCode();
        int b = B==null?0:B.hashCode();
        return (Integer.toUnsignedLong(a)<<32)|Integer.toUnsignedLong(b);
    }
}
