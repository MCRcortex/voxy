package me.cortex.voxy.common.world.other;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.config.IMappingStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minecraft 26.3 renamed block-state NBT from {Name, Properties} to {id, properties}
 * (BlockStateFieldNamesFix, data version 5006). Saved Voxy mappings have no data version
 * and still use the old shape, so {@link BlockState#CODEC} fails.
 *
 * Datafixing those compounds from 0 reruns the 1.13 flattener. Datafixing from 1451
 * still reruns {@code RedstoneWireConnectionsFix} (data version 2531). Unversioned
 * mappings are therefore upgraded from 2531.
 */
public class MapperBlockStateMigrationTest {
    private static final int BLOCK_STATE_TYPE = 1;
    private static final int BIOME_TYPE = 2;

    @BeforeAll
    static void bootstrap() {
        Logger.SHUTUP = true;
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unversionedBaselineSkipsRedstoneFixerAndFlattening() throws CommandSyntaxException {
        assertEquals(2531, Mapper.StateEntry.UNVERSIONED_BLOCK_STATE_DATA_VERSION);
        assertTrue(currentDataVersion() >= 5006, "BlockStateFieldNamesFix is data version 5006");

        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stairs = oakStairs();
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        BlockState connected = connectedWire();
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState shortGrass = Blocks.SHORT_GRASS.defaultBlockState();
        BlockState grassBlock = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState chain = Blocks.IRON_CHAIN.defaultBlockState();
        BlockState cauldron = Blocks.CAULDRON.defaultBlockState();
        assertEquals(RedstoneSide.NONE, wire.getValue(RedstoneWireBlock.EAST));

        Tag bed = TagParser.parseCompoundFully("{Name:\"minecraft:bed\",Properties:{facing:\"north\",occupied:\"false\",part:\"foot\"}}");
        Tag oldChain = TagParser.parseCompoundFully("{Name:\"minecraft:chain\",Properties:{axis:\"y\",waterlogged:\"false\"}}");
        Tag oldGrass = TagParser.parseCompoundFully("{Name:\"minecraft:grass\"}");

        int baseline = Mapper.StateEntry.UNVERSIONED_BLOCK_STATE_DATA_VERSION;
        assertEquals(stone, roundTrip(stone, baseline));
        assertEquals(stairs, roundTrip(stairs, baseline));
        assertEquals(wire, roundTrip(wire, baseline));
        assertEquals(connected, roundTrip(connected, baseline));
        assertEquals(leaves, roundTrip(leaves, baseline));
        assertEquals(shortGrass, roundTrip(shortGrass, baseline));
        assertEquals(grassBlock, roundTrip(grassBlock, baseline));
        assertEquals(chain, roundTrip(chain, baseline));
        assertEquals(cauldron, roundTrip(cauldron, baseline));

        CompoundTag fixedStone = (CompoundTag) fix(legacyOf(stone), baseline);
        assertTrue(fixedStone.contains("id"), fixedStone.toString());
        assertFalse(fixedStone.contains("Name"), fixedStone.toString());

        // From 0 the 1.13 flattener rewrites bed, and the redstone connection fixer rewrites a
        // disconnected wire. 1451 skips the flattener but the wire fixer is data version 2531,
        // so it still runs. Starting at 2531 skips that fixer and keeps later renames.
        assertEquals("side", property(fix(legacyOf(wire), 0), "east"));
        assertEquals("side", property(fix(legacyOf(wire), 1451), "east"));
        assertEquals("none", property(fix(legacyOf(wire), baseline), "east"));
        assertEquals("minecraft:red_bed", blockId(fix(bed, 0)));
        assertEquals("minecraft:bed", blockId(fix(bed, 1451)));
        assertEquals("minecraft:bed", blockId(fix(bed, baseline)));

        assertEquals("minecraft:iron_chain", blockId(fix(oldChain, baseline)));
        assertEquals("minecraft:short_grass", blockId(fix(oldGrass, baseline)));
        assertEquals(Blocks.SHORT_GRASS.defaultBlockState(), parse(fix(oldGrass, baseline)));
        assertEquals(Blocks.IRON_CHAIN.defaultBlockState(), parse(fix(oldChain, baseline)));

        // The pre-rename codec shape does not parse on 26.3.
        var raw = BlockState.CODEC.parse(NbtOps.INSTANCE, legacyOf(stone));
        assertTrue(raw.isError(), raw.toString());
    }

    @Test
    void legacyMappingsUpgradeAndFailedDecodesAreNotRewritten() throws IOException, CommandSyntaxException {
        MemoryMappings storage = new MemoryMappings();
        byte[] stoneBytes = writeRoot(legacyRoot(1, Blocks.STONE.defaultBlockState()));
        byte[] stairsBytes = writeRoot(legacyRoot(2, oakStairs()));
        byte[] wireBytes = writeRoot(legacyRoot(3, Blocks.REDSTONE_WIRE.defaultBlockState()));
        byte[] brokenBytes = writeRoot(rootWithState(4, TagParser.parseCompoundFully("{Name:\"minecraft:bed\",Properties:{facing:\"north\",occupied:\"false\",part:\"foot\"}}")));
        byte[] biomeBytes = new Mapper.BiomeEntry(0, "minecraft:plains").serialize();
        storage.put(blockKey(1), stoneBytes);
        storage.put(blockKey(2), stairsBytes);
        storage.put(blockKey(3), wireBytes);
        storage.put(blockKey(4), brokenBytes);
        storage.put(biomeKey(0), biomeBytes);

        Mapper mapper = new Mapper(storage);

        assertEquals(Blocks.STONE.defaultBlockState(), mapper.getBlockStateFromBlockId(1));
        assertEquals(oakStairs(), mapper.getBlockStateFromBlockId(2));
        assertEquals(Blocks.REDSTONE_WIRE.defaultBlockState(), mapper.getBlockStateFromBlockId(3));
        assertTrue(mapper.getBlockStateFromBlockId(4).isAir(), "failed decode stays air, not a random block");
        assertEquals(0, mapper.getIdForBlockState(Blocks.AIR.defaultBlockState()));
        assertEquals("minecraft:plains", mapper.getBiomeEntries()[0].biome);
        assertFalse(storage.data.containsKey(blockKey(0)), "implicit air id 0 is not stored");

        assertArrayEquals(brokenBytes, storage.get(blockKey(4)), "failed decode must not rewrite stored bytes");
        assertUpgraded(storage.get(blockKey(1)), 1, Blocks.STONE.defaultBlockState());
        assertUpgraded(storage.get(blockKey(3)), 3, Blocks.REDSTONE_WIRE.defaultBlockState());

        // A second open resaves nothing and still decodes. The broken id is still the original bytes.
        byte[] upgradedStone = storage.get(blockKey(1)).clone();
        byte[] upgradedWire = storage.get(blockKey(3)).clone();
        byte[] upgradedBiome = storage.get(biomeKey(0)).clone();
        Mapper again = new Mapper(storage);
        assertEquals(Blocks.REDSTONE_WIRE.defaultBlockState(), again.getBlockStateFromBlockId(3));
        assertTrue(again.getBlockStateFromBlockId(4).isAir());
        assertArrayEquals(upgradedStone, storage.get(blockKey(1)));
        assertArrayEquals(upgradedWire, storage.get(blockKey(3)));
        assertArrayEquals(brokenBytes, storage.get(blockKey(4)));
        assertArrayEquals(upgradedBiome, storage.get(biomeKey(0)));
    }

    @Test
    void storedDataVersionIsUsedAndCurrentMappingsStayPut() throws IOException {
        boolean[] resave = new boolean[1];
        CompoundTag versioned = legacyRoot(1, Blocks.REDSTONE_WIRE.defaultBlockState());
        versioned.putInt("data_version", 4790);//26.1.2
        Mapper.StateEntry upgraded = Mapper.StateEntry.deserialize(1, writeRoot(versioned), resave);
        assertEquals(Blocks.REDSTONE_WIRE.defaultBlockState(), upgraded.state);
        assertTrue(resave[0]);

        resave[0] = false;
        byte[] current = new Mapper.StateEntry(7, oakStairs()).serialize();
        CompoundTag written = read(current);
        assertEquals(currentDataVersion(), written.getIntOr("data_version", -1));
        assertFalse(written.getCompoundOrEmpty("block_state").contains("Name"));
        Mapper.StateEntry roundTrip = Mapper.StateEntry.deserialize(7, current, resave);
        assertEquals(oakStairs(), roundTrip.state);
        assertFalse(resave[0], "a mapping already at the current data version must not be rewritten");

        MemoryMappings storage = new MemoryMappings();
        CompoundTag unversionedCurrent = new CompoundTag();
        unversionedCurrent.putInt("id", 1);
        unversionedCurrent.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, Blocks.STONE.defaultBlockState()).result().get());
        storage.put(blockKey(1), writeRoot(unversionedCurrent));
        Mapper mapper = new Mapper(storage);
        assertEquals(Blocks.STONE.defaultBlockState(), mapper.getBlockStateFromBlockId(1));
        CompoundTag stamped = read(storage.get(blockKey(1)));
        assertEquals(currentDataVersion(), stamped.getIntOr("data_version", -1));
        assertEquals(Blocks.STONE.defaultBlockState(), parseState(stamped));
    }

    @Test
    void duplicateLegacyIdsBothUpgrade() throws IOException {
        MemoryMappings storage = new MemoryMappings();
        storage.put(blockKey(1), writeRoot(legacyRoot(1, Blocks.STONE.defaultBlockState())));
        storage.put(blockKey(2), writeRoot(legacyRoot(2, Blocks.STONE.defaultBlockState())));
        Mapper mapper = new Mapper(storage);
        assertEquals(Blocks.STONE.defaultBlockState(), mapper.getBlockStateFromBlockId(1));
        assertEquals(Blocks.STONE.defaultBlockState(), mapper.getBlockStateFromBlockId(2));
        int winner = mapper.getIdForBlockState(Blocks.STONE.defaultBlockState());
        assertTrue(winner == 1 || winner == 2, "winner was " + winner);
        assertUpgraded(storage.get(blockKey(1)), 1, Blocks.STONE.defaultBlockState());
        assertUpgraded(storage.get(blockKey(2)), 2, Blocks.STONE.defaultBlockState());
    }

    private static void assertUpgraded(byte[] data, int id, BlockState expected) throws IOException {
        CompoundTag compound = read(data);
        assertEquals(id, compound.getIntOr("id", -1));
        assertEquals(currentDataVersion(), compound.getIntOr("data_version", -1));
        Tag stateTag = compound.get("block_state");
        // Default states encode as a bare id string. States with properties encode as {id, properties}.
        if (stateTag instanceof CompoundTag state) {
            assertFalse(state.contains("Name"), state.toString());
            assertTrue(state.contains("id"), state.toString());
        }
        assertEquals(expected, parse(stateTag));
    }

    private static BlockState parseState(CompoundTag root) {
        return parse(root.get("block_state"));
    }

    private static BlockState oakStairs() {
        return Blocks.OAK_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.TOP)
                .setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT)
                .setValue(StairBlock.WATERLOGGED, false);
    }

    private static BlockState connectedWire() {
        return Blocks.REDSTONE_WIRE.defaultBlockState()
                .setValue(RedstoneWireBlock.NORTH, RedstoneSide.SIDE)
                .setValue(RedstoneWireBlock.SOUTH, RedstoneSide.UP)
                .setValue(RedstoneWireBlock.POWER, 7);
    }

    private static CompoundTag legacyOf(BlockState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        CompoundTag properties = new CompoundTag();
        for (var value : state.getValues().toList()) {
            properties.putString(value.property().getName(), value.valueName());
        }
        if (!properties.isEmpty()) {
            tag.put("Properties", properties);
        }
        return tag;
    }

    private static CompoundTag legacyRoot(int id, BlockState state) {
        return rootWithState(id, legacyOf(state));
    }

    private static CompoundTag rootWithState(int id, Tag blockState) {
        CompoundTag root = new CompoundTag();
        root.putInt("id", id);
        root.put("block_state", blockState);
        return root;
    }

    private static Tag fix(Tag blockState, int fromVersion) {
        return DataFixers.getDataFixer()
                .update(References.BLOCK_STATE, new com.mojang.serialization.Dynamic<>(NbtOps.INSTANCE, blockState), fromVersion, currentDataVersion())
                .getValue();
    }

    private static BlockState roundTrip(BlockState state, int fromVersion) {
        return parse(fix(legacyOf(state), fromVersion));
    }

    private static BlockState parse(Tag tag) {
        var parsed = BlockState.CODEC.parse(NbtOps.INSTANCE, tag);
        if (parsed.isError()) {
            throw new AssertionError(parsed.error().get().message() + " tag=" + tag);
        }
        return parsed.getOrThrow();
    }

    private static String blockId(Tag tag) {
        CompoundTag compound = (CompoundTag) tag;
        String id = compound.getStringOr("id", null);
        return id != null ? id : compound.getStringOr("Name", null);
    }

    private static String property(Tag tag, String name) {
        CompoundTag compound = (CompoundTag) tag;
        CompoundTag properties = compound.getCompoundOrEmpty("properties");
        if (properties.isEmpty()) {
            properties = compound.getCompoundOrEmpty("Properties");
        }
        return properties.getStringOr(name, "<missing>");
    }

    private static int currentDataVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    private static int blockKey(int id) {
        return id | (BLOCK_STATE_TYPE << 30);
    }

    private static int biomeKey(int id) {
        return id | (BIOME_TYPE << 30);
    }

    private static byte[] writeRoot(CompoundTag root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, out);
        return out.toByteArray();
    }

    private static CompoundTag read(byte[] data) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
    }

    private static final class MemoryMappings implements IMappingStorage {
        private final Int2ObjectOpenHashMap<byte[]> data = new Int2ObjectOpenHashMap<>();

        void put(int key, byte[] bytes) {
            this.data.put(key, bytes.clone());
        }

        byte[] get(int key) {
            byte[] bytes = this.data.get(key);
            return bytes == null ? null : bytes.clone();
        }

        @Override
        public void putIdMapping(int id, ByteBuffer data) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            this.data.put(id, bytes);
        }

        @Override
        public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() {
            Int2ObjectOpenHashMap<byte[]> copy = new Int2ObjectOpenHashMap<>();
            for (var entry : this.data.int2ObjectEntrySet()) {
                copy.put(entry.getIntKey(), entry.getValue().clone());
            }
            return copy;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
