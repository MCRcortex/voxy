package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.world.WorldEngine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TranslocatingStorageAdaptor extends DelegatingStorageAdaptor {
    public enum Mode {
        BOX_ONLY,
        PRIORITY_BOX,
        PRIORITY_ORIGINAL
    }
    public record BoxTransform(int x1, int y1, int z1, int x2, int y2, int z2, int dx, int dy, int dz, Mode mode) {
        public BoxTransform(int x1, int y1, int z1, int x2, int y2, int z2, int dx, int dy, int dz) {
            this(x1, y1, z1, x2, y2, z2, dx, dy, dz, Mode.BOX_ONLY);
        }
        public long transformIfInBox(long pos) {
            int lvl = WorldEngine.getLevel(pos);
            int x = WorldEngine.getX(pos);
            int y = WorldEngine.getY(pos);
            int z = WorldEngine.getZ(pos);

            //TODO: FIXME this might need to be the other way around, as in shift x,y,z instead of x1 etc
            if (!((this.x1>>lvl) <= x && x <= (this.x2>>lvl) &&
                  (this.y1>>lvl) <= y && y <= (this.y2>>lvl) &&
                  (this.z1>>lvl) <= z && z <= (this.z2>>lvl))) {
                return -1;
            }
            return WorldEngine.getWorldSectionId(lvl,
                    x + (this.dx>>lvl),
                    y + (this.dy>>lvl),
                    z + (this.dz>>lvl)
            );
        }
    }

    private final BoxTransform[] transforms;

    public TranslocatingStorageAdaptor(StorageBackend delegate, BoxTransform... transforms) {
        super(delegate);
        this.transforms = transforms;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        for (var transform : this.transforms) {
            long tpos = transform.transformIfInBox(key);
            if (tpos != -1) {
                if (transform.mode == Mode.BOX_ONLY || transform.mode == null) {
                    return super.getSectionData(tpos);
                } else if (transform.mode == Mode.PRIORITY_BOX) {
                    var data = super.getSectionData(tpos);
                    if (data == null) {
                        return super.getSectionData(key);
                    }
                } else if (transform.mode == Mode.PRIORITY_ORIGINAL) {
                    var data = super.getSectionData(key);
                    if (data == null) {
                        return super.getSectionData(tpos);
                    }
                } else {
                    throw new IllegalStateException();
                }
            }
        }
        return super.getSectionData(key);
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        //Dont save data if its a transformed position
        for (var transform : this.transforms) {
            long tpos = transform.transformIfInBox(key);
            if (tpos != -1) {
                return;
            }
        }
        super.setSectionData(key, data);
    }

    @Override
    public void deleteSectionData(long key) {
        //Dont delete save data if its a transformed position
        for (var transform : this.transforms) {
            long tpos = transform.transformIfInBox(key);
            if (tpos != -1) {
                return;
            }
        }
        super.deleteSectionData(key);
    }

    public static class Config extends StorageConfig {
        public StorageConfig delegate;
        public List<BoxTransform> transforms = new ArrayList<>();


        @Override
        public StorageBackend build(ConfigBuildCtx ctx) {
            return new TranslocatingStorageAdaptor(this.delegate.build(ctx), this.transforms.toArray(BoxTransform[]::new));
        }

        @Override
        public List<StorageConfig> getChildStorageConfigs() {
            return List.of(this.delegate);
        }

        public static String getConfigTypeName() {
            return "TranslocatingAdaptor";
        }
    }
}
