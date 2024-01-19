package me.cortex.zenith.client;

import me.cortex.zenith.common.world.storage.lmdb.LMDBInterface;
import me.cortex.zenith.common.world.storage.StorageBackend;
import me.cortex.zenith.client.importers.WorldImporter;
import me.cortex.zenith.common.world.storage.lmdb.LMDBStorageBackend;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;

import static org.lwjgl.util.lmdb.LMDB.MDB_NOLOCK;
import static org.lwjgl.util.lmdb.LMDB.MDB_NOSUBDIR;

public class Test {
    public static void main1(String[] args) {
        var dbi = new LMDBInterface.Builder()
                .setMaxDbs(1)
                .open("testdbdir.db", MDB_NOLOCK | MDB_NOSUBDIR)
                .fetch();
        dbi.setMapSize(1<<29);
        var db = dbi.createDb(null);
        db.transaction(obj->{
            var key = ByteBuffer.allocateDirect(4);
            var val = ByteBuffer.allocateDirect(1);
            for (int i = 0; i < 1<<20; i++) {
                key.putInt(0, i);
                obj.put(key, val, 0);
            }
            return 1;
        });
        db.close();
        dbi.close();
    }

    public static void main2(String[] args) throws Exception {
        var storage = new LMDBStorageBackend(new File("run/storagefile.db"));
        for (int i = 0; i < 2; i++) {
            new Thread(()->{
                //storage.getSectionData(1143914312599863680L);
                storage.setSectionData(1143914312599863680L, MemoryUtil.memAlloc(12345));
            }).start();
        }
        //storage.getSectionData(1143914312599863680L);
        //storage.setSectionData(1143914312599863612L, ByteBuffer.allocateDirect(12345));
        //storage.setSectionData(1143914312599863680L, ByteBuffer.allocateDirect(12345));
        //storage.close();

        System.out.println(storage.getIdMappingsData());
        storage.putIdMapping(1, ByteBuffer.allocateDirect(12));

        Thread.sleep(1000);
        storage.close();
    }

    public static void main(String[] args) {
        //WorldEngine engine = new WorldEngine(new File("storagefile2.db"), 5);
        WorldImporter importer = new WorldImporter(null, null);
        //importer.importWorld(new File("run/saves/Drehmal 2.2 Apotheosis Beta - 1.0.0/region/"));
    }
}
