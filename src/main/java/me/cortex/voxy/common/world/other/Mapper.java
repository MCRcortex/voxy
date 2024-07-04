package me.cortex.voxy.common.world.other;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.storage.StorageBackend;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Pair;
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
    public static final long UNKNOWN_MAPPING = -1;
    public static final long AIR = 0;

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
        this.block2stateEntry.put(airEntry.state, airEntry);
        this.blockId2stateEntry.add(airEntry);

        this.loadFromStorage();
    }


    public static boolean isAir(long id) {
        return ((id>>27)&((1<<20)-1)) == 0;
    }

    public static int getBlockId(long id) {
        return (int) ((id>>27)&((1<<20)-1));
    }

    public static int getBiomeId(long id) {
        return (int) ((id>>47)&0x1FF);
    }

    public static int getLightId(long id) {
        return (int) ((id>>56)&0xFF);
    }

    public static long withLight(long id, int light) {
        return (id&(~(0xFFL<<56)))|(Integer.toUnsignedLong(light)<<56);
    }

    public void setCallbacks(Consumer<StateEntry> stateCallback, Consumer<BiomeEntry> biomeCallback) {
        this.newStateCallback = stateCallback;
        this.newBiomeCallback = biomeCallback;
    }

    private void loadFromStorage() {
        var mappings = this.storage.getIdMappingsData();
        List<StateEntry> sentries = new ArrayList<>();
        List<BiomeEntry> bentries = new ArrayList<>();
        List<Pair<byte[], Integer>> sentryErrors = new ArrayList<>();


        for (var entry : mappings.int2ObjectEntrySet()) {
            int entryType = entry.getIntKey()>>>30;
            int id = entry.getIntKey() & ((1<<30)-1);
            if (entryType == BLOCK_STATE_TYPE) {
                var sentry = StateEntry.deserialize(id, entry.getValue());
                if (sentry.state.isAir()) {
                    System.err.println("Deserialization was air, removed block");
                    sentryErrors.add(new Pair<>(entry.getValue(), id));
                    continue;
                }
                sentries.add(sentry);
                var oldEntry = this.block2stateEntry.put(sentry.state, sentry);
                if (oldEntry != null) {
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

        {
            //Insert garbage types into the mapping for those blocks, TODO:FIXME: Need to upgrade the type or have a solution to error blocks
            var rand = new Random();
            for (var error : sentryErrors) {
                while (true) {
                    var state = new StateEntry(error.getRight(), Block.STATE_IDS.get(rand.nextInt(Block.STATE_IDS.size() - 1)));
                    if (this.block2stateEntry.put(state.state, state) == null) {
                        sentries.add(state);
                        break;
                    }
                }
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

    private synchronized StateEntry registerNewBlockState(BlockState state) {
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

    private synchronized BiomeEntry registerNewBiome(String biome) {
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
        return composeMappingId(light, this.getIdForBlockState(state), this.getIdForBiome(biome));
    }

    public BlockState getBlockStateFromBlockId(int blockId) {
        return this.blockId2stateEntry.get(blockId).state;
    }

    //TODO: replace lambda with a class cached lambda ref (cause doing this:: still does a lambda allocation)
    public int getIdForBlockState(BlockState state) {
        return this.block2stateEntry.computeIfAbsent(state, this::registerNewBlockState).id;
    }


    //TODO: replace lambda with a class cached lambda ref (cause doing this:: still does a lambda allocation)
    public int getIdForBiome(RegistryEntry<Biome> biome) {
        String biomeId = biome.getKey().get().getValue().toString();
        return this.biome2biomeEntry.computeIfAbsent(biomeId, this::registerNewBiome).id;
    }

    public static long composeMappingId(byte light, int blockId, int biomeId) {
        if (blockId == AIR) {//Dont care about biome for air
            return Byte.toUnsignedLong(light)<<56;
        }
        return (Byte.toUnsignedLong(light)<<56)|(Integer.toUnsignedLong(biomeId) << 47)|(Integer.toUnsignedLong(blockId)<<27);
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

    public void forceResaveStates() {
        var blocks = new ArrayList<>(this.block2stateEntry.values());
        var biomes = new ArrayList<>(this.biome2biomeEntry.values());


        for (var entry : blocks) {
            if (entry.state.isAir() && entry.id == 0) {
                continue;
            }
            if (this.blockId2stateEntry.indexOf(entry) != entry.id) {
                throw new IllegalStateException("State Id NOT THE SAME, very critically bad");
            }
            byte[] serialized = entry.serialize();
            ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
            buffer.put(serialized);
            buffer.rewind();
            this.storage.putIdMapping(entry.id | (BLOCK_STATE_TYPE<<30), buffer);
            MemoryUtil.memFree(buffer);
        }

        for (var entry : biomes) {
            if (this.biomeId2biomeEntry.indexOf(entry) != entry.id) {
                throw new IllegalStateException("Biome Id NOT THE SAME, very critically bad");
            }

            byte[] serialized = entry.serialize();
            ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
            buffer.put(serialized);
            buffer.rewind();
            this.storage.putIdMapping(entry.id | (BIOME_TYPE<<30), buffer);
            MemoryUtil.memFree(buffer);
        }

        this.storage.flush();
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
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtSizeTracker.ofUnlimitedBytes());
                if (compound.getInt("id") != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, compound.getCompound("block_state")).getOrThrow();
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
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtSizeTracker.ofUnlimitedBytes());
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
