package me.cortex.voxy.common.world.other;

import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.IMappingStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;


//There are independent mappings for biome and block states, these get combined in the shader and allow for more
// variaty of things
public class Mapper {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;

    private final IMappingStorage storage;
    public static final long UNKNOWN_MAPPING = -1;
    public static final long AIR = 0;

    private final ReentrantLock blockLock = new ReentrantLock();
    private final ConcurrentHashMap<BlockState, StateEntry> block2stateEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<StateEntry> blockId2stateEntry = new ObjectArrayList<>();


    private final ReentrantLock biomeLock = new ReentrantLock();
    private final ConcurrentHashMap<String, BiomeEntry> biome2biomeEntry = new ConcurrentHashMap<>(2000,0.75f, 10);
    private final ObjectArrayList<BiomeEntry> biomeId2biomeEntry = new ObjectArrayList<>();

    private Consumer<StateEntry> newStateCallback;
    private Consumer<BiomeEntry> newBiomeCallback;
    public Mapper(IMappingStorage storage) {
        this.storage = storage;
        //Insert air since its a special entry (index 0)
        var airEntry = new StateEntry(0, Blocks.AIR.defaultBlockState());
        this.block2stateEntry.put(airEntry.state, airEntry);
        this.blockId2stateEntry.add(airEntry);

        this.loadFromStorage();
    }


    public static boolean isAir(long id) {
        //Note: air can mean void, cave or normal air, as the block state is remapped during ingesting
        return (id&(((1L<<20)-1)<<27)) == 0;
    }

    public static int isNotAirInt(long id) {
        //This is stupid and insane that even have todo this
        // works cause 0 is air, so !=0 is not air
        return Math.min(getBlockId(id), 1);
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
        return (id&(~(0xFFL<<56)))|(Integer.toUnsignedLong(light&0xFF)<<56);
    }

    public static long withBlockBiome(long id, int block, int biome) {
        return (id&(0xFFL<<56))|(Integer.toUnsignedLong(block)<<27)|(Integer.toUnsignedLong(biome)<<47);
    }

    public static long airWithLight(int light) {
        return Integer.toUnsignedLong(light&0xFF)<<56;
    }

    public void setStateCallback(Consumer<StateEntry> stateCallback) {
        this.newStateCallback = stateCallback;
    }

    public void setBiomeCallback(Consumer<BiomeEntry> biomeCallback) {
        this.newBiomeCallback = biomeCallback;
    }

    private void loadFromStorage() {
        // Each saved blockstate records the data version it was written with.
        // Unversioned {Name, Properties} compounds are upgraded from data version 2531.
        // A decode that still fails stays air in memory so section ids stay aligned,
        // and the stored bytes are left untouched.
        var mappings = this.storage.getIdMappingsData();
        List<StateEntry> sentries = new ArrayList<>();
        List<BiomeEntry> bentries = new ArrayList<>();

        boolean[] forceResave = new boolean[1];
        for (var entry : mappings.int2ObjectEntrySet()) {
            int entryType = entry.getIntKey()>>>30;
            int id = entry.getIntKey() & ((1<<30)-1);
            if (entryType == BLOCK_STATE_TYPE) {
                var sentry = StateEntry.deserialize(id, entry.getValue(), forceResave);
                sentries.add(sentry);
                if (sentry.state.isAir()) {
                    // Id 0 already owns air. Do not insert a substitute block and do not
                    // put this id into the state map, or the resave below would overwrite
                    // the original bytes.
                    continue;
                }
                var oldEntry = this.block2stateEntry.putIfAbsent(sentry.state, sentry);
                if (oldEntry != null) {
                    Logger.warn("Multiple mappings for blockstate, using old state, expect things to possibly go really badly. " + oldEntry.id + ":" + sentry.id + ":" + sentry.state );
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
                throw new IllegalStateException("Biome entry not ordered. got " + entry.biome + " with id " + entry.id + " expected id " + this.biomeId2biomeEntry.size());
            }
            this.biomeId2biomeEntry.add(entry);
        });

        if (forceResave[0]) {
            Logger.warn("Forced state resave triggered");
            this.forceResaveStates();
        }
    }

    public final int getBlockStateCount() {
        return this.blockId2stateEntry.size();
    }

    private StateEntry registerNewBlockState(BlockState state) {
        this.blockLock.lock();
        var entry = this.block2stateEntry.get(state);
        if (entry != null) {
            this.blockLock.unlock();
            return entry;
        }

        entry = new StateEntry(this.blockId2stateEntry.size(), state);
        this.blockId2stateEntry.add(entry);
        this.block2stateEntry.put(state, entry);
        this.blockLock.unlock();

        byte[] serialized = entry.serialize();
        ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
        buffer.put(serialized);
        buffer.rewind();
        this.storage.putIdMapping(entry.id | (BLOCK_STATE_TYPE<<30), buffer);
        MemoryUtil.memFree(buffer);
        //this.storage.flush();

        if (this.newStateCallback!=null)this.newStateCallback.accept(entry);
        return entry;
    }

    private BiomeEntry registerNewBiome(String biome) {
        this.biomeLock.lock();
        var entry = this.biome2biomeEntry.get(biome);
        if (entry != null) {
            this.biomeLock.unlock();
            return entry;
        }
        entry = new BiomeEntry(this.biomeId2biomeEntry.size(), biome);
        this.biomeId2biomeEntry.add(entry);
        this.biome2biomeEntry.put(biome, entry);
        this.biomeLock.unlock();

        byte[] serialized = entry.serialize();
        ByteBuffer buffer = MemoryUtil.memAlloc(serialized.length);
        buffer.put(serialized);
        buffer.rewind();
        this.storage.putIdMapping(entry.id | (BIOME_TYPE<<30), buffer);
        MemoryUtil.memFree(buffer);
        //this.storage.flush();

        if (this.newBiomeCallback!=null)this.newBiomeCallback.accept(entry);
        return entry;
    }


    //TODO:FIXME: IS VERY SLOW NEED TO MAKE IT LOCK FREE, or at minimum use a concurrent map
    public long getBaseId(byte light, BlockState state, Holder<Biome> biome) {
        if (state.isAir()) return Byte.toUnsignedLong(light) <<56;//Special case and fast return for air, dont care about the biome
        return composeMappingId(light, this.getIdForBlockState(state), this.getIdForBiome(biome));
    }

    public BlockState getBlockStateFromBlockId(int blockId) {
        return this.blockId2stateEntry.get(blockId).state;
    }

    public int getIdForBlockState(BlockState state) {
        if (state.isAir()) {
            return 0;
        }
        var mapping = this.block2stateEntry.get(state);
        if (mapping == null) {
            mapping = this.registerNewBlockState(state);
        }
        return mapping.id;
    }

    public int getBlockStateOpacity(long mappingId) {
        return this.getBlockStateOpacity(getBlockId(mappingId));
    }

    public int getBlockStateOpacity(int blockId) {
        return this.blockId2stateEntry.get(blockId).opacity;
    }

    public int getIdForBiome(Holder<Biome> biome) {
        String biomeId = biome.unwrapKey().get().identifier().toString();
        var entry = this.biome2biomeEntry.get(biomeId);
        if (entry == null) {
            entry = this.registerNewBiome(biomeId);
        }
        return entry.id;
    }

    public static long composeMappingId(byte light, int blockId, int biomeId) {
        if (blockId == AIR) {//Dont care about biome for air
            return Byte.toUnsignedLong(light)<<56;
        }
        return (Byte.toUnsignedLong(light)<<56)|(Integer.toUnsignedLong(biomeId) << 47)|(Integer.toUnsignedLong(blockId)<<27);
    }

    //TODO: fixme: synchronize access to this.blockId2stateEntry
    public StateEntry[] getStateEntries() {
        this.blockLock.lock();
        var set = new ArrayList<>(this.blockId2stateEntry);
        StateEntry[] out = new StateEntry[set.size()];
        int i = 0;
        for (var entry : set) {
            if (entry.id != i++) {
                throw new IllegalStateException();
            }
            out[i-1] = entry;
        }
        this.blockLock.unlock();
        return out;
    }

    //TODO: fixme: synchronize access to this.biomeId2biomeEntry
    public BiomeEntry[] getBiomeEntries() {
        this.biomeLock.lock();
        var set = new ArrayList<>(this.biomeId2biomeEntry);
        BiomeEntry[] out = new BiomeEntry[set.size()];
        int i = 0;
        for (var entry : set) {
            if (entry.id != i++) {
                throw new IllegalStateException();
            }
            out[i-1] = entry;
        }
        this.biomeLock.unlock();
        return out;
    }

    public void forceResaveStates() {
        // Resave by id, not by the state map, so a duplicate id that upgraded to an
        // already-registered state is still written. Air ids are skipped below.
        var blocks = new ArrayList<>(this.blockId2stateEntry);
        var biomes = new ArrayList<>(this.biome2biomeEntry.values());


        for (var entry : blocks) {
            // Id 0 is implicit air and is never stored. Any other air entry is a failed
            // decode held only in memory; persisting it would destroy the original bytes.
            if (entry.state.isAir()) {
                continue;
            }
            if (this.blockId2stateEntry.indexOf(entry) != entry.id) {
                throw new IllegalStateException("State Id NOT THE SAME, very critically bad. arr:" + this.blockId2stateEntry.indexOf(entry) + " entry: " + entry.id);
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

    public void close() {

    }


    public static final class StateEntry {
        public final int id;
        public final BlockState state;
        public final int opacity;

        // Mappings written before data_version existed store the pre-5006 {Name, Properties}
        // compound. Datafixing that from 0 reruns the 1.13 flattener (minecraft:bed becomes
        // red_bed). Datafixing from the flattened schema (1451) still reruns
        // RedstoneWireConnectionsFix, data version 2531, which turns a disconnected wire's
        // "none" into "side". 2531 is that fixer's own version, so it is not applied again,
        // while every later migration still runs, including block renames and
        // BlockStateFieldNamesFix (5006, Name/Properties -> id/properties).
        static final int UNVERSIONED_BLOCK_STATE_DATA_VERSION = 2531;
        private static final String DATA_VERSION_KEY = "data_version";

        public StateEntry(int id, BlockState state) {
            this.id = id;
            this.state = state;
            //Override opacity of leaves to be solid
            if (state.getBlock() instanceof LeavesBlock) {
                this.opacity = 15;
            } else {
                this.opacity = state.getLightDampening();
            }
        }

        public byte[] serialize() {
            try {
                var serialized = new CompoundTag();
                serialized.putInt("id", this.id);
                serialized.putInt(DATA_VERSION_KEY, currentDataVersion());
                serialized.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, this.state).result().get());
                var out = new ByteArrayOutputStream();
                NbtIo.writeCompressed(serialized, out);
                return out.toByteArray();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static StateEntry deserialize(int id, byte[] data, boolean[] forceResave) {
            try {
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
                if (compound.getIntOr("id", -1) != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }

                var bsc = compound.get("block_state");
                if (bsc == null) {
                    throw new IllegalStateException("Expected a block state but it was null: " + compound);
                }

                int currentVersion = currentDataVersion();
                boolean hasVersion = compound.contains(DATA_VERSION_KEY);
                int storedVersion = compound.getIntOr(DATA_VERSION_KEY, -1);
                boolean dataFixed = false;
                if (hasVersion) {
                    if (storedVersion < currentVersion) {
                        Logger.info("Upgrading saved blockstate from data version " + storedVersion);
                        bsc = fixBlockState(bsc, storedVersion, currentVersion);
                        dataFixed = true;
                    }
                } else if (isLegacyNameProperties(bsc)) {
                    Logger.info("Upgrading saved blockstate from data version " + UNVERSIONED_BLOCK_STATE_DATA_VERSION);
                    bsc = fixBlockState(bsc, UNVERSIONED_BLOCK_STATE_DATA_VERSION, currentVersion);
                    dataFixed = true;
                }

                var stateResult = BlockState.CODEC.parse(NbtOps.INSTANCE, bsc);
                if (stateResult.isError() || stateResult.getOrThrow().isAir()) {
                    // Air stays in memory only. Do not set forceResave: rewriting the stored
                    // bytes (previously with a random block) is what permanently corrupted worlds.
                    if (stateResult.isError()) {
                        Logger.error("Could not decode blockstate setting to air. id:" + id + " error: " + stateResult.error().get().message() + ". Stored bytes were left unchanged.");
                    } else {
                        Logger.error("Deserialization was air, keeping id " + id + " in memory only. Stored bytes were left unchanged.");
                    }
                    return new StateEntry(id, Blocks.AIR.defaultBlockState());
                }

                var state = stateResult.getOrThrow();
                if (dataFixed) {
                    Logger.info("Fixed blockstate to: " + state);
                    forceResave[0] = true;
                } else if (!hasVersion) {
                    // Already the current codec shape, but with no data version. Stamp it so the
                    // next game upgrade has a real baseline instead of guessing.
                    forceResave[0] = true;
                }
                return new StateEntry(id, state);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static boolean isLegacyNameProperties(Tag tag) {
            if (!(tag instanceof CompoundTag compound)) {
                return false;
            }
            return compound.contains("Name") && !compound.contains("id");
        }

        private static Tag fixBlockState(Tag blockState, int fromVersion, int toVersion) {
            return DataFixers.getDataFixer()
                    .update(References.BLOCK_STATE, new Dynamic<>(NbtOps.INSTANCE, blockState), fromVersion, toVersion)
                    .getValue();
        }

        private static int currentDataVersion() {
            return SharedConstants.getCurrentVersion().dataVersion().version();
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
                var serialized = new CompoundTag();
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
                var compound = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
                if (compound.getIntOr("id", -1) != id) {
                    throw new IllegalStateException("Encoded id != expected id");
                }
                String biome = compound.getStringOr("biome_id", null);
                return new BiomeEntry(id, biome);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
