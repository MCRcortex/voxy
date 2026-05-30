package me.cortex.voxy;

public final class VoxyConstants {
    private VoxyConstants() {}

    public static final String MOD_ID = "voxy";

    /** Number of cells per side of a LOD section (16 x 16 grid). */
    public static final int SECTION_SIZE = 16;

    /** Maximum supported LOD level (LOD 0 = full detail, higher = coarser). */
    public static final int MAX_LOD_LEVELS = 8;

    /**
     * Protocol version for the Voxy network handshake.
     * Increment when the packet format changes to force cache invalidation.
     */
    public static final int PROTOCOL_VERSION = 1;
}
