package me.cortex.voxy.common.lod;

import me.cortex.voxy.VoxyConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

/**
 * Immutable container for a single LOD section.
 *
 * <p>A section holds a {@value VoxyConstants#SECTION_SIZE}×{@value VoxyConstants#SECTION_SIZE}
 * grid of surface-sample data.  The data arrays are indexed by {@code x + z * SECTION_SIZE}.
 *
 * <ul>
 *   <li>{@link #blockStates} — Minecraft block state IDs for the topmost solid block.
 *   <li>{@link #heights}     — Y coordinate of that block (world height, may be negative).
 *   <li>{@link #lightData}   — Packed sky (hi 4 bits) and block (lo 4 bits) light.
 *   <li>{@link #biomeIds}    — Biome registry IDs.
 * </ul>
 */
public final class LodSection {

    /** Encoded section position: see {@link SectionKey}. */
    public final long key;

    public final int[]   blockStates; // length = SECTION_SIZE * SECTION_SIZE
    public final short[] heights;     // length = SECTION_SIZE * SECTION_SIZE
    public final byte[]  lightData;   // length = SECTION_SIZE * SECTION_SIZE
    public final int[]   biomeIds;    // length = SECTION_SIZE * SECTION_SIZE

    /** Content hash — used for delta detection without comparing full data. */
    public final long hash;

    private static final int CELLS = VoxyConstants.SECTION_SIZE * VoxyConstants.SECTION_SIZE;

    public LodSection(long key, int[] blockStates, short[] heights, byte[] lightData, int[] biomeIds) {
        if (blockStates.length != CELLS || heights.length != CELLS
                || lightData.length != CELLS || biomeIds.length != CELLS) {
            throw new IllegalArgumentException("All data arrays must have length " + CELLS);
        }
        this.key         = key;
        this.blockStates = blockStates;
        this.heights     = heights;
        this.lightData   = lightData;
        this.biomeIds    = biomeIds;
        this.hash        = LodSectionHash.compute(blockStates, heights, lightData, biomeIds);
    }

    // -------------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------------

    /** Serializes the section payload (excluding the key) to a compact byte array. */
    public byte[] toBytes() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(CELLS * 11);
             DataOutputStream dos = new DataOutputStream(baos)) {
            for (int v : blockStates) dos.writeInt(v);
            for (short h : heights)   dos.writeShort(h);
            for (byte l : lightData)  dos.writeByte(l);
            for (int b : biomeIds)    dos.writeInt(b);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reconstructs a {@link LodSection} from a key and the byte array produced by {@link #toBytes()}. */
    public static LodSection fromBytes(long key, byte[] data) {
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
            int[]   bs = new int[CELLS];
            short[] ht = new short[CELLS];
            byte[]  lt = new byte[CELLS];
            int[]   bi = new int[CELLS];
            for (int i = 0; i < CELLS; i++) bs[i] = dis.readInt();
            for (int i = 0; i < CELLS; i++) ht[i] = dis.readShort();
            for (int i = 0; i < CELLS; i++) lt[i] = dis.readByte();
            for (int i = 0; i < CELLS; i++) bi[i] = dis.readInt();
            return new LodSection(key, bs, ht, lt, bi);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LodSection other)) return false;
        return key == other.key && hash == other.hash
                && Arrays.equals(blockStates, other.blockStates)
                && Arrays.equals(heights, other.heights)
                && Arrays.equals(lightData, other.lightData)
                && Arrays.equals(biomeIds, other.biomeIds);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(hash);
    }

    @Override
    public String toString() {
        return SectionKey.toString(key);
    }
}
