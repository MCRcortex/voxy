package me.cortex.voxy.common.storage.other;

import me.cortex.voxy.common.storage.StorageBackend;
import me.cortex.voxy.common.storage.config.ConfigBuildCtx;
import me.cortex.voxy.common.storage.config.StorageConfig;
import me.cortex.voxy.common.world.WorldEngine;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TranslocatingStorageAdaptor extends DelegatingStorageAdaptor {
    public record BoxTransform(int x1, int y1, int z1, int x2, int y2, int z2, int dx, int dy, int dz) {
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

    private long transformPosition(long pos) {
        for (var transform : this.transforms) {
            long tpos = transform.transformIfInBox(pos);
            if (tpos != -1) {
                return tpos;
            }
        }
        return pos;
    }

    @Override
    public ByteBuffer getSectionData(long key) {
        return super.getSectionData(this.transformPosition(key));
    }

    @Override
    public void setSectionData(long key, ByteBuffer data) {
        super.setSectionData(this.transformPosition(key), data);
    }

    @Override
    public void deleteSectionData(long key) {
        super.deleteSectionData(this.transformPosition(key));
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
