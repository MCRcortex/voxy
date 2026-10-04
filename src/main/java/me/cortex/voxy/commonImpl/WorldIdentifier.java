package me.cortex.voxy.commonImpl;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class WorldIdentifier {
    private static final ResourceKey<DimensionType> NULL_DIM_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse("voxy:null_dimension_id"));

    public final ResourceKey<Level> key;
    public final long biomeSeed;
    public final ResourceKey<DimensionType> dimension;//Maybe?

    private final transient long hashCode;

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
        return a.registry().equals(b.registry()) && a.identifier().equals(b.identifier());
    }

    //================================================================================
    //Util methods to access the engine neatly
    //================================================================================
    @Nullable private volatile WeakReference<WorldEngine> cachedEngineObject;
    private static final VarHandle CACHE_OBJECT_HANDLE;

    static {
        try {
            CACHE_OBJECT_HANDLE = MethodHandles.lookup().findVarHandle(WorldIdentifier.class, "cachedEngineObject", WeakReference.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
    private WorldEngine getFromCache() {
        final var ref = (WeakReference<WorldEngine>) CACHE_OBJECT_HANDLE.get(this);
        if (ref == null) return null;
        var engine = ref.get();
        if (engine != null && engine.isLive()) {
            engine.markActive();
            return engine;
        } else {
            if (CACHE_OBJECT_HANDLE.compareAndSet(this, ref, null)) {
                return null;
            }
            return getFromCache();//We have to try again (tailcall (please))
        }
    }

    private Object getOrDoWorld(boolean increment, boolean loadIfMissing) {
        var instance = VoxyCommon.getInstance();
        if (instance == null) {
            this.cachedEngineObject = null;
            return null;
        }
        Object worldOrFuture = this.getFromCache();
        if (worldOrFuture != null) {
            if (increment) ((WorldEngine)worldOrFuture).acquireRef();
            return worldOrFuture;
        }
        worldOrFuture = instance.getOrCreateLoadingFuture(this, increment, loadIfMissing);
        if (worldOrFuture instanceof WorldEngine engine) {
            if (engine.instanceIn != instance) throw new IllegalStateException();
            CACHE_OBJECT_HANDLE.compareAndSet(this, null, engine.weakSelfRef);
        }
        return worldOrFuture;
    }

    //Gets the engine if it exists and loaded
    public WorldEngine getEngineNullable() {
        var worldOrFuture = this.getOrDoWorld(false, false);
        if (worldOrFuture == null || worldOrFuture instanceof CompletableFuture<?>)
            return null;
        return (WorldEngine) worldOrFuture;
    }

    //Gets the engine now, if its not loaded, starts the loading and returns null
    public WorldEngine getNowOrStartEngineNullable() {
        var worldOrFuture = this.getOrDoWorld(false, true);
        if (worldOrFuture == null || worldOrFuture instanceof CompletableFuture<?>)
            return null;
        return (WorldEngine) worldOrFuture;
    }

    public Object getOrStartEngine(boolean increment) {
        return this.getOrDoWorld(increment, true);
    }

    /*
    public WorldEngine getNowOrCreateEngineBlocking(boolean incrementRef) {
        var ret = this.getNowOrCreateEngineFuture(incrementRef);
        if (ret instanceof CompletableFuture<?> cf) {
            try {
                return ((CompletableFuture<WorldEngine>)cf).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return (WorldEngine) ret;
    }*/

    public CompletableFuture<WorldEngine> getFutureNowOrCreateEngine(boolean incrementRef) {
        var ret = this.getOrDoWorld(incrementRef, true);
        if (ret instanceof CompletableFuture<?> cf) {
            return (CompletableFuture<WorldEngine>) cf;
        }
        return CompletableFuture.completedFuture((WorldEngine) ret);
    }

    //================================================================================

    public static WorldIdentifier of(Level level) {
        //Gets or makes an identifier for world
        if (level == null) {
            return null;
        }
        return ((IWorldGetIdentifier)level).voxy$getIdentifier();
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
        var B = key.identifier();
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

    @Override
    public String toString() {
        return "WorldIdentifier[" + this.key.identifier().toString() + ", " + this.biomeSeed + ", " + this.dimension.identifier().toString() + ']';
    }

    public static class GsonAdapter extends TypeAdapter<WorldIdentifier> {
        public static final GsonAdapter INSTANCE = new GsonAdapter();

        private GsonAdapter(){}

        @Override
        public void write(JsonWriter writer, WorldIdentifier identifier) throws IOException {
            writer.beginObject();

            writer.name("key");
            writer.value(identifier.key.identifier().toString());

            writer.name("biomeSeed");
            writer.value(identifier.biomeSeed);

            writer.name("dimension");
            writer.value(identifier.dimension.identifier().toString());

            writer.endObject();
        }


        private static final Gson GSON = new Gson();
        @Override
        public WorldIdentifier read(JsonReader reader) throws IOException {
            var obj = GSON.getAdapter(JsonElement.class).read(reader).getAsJsonObject();

            var sKey = obj.getAsJsonPrimitive("key").getAsString();
            long biomeSeed = obj.getAsJsonPrimitive("biomeSeed").getAsLong();
            var sDim = obj.getAsJsonPrimitive("dimension").getAsString();

            var key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(sKey));
            var dim = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.parse(sDim));
            return new WorldIdentifier(key, biomeSeed, dim);
        }
    }
}
