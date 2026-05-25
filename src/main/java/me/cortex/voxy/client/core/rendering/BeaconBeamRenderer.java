package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.config.VoxyConfig;
import me.cortex.voxy.client.core.RenderProperties;
import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.common.world.WorldSection;
import me.cortex.voxy.common.world.other.Mapper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.BeaconBeamBlock;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45C.glNamedBufferSubData;

public class BeaconBeamRenderer {
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int VERTICES_PER_BEAM = 48;
    private static final int MAX_BEAMS = 4096;
    private static final float FAR_PLANE_BLOCKS = 16.0f * 2500.0f;
    private static final float MIN_BEAM_HEIGHT = 2048.0f;
    private static final float BEAM_SCALE_THRESHOLD = 96.0f;
    private static final float FAR_BEAM_SCALE_THRESHOLD = BEAM_SCALE_THRESHOLD * 2.0f;
    private static final float SOLID_BEAM_RADIUS = 0.2f;
    private static final float BEAM_GLOW_RADIUS = 0.25f;
    private static final float BEAM_GLOW_ALPHA = 32.0f / 255.0f;

    private final WorldEngine world;
    private final RenderProperties properties;
    private final Shader shader;
    private final GlVertexArray vao;
    private final int beaconBlockId;
    private final int maxWorldY;
    private final Map<Long, Beam> beams = new ConcurrentHashMap<>();
    private final AtomicBoolean cacheDirty = new AtomicBoolean(true);
    private final Thread scannerThread;
    private volatile boolean live = true;
    private GlBuffer vertexBuffer;
    private int vertexCount;
    private int lastBaseSectionX = Integer.MIN_VALUE;
    private int lastBaseSectionY = Integer.MIN_VALUE;
    private int lastBaseSectionZ = Integer.MIN_VALUE;

    public BeaconBeamRenderer(WorldEngine world, RenderProperties properties) {
        this.world = world;
        this.properties = properties;
        this.beaconBlockId = world.getMapper().getIdForBlockState(Blocks.BEACON.defaultBlockState());
        var level = Minecraft.getInstance().level;
        this.maxWorldY = level == null ? 320 : level.getMaxY();
        this.shader = Shader.make()
                .addSource(ShaderType.VERTEX, """
                        #version 460 core
                        layout(location = 0) uniform mat4 MVP;
                        layout(location = 0) in vec3 Position;
                        layout(location = 1) in vec4 Colour;
                        layout(location = 0) out vec4 colour;
                        void main() {
                            gl_Position = MVP * vec4(Position, 1.0);
                            colour = Colour;
                        }
                        """)
                .addSource(ShaderType.FRAGMENT, """
                        #version 460 core
                        layout(location = 0) in vec4 colour;
                        layout(location = 0) out vec4 outColour;
                        void main() {
                            outColour = colour;
                        }
                        """)
                .compile()
                .name("Voxy beacon beam shader");
        this.vao = new GlVertexArray()
                .setStride(FLOATS_PER_VERTEX * Float.BYTES)
                .setF(0, GL_FLOAT, 3, 0)
                .setF(1, GL_FLOAT, 4, 3 * Float.BYTES);
        this.scannerThread = new Thread(this::scanStoredLevel0Sections, "Voxy beacon beam scanner");
        this.scannerThread.setDaemon(true);
        this.scannerThread.setPriority(Thread.MIN_PRIORITY);
        this.scannerThread.start();
    }

    public void render(Viewport<?> viewport) {
        if (!VoxyConfig.CONFIG.renderBeaconBeams) {
            return;
        }
        this.updateBeamBuffer(viewport);
        if (this.vertexCount == 0) {
            return;
        }

        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        boolean blend = glIsEnabled(GL_BLEND);
        boolean depth = glIsEnabled(GL_DEPTH_TEST);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(this.properties.closerEqualDepthCompare());
        glEnable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        this.shader.bind();
        try (var stack = MemoryStack.stackPush()) {
            var matrix = stack.mallocFloat(16);
            new Matrix4f(viewport.MVP)
                    .translate(-viewport.innerTranslation.x, -viewport.innerTranslation.y, -viewport.innerTranslation.z)
                    .get(matrix);
            glUniformMatrix4fv(0, false, matrix);
        }
        this.vao.bind();
        glDrawArrays(GL_TRIANGLES, 0, this.vertexCount);

        glDepthMask(depthMask);
        glDepthFunc(depthFunc);
        if (!depth) glDisable(GL_DEPTH_TEST);
        if (!blend) glDisable(GL_BLEND);
        if (cull) glEnable(GL_CULL_FACE);
    }

    private void updateBeamBuffer(Viewport<?> viewport) {
        boolean baseChanged = viewport.section.x != this.lastBaseSectionX
                || viewport.section.y != this.lastBaseSectionY
                || viewport.section.z != this.lastBaseSectionZ;
        if (baseChanged || this.cacheDirty.getAndSet(false)) {
            this.lastBaseSectionX = viewport.section.x;
            this.lastBaseSectionY = viewport.section.y;
            this.lastBaseSectionZ = viewport.section.z;
            this.upload(viewport);
        }
    }

    private void scanStoredLevel0Sections() {
        this.world.storage.iteratePositions(0, sectionPos -> {
            if (!this.live || this.beams.size() >= MAX_BEAMS) {
                return;
            }
            WorldSection section = this.world.acquireIfExists(sectionPos);
            if (section == null) {
                return;
            }
            try {
                this.scanSection(section);
            } finally {
                section.release();
            }
        });
    }

    public void worldEvent(WorldSection section, int updateFlags, int neighborMsk) {
        if ((updateFlags & WorldEngine.UPDATE_TYPE_BLOCK_BIT) == 0) {
            return;
        }
        int sx = section.x << 5;
        int sy = section.y << 5;
        int sz = section.z << 5;
        if (this.beams.entrySet().removeIf(entry -> entry.getValue().isInSection(sx, sy, sz))) {
            this.cacheDirty.set(true);
        }
        this.scanSection(section);
    }

    private void scanSection(WorldSection section) {
        long[] data = section._unsafeGetRawDataArray();
        for (int idx = 0; idx < data.length && this.beams.size() < MAX_BEAMS; idx++) {
            if (Mapper.getBlockId(data[idx]) == this.beaconBlockId) {
                int lx = idx & 31;
                int lz = (idx >> 5) & 31;
                int ly = (idx >> 10) & 31;
                int bx = (section.x << 5) + lx;
                int by = (section.y << 5) + ly;
                int bz = (section.z << 5) + lz;
                float[] colour = this.getBeamColour(section.x, section.y, section.z, lx, ly, lz);
                if (this.beams.putIfAbsent(packBeamKey(bx, by, bz), new Beam(bx, by, bz, colour[0], colour[1], colour[2])) == null) {
                    this.cacheDirty.set(true);
                }
            }
        }
    }

    private float[] getBeamColour(int sectionX, int sectionY, int sectionZ, int localX, int localY, int localZ) {
        int x = (sectionX << 5) + localX;
        int z = (sectionZ << 5) + localZ;
        for (int y = (sectionY << 5) + localY + 1; y < this.maxWorldY; y++) {
            WorldSection section = this.world.acquireIfExists(0, Math.floorDiv(x, 32), Math.floorDiv(y, 32), Math.floorDiv(z, 32));
            if (section == null) {
                continue;
            }
            try {
                int blockId = Mapper.getBlockId(section._unsafeGetRawDataArray()[WorldSection.getIndex(x & 31, y & 31, z & 31)]);
                if (blockId == 0) {
                    continue;
                }
                var block = this.world.getMapper().getBlockStateFromBlockId(blockId).getBlock();
                if (block instanceof BeaconBeamBlock beamBlock) {
                    int rgb = beamBlock.getColor().getTextureDiffuseColor();
                    return new float[] {
                            ((rgb >> 16) & 0xFF) / 255.0f,
                            ((rgb >> 8) & 0xFF) / 255.0f,
                            (rgb & 0xFF) / 255.0f
                    };
                }
            } finally {
                section.release();
            }
        }
        return new float[] {1.0f, 1.0f, 1.0f};
    }

    private void upload(Viewport<?> viewport) {
        this.vertexCount = this.beams.size() * VERTICES_PER_BEAM;
        long size = (long)this.vertexCount * FLOATS_PER_VERTEX * Float.BYTES;
        if (size == 0) {
            return;
        }
        if (this.vertexBuffer == null || this.vertexBuffer.size() < size) {
            if (this.vertexBuffer != null) {
                this.vertexBuffer.free();
            }
            this.vertexBuffer = new GlBuffer(size, GL_DYNAMIC_STORAGE_BIT, false).name("Voxy beacon beam buffer");
            this.vao.bindBuffer(this.vertexBuffer.id);
        }

        FloatBuffer buffer = MemoryUtil.memAllocFloat((int)(size / Float.BYTES));
        try {
            for (Beam beam : this.beams.values()) {
                this.writeBeam(buffer, viewport, beam);
            }
            buffer.flip();
            glNamedBufferSubData(this.vertexBuffer.id, 0, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private void writeBeam(FloatBuffer out, Viewport<?> viewport, Beam beam) {
        float baseX = viewport.section.x << 5;
        float baseY = viewport.section.y << 5;
        float baseZ = viewport.section.z << 5;
        float cx = beam.x - baseX + 0.5f;
        float cy = beam.y - baseY + 1.0f;
        float cz = beam.z - baseZ + 0.5f;
        float dx = (beam.x + 0.5f) - (float) viewport.cameraX;
        float dz = (beam.z + 0.5f) - (float) viewport.cameraZ;
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);
        float top = cy + Math.max(MIN_BEAM_HEIGHT, FAR_PLANE_BLOCKS - horizontalDistance);
        float radiusScale = Math.max(1.0f, horizontalDistance / FAR_BEAM_SCALE_THRESHOLD);
        this.writeBox(out, cx, cy, cz, top, SOLID_BEAM_RADIUS * radiusScale, beam.r, beam.g, beam.b, 1.0f);
        this.writeBox(out, cx, cy, cz, top, BEAM_GLOW_RADIUS * radiusScale, beam.r, beam.g, beam.b, BEAM_GLOW_ALPHA);
    }

    private void writeBox(FloatBuffer out, float cx, float cy, float cz, float top, float radius, float r, float g, float b, float a) {
        this.writeQuad(out, cx - radius, cy, cz - radius, cx + radius, cy, cz - radius, cx + radius, top, cz - radius, cx - radius, top, cz - radius, r, g, b, a);
        this.writeQuad(out, cx + radius, cy, cz + radius, cx - radius, cy, cz + radius, cx - radius, top, cz + radius, cx + radius, top, cz + radius, r, g, b, a);
        this.writeQuad(out, cx - radius, cy, cz + radius, cx - radius, cy, cz - radius, cx - radius, top, cz - radius, cx - radius, top, cz + radius, r, g, b, a);
        this.writeQuad(out, cx + radius, cy, cz - radius, cx + radius, cy, cz + radius, cx + radius, top, cz + radius, cx + radius, top, cz - radius, r, g, b, a);
    }

    private void writeQuad(FloatBuffer out, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        this.writeVertex(out, ax, ay, az, r, g, b, a);
        this.writeVertex(out, bx, by, bz, r, g, b, a);
        this.writeVertex(out, cx, cy, cz, r, g, b, a);
        this.writeVertex(out, ax, ay, az, r, g, b, a);
        this.writeVertex(out, cx, cy, cz, r, g, b, a);
        this.writeVertex(out, dx, dy, dz, r, g, b, a);
    }

    private void writeVertex(FloatBuffer out, float x, float y, float z, float r, float g, float b, float a) {
        out.put(x).put(y).put(z).put(r).put(g).put(b).put(a);
    }

    private static long packBeamKey(int x, int y, int z) {
        return (x & 0x3FFFFFFL) | ((long)(y & 0xFFF) << 26) | ((long)(z & 0x3FFFFFF) << 38);
    }

    public void free() {
        this.live = false;
        this.scannerThread.interrupt();
        this.shader.free();
        this.vao.free();
        if (this.vertexBuffer != null) {
            this.vertexBuffer.free();
        }
    }

    private record Beam(float x, float y, float z, float r, float g, float b) {
        private boolean isInSection(int sx, int sy, int sz) {
            return this.x >= sx && this.x < sx + 32
                    && this.y >= sy && this.y < sy + 32
                    && this.z >= sz && this.z < sz + 32;
        }
    }
}
