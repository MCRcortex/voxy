package me.cortex.voxelmon.core.world.other;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxelmon.core.util.MemoryBuffer;
import me.cortex.voxelmon.core.world.storage.StorageBackend;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.stat.Stat;
import net.minecraft.world.biome.Biome;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


//There are independent mappings for biome and block states, these get combined in the shader and allow for more
// variaty of things
public class Mapper {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;

    private final StorageBackend storage;
    public static final int UNKNOWN_MAPPING = -1;
    public static final int AIR = 0;

    private final Map<BlockState, StateEntry> block2stateEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<StateEntry> blockId2stateEntry = new ObjectArrayList<>();
    private final Map<String, BiomeEntry> biome2biomeEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<BiomeEntry> biomeId2biomeEntry = new ObjectArrayList<>();

    private Consumer<StateEntry> newStateCallback;
    private Consumer<BiomeEntry> newBiomeCallback;
    public Mapper(StorageBackend storage) {
        this.storage = storage;
        //Insert air since its a special entry (index 0)
        var airEntry = new StateEntry(0, Blocks.AIR.getDefaultState());
        block2stateEntry.put(airEntry.state, airEntry);
        blockId2stateEntry.add(airEntry);

        this.loadFromStorage();
    }

    public static boolean isTranslucent(long id) {
        //Atm hardcode to air
        return ((id>>27)&((1<<20)-1)) == 0;
    }

    public static boolean isAir(long id) {
        return ((id>>27)&((1<<20)-1)) == 0;
    }

    public void setCallbacks(Consumer<StateEntry> stateCallback, Consumer<BiomeEntry> biomeCallback) {
        this.newStateCallback = stateCallback;
        this.newBiomeCallback = biomeCallback;
    }

    private void loadFromStorage() {
        var mappings = this.storage.getIdMappings();
        List<StateEntry> sentries = new ArrayList<>();
        List<BiomeEntry> bentries = new ArrayList<>();
        for (var entry : mappings.int2ObjectEntrySet()) {
            int entryType = entry.getIntKey()>>>30;
            int id = entry.getIntKey() & ((1<<30)-1);
            if (entryType == BLOCK_STATE_TYPE) {
                var sentry = StateEntry.deserialize(id, entry.getValue());
                sentries.add(sentry);
                if (this.block2stateEntry.put(sentry.state, sentry) != null) {
                    throw new IllegalStateException("Multiple mappings for blockstate");
                }
            } else if (entryType == BIOME_TYPE) {
                var bentry = BiomeEntry.deserialize(id, entry.getValue());
                bentries.add(bentry);
                if (this.biome2biomeEntry.put(bentry.biome, bentry) != null) {
                    throw new IllegalStateException("Multiple mappings for biome entry");
                }
            } else {
                throw new IllegalStateException("Unknown entryType");
            }
        }

        //Insert into the arrays
        sentries.stream().sorted(Comparator.comparing(a->a.id)).forEach(entry -> {
            if (this.blockId2stateEntry.size() != entry.id) {
                throw new IllegalStateException("Block entry not ordered");
            }
            this.blockId2stateEntry.add(entry);
        });
        bentries.stream().sorted(Comparator.comparing(a->a.id)).forEach(entry -> {
            if (this.biomeId2biomeEntry.size() != entry.id) {
                throw new IllegalStateException("Biome entry not ordered");
            }
            this.biomeId2biomeEntry.add(entry);
        });

    }

    private StateEntry registerNewBlockState(BlockState state) {
        StateEntry entry = new StateEntry(this.blockId2stateEntry.size(), state);
        //this.block2stateEntry.put(state, entry);
        this.blockId2stateEntry.add(entry);

        byte[] serialized = entry.serialize();
        ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
        buffer.put(serialized);
        buffer.rewind();
        this.storage.putIdMapping(entry.id | (BLOCK_STATE_TYPE<<30), buffer);
        MemoryUtil.memFree(buffer);

        if (this.newStateCallback!=null)this.newStateCallback.accept(entry);
        return entry;
    }

    private BiomeEntry registerNewBiome(String biome) {
        BiomeEntry entry = new BiomeEntry(this.biome2biomeEntry.size(), biome);
        //this.biome2biomeEntry.put(biome, entry);
        this.biomeId2biomeEntry.add(entry);

        byte[] serialized = entry.serialize();
        ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
        buffer.put(serialized);
        buffer.rewind();
        this.storage.putIdMapping(entry.id | (BIOME_TYPE<<30), buffer);
        MemoryUtil.memFree(buffer);

        if (this.newBiomeCallback!=null)this.newBiomeCallback.accept(entry);
        return entry;
    }


    //TODO:FIXME: IS VERY SLOW NEED TO MAKE IT LOCK FREE, or at minimum use a concurrent map
    public long getBaseId(byte light, BlockState state, RegistryEntry<Biome> biome) {
        if (state.isAir()) return ((long)light)<<56;//Special case and fast return for air, dont care about the biome
        StateEntry sentry = this.block2stateEntry.computeIfAbsent(state, this::registerNewBlockState);

        String biomeId = biome.getKey().get().getValue().toString();
        BiomeEntry bentry = this.biome2biomeEntry.computeIfAbsent(biomeId, this::registerNewBiome);

        return (Byte.toUnsignedLong(light)<<56)|(Integer.toUnsignedLong(bentry.id) << 47)|(Integer.toUnsignedLong(sentry.id)<<27);
    }

    //TODO: fixme: synchronize access to this.blockId2stateEntry
    public StateEntry[] getStateEntries() {
        var set = new ArrayList<>(this.blockId2stateEntry);
        StateEntry[] out = new StateEntry[set.size()];
        int i = 0;
        for (var entry : set) {
            if (entry.id != i++) {
                throw new IllegalStateException();
            }
            out[i-1] = entry;
        }
        return out;
    }

    //TODO: fixme: synchronize access to this.biomeId2biomeEntry
    public BiomeEntry[] getBiomeEntries() {
        var set = new ArrayList<>(this.biomeId2biomeEntry);
        BiomeEntry[] out = new BiomeEntry[set.size()];
        int i = 0;
        for (var entry : set) {
            if (entry.id != i++) {
                throw new IllegalStateException();
            }
            out[i-1] = entry;
        }
        return out;
    }

    public static final class StateEntry {
        public final int id;
        public final BlockState state;
        public StateEntry(int id, BlockState state) {
            this.id = id;
            this.state = state;
        }

        public byte[] serialize() {
            try {
                var serialized = new NbtCompound();
                serialized.putInt("id", this.id);
                serialized.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, this.state).result().get());
                var out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(serialized, out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static StateEntry deserialize(int id, byte[] data) {
            try {
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtTagSizeTracker.ofUnlimitedBytes());
                if (compound.getInt("id") != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, compound.getCompound("block_state")).get().orThrow();
                return new StateEntry(id, state);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static final class BiomeEntry {
        public final int id;
        public final String biome;

        public BiomeEntry(int id, String biome) {
            this.id = id;
            this.biome = biome;
        }

        public byte[] serialize() {
            try {
                var serialized = new NbtCompound();
                serialized.putInt("id", this.id);
                serialized.putString("biome_id", this.biome);
                var out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(serialized, out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static BiomeEntry deserialize(int id, byte[] data) {
            try {
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtTagSizeTracker.ofUnlimitedBytes());
                if (compound.getInt("id") != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                String biome = compound.getString("biome_id");
                return new BiomeEntry(id, biome);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
