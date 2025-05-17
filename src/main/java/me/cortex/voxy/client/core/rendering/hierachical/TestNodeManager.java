package me.cortex.voxy.client.core.rendering.hierachical;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.*;
import me.cortex.voxy.client.core.rendering.ISectionWatcher;
import me.cortex.voxy.client.core.rendering.building.BuiltSection;
import me.cortex.voxy.client.core.rendering.section.geometry.AbstractSectionGeometryManager;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.HierarchicalBitSet;
import me.cortex.voxy.common.util.MemoryBuffer;
import me.cortex.voxy.common.world.WorldEngine;
import org.lwjgl.system.MemoryUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static me.cortex.voxy.common.world.WorldEngine.*;

public class TestNodeManager {
    private static final class MemoryGeometryManager extends AbstractSectionGeometryManager {
        private record Entry(long pos, long size) {}
        private long memoryInUse = 0;
        private final HierarchicalBitSet allocation;
        private final Int2ObjectOpenHashMap<Entry> sections = new Int2ObjectOpenHashMap<>();
        public MemoryGeometryManager(int maxSections, long geometryCapacity) {
            super(maxSections, geometryCapacity);
            this.allocation = new HierarchicalBitSet(maxSections);
        }

        @Override
        public int uploadReplaceSection(int oldId, BuiltSection section) {
            if (section.isEmpty()) {
                throw new IllegalArgumentException();
            }
            if (oldId != -1) {
                this.removeSection(oldId);
            }
            int newId = this.allocation.allocateNext();
            var entry = new Entry(section.position, section.geometryBuffer.size);
            if (this.sections.put(newId, entry) != null) {
                throw new IllegalStateException();
            }
            this.memoryInUse += entry.size;
            section.free();

            Logger.info("Creating geometry with id", newId, "and size", entry.size, "at pos", WorldEngine.pprintPos(entry.pos));

            return newId;
        }

        @Override
        public void removeSection(int id) {
            if (!this.allocation.free(id)) {
                throw new IllegalStateException();
            }
            var old = this.sections.remove(id);
            if (old == null) {
                throw new IllegalStateException();
            }
            this.memoryInUse -= old.size;
            Logger.info("Removing geometry with id", id, "it was at pos", WorldEngine.pprintPos(old.pos));
        }

        @Override
        public void downloadAndRemove(int id, Consumer<BuiltSection> callback) {
            this.removeSection(id);
        }

        @Override
        public long getUsedCapacity() {
            return this.memoryInUse;
        }
    }

    private static class CleanerImp implements NodeManager.ICleaner {
        private final IntOpenHashSet active = new IntOpenHashSet();

        @Override
        public void alloc(int id) {
            if (!this.active.add(id)) {
                throw new IllegalStateException();
            }
        }

        @Override
        public void move(int from, int to) {

        }

        @Override
        public void free(int id) {
            if (!this.active.remove(id)) {
                throw new IllegalStateException();
            }
        }
    }
    private static class Watcher implements ISectionWatcher {
        private final Long2ByteOpenHashMap updateTypes = new Long2ByteOpenHashMap();

        @Override
        public boolean watch(long position, int types) {
            byte current = 0;
            boolean had = false;
            if (this.updateTypes.containsKey(position)) {
                current = this.updateTypes.get(position);
                had = true;
            }
            if (had && current == 0) {
                throw new IllegalStateException();
            }
            this.updateTypes.put(position, (byte) (current | types));
            byte delta = (byte) (types&(~current));
            Logger.info("Watching pos", WorldEngine.pprintPos(position), "with types", getPrettyTypes(types), "was", getPrettyTypes(current));
            return delta!=0;//returns true if new types where set
        }

        @Override
        public boolean unwatch(long position, int types) {
            if (!this.updateTypes.containsKey(position)) {
                throw new IllegalStateException("Pos not in map: " + WorldEngine.pprintPos(position));
            }
            byte current = this.updateTypes.get(position);
            byte newTypes = (byte) (current&(~types));
            if (newTypes == 0) {
                this.updateTypes.remove(position);
            } else {
                this.updateTypes.put(position, newTypes);
            }
            Logger.info("UnWatching pos", WorldEngine.pprintPos(position), "removing types", getPrettyTypes(types), "was watching", getPrettyTypes(current), "new types", getPrettyTypes(newTypes));
            return newTypes == 0;//Returns true on removal
        }

        @Override
        public int get(long position) {
            return this.updateTypes.getOrDefault(position, (byte) 0);
        }

        private static String[] getPrettyTypes(int msk) {
            if ((msk&~UPDATE_FLAGS)!=0) {
                throw new IllegalStateException();
            }
            String[] types = new String[Integer.bitCount(msk)];
            int i = 0;
            if ((msk&UPDATE_TYPE_BLOCK_BIT)!=0) {
                types[i++] = "BLOCK";
            }
            if ((msk&UPDATE_TYPE_CHILD_EXISTENCE_BIT)!=0) {
                types[i++] = "CHILD";
            }
            return types;
        }
    }

    private static class TestBase {
        public final MemoryGeometryManager geometryManager;
        public final NodeManager nodeManager;
        public final Watcher watcher;
        public final CleanerImp cleaner;

        public TestBase() {
            this.watcher = new Watcher();
            this.cleaner = new CleanerImp();
            this.geometryManager = new MemoryGeometryManager(1<<20, 1<<30);
            this.nodeManager = new NodeManager(1 << 21, this.geometryManager, this.watcher);
            this.nodeManager.setClear(this.cleaner);
        }

        public void putTopPos(long pos) {
            this.nodeManager.insertTopLevelNode(pos);
        }

        public void meshUpdate(long pos, int childExistence, int geometrySize) {
            if (childExistence == -1) {
                childExistence = 0xFF;
            }
            if (childExistence>255) {
                throw new IllegalArgumentException();
            }
            MemoryBuffer buff = null;
            if (geometrySize != 0) {
                buff = new MemoryBuffer(geometrySize);
            }
            var builtGeometry = new BuiltSection(pos, (byte) childExistence, -2, buff, null);
            this.nodeManager.processGeometryResult(builtGeometry);
        }

        public void request(long pos) {
            this.nodeManager.processRequest(pos);
        }

        public void childUpdate(long pos, int existence) {
            if (existence == -1) {
                existence = 0xFF;
            }
            if (existence>255) {
                throw new IllegalArgumentException();
            }
            this.nodeManager.processChildChange(pos, (byte) existence);
        }

        public boolean printNodeChanges() {
            var changes = this.nodeManager._generateChangeList();
            if (changes == null) {
                return false;
            }
            for (int c = 0; c < changes.size/20; c++) {
                long ptr = changes.address+20L*c;
                int nodeId = MemoryUtil.memGetInt(ptr); ptr+=4;
                long pos = Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr))<<32; ptr += 4;
                pos     |= Integer.toUnsignedLong(MemoryUtil.memGetInt(ptr)); ptr += 4;
                int z = MemoryUtil.memGetInt(ptr); ptr += 4;
                int w = MemoryUtil.memGetInt(ptr); ptr += 4;

                int childPtr = w&0xFFFFFF;
                int geometry = z&0xFFFFFF;
                short flags = 0;

                flags |= (short) ((z>>>24)&0xFF);
                flags |= (short) (((w>>>24)&0xFF)<<8);

                Logger.info("Node update, id:",nodeId,"pos:",WorldEngine.pprintPos(pos),"childPtr:",childPtr,"geometry:",geometry,"flags:",flags);
            }
            changes.free();
            return true;
        }

        public void removeNodeGeometry(long pos) {
            this.nodeManager.removeNodeGeometry(pos);
        }

        public void verifyIntegrity() {
            this.nodeManager.verifyIntegrity(this.watcher.updateTypes.keySet(), this.cleaner.active);
        }

        public void remTopPos(long pos) {
            this.nodeManager.removeTopLevelNode(pos);
        }
    }

    private static void fillInALl(TestBase test, long pos, Long2IntFunction converter) {
        test.request(pos);


        int ce = converter.get(pos);
        //Satisfy request for all the children
        for (int i = 0; i<8;i++) {
            if ((ce&(1<<i))==0) continue;
            long p = makeChildPos(pos, i);
            test.meshUpdate(p, converter.get(p), 8);
        }

        if (WorldEngine.getLevel(pos) == 1) {
            return;
        }


        for (int i = 0; i<8;i++) {
            if ((ce&(1<<i))==0) continue;
            fillInALl(test, makeChildPos(pos, i), converter);
        }
    }


    private static class Node {
        private final long pos;
        private final Node[] children = new Node[8];
        private byte childExistenceMask;
        private int meshId;
        private Node(long pos) {
            this.pos = pos;
        }
    }

    public static void main(String[] args) {
        Logger.INSERT_CLASS = false;
        int ITER_COUNT = 5_000;
        int INNER_ITER_COUNT = 100_000;
        boolean GEO_REM = true;

        AtomicInteger finished = new AtomicInteger();
        HashSet<List<StackTraceElement>> seenTraces = new HashSet<>();

        Logger.SHUTUP = true;

        if (false) {
            for (int q = 0; q < ITER_COUNT; q++) {
                //Logger.info("Iteration "+ q);
                if (runTest(INNER_ITER_COUNT, q, seenTraces, GEO_REM)) {
                    finished.incrementAndGet();
                }
            }
        } else {
            IntStream.range(0, ITER_COUNT).parallel().forEach(i->{
                if (runTest(INNER_ITER_COUNT, i, seenTraces, GEO_REM)) {
                    finished.incrementAndGet();
                }
            });
        }
        System.out.println("Finished " + finished.get() + " iterations out of " + ITER_COUNT);
    }
    private static long rPos(Random r, LongList tops) {
        int lvl = r.nextInt(5);
        long top = tops.getLong(r.nextInt(tops.size()));
        if (lvl==4) {
            return top;
        }
        int bound = 16>>lvl;
        return WorldEngine.getWorldSectionId(lvl, r.nextInt(bound)+(WorldEngine.getX(top)<<4), r.nextInt(bound)+(WorldEngine.getY(top)<<4), r.nextInt(bound)+(WorldEngine.getZ(top)<<4));
    }

    private static boolean runTest(int ITERS, int testIdx, Set<List<StackTraceElement>> traces, boolean geoRemoval) {
        Random r = new Random(testIdx * 1234L);
        try {
            var test = new TestBase();
            LongList tops = new LongArrayList();

            int R = 1;
            if (r.nextBoolean()) {
                R++;
                if (r.nextBoolean()) {
                    R++;
                    if (r.nextBoolean()) {
                        R++;
                    }
                }
            }

            //Fuzzy bruteforce everything
            for (int x = -R; x<=R; x++) {
                for (int z = -R; z<=R; z++) {
                    tops.add(WorldEngine.getWorldSectionId(4, x, 0, z));
                    tops.add(WorldEngine.getWorldSectionId(4, x, 1, z));
                }
            }

            for (long p : tops) {
                test.putTopPos(p);
                test.meshUpdate(p, -1, 18);
                fillInALl(test, p, a->-1);
                test.printNodeChanges();
                test.verifyIntegrity();
            }
            for (int i = 0; i < ITERS; i++) {
                long pos = rPos(r, tops);
                int op = r.nextInt(5);
                int extra = r.nextInt(256);
                boolean hasGeometry = r.nextBoolean();
                boolean addRemTLN = r.nextInt(64) == 0;
                boolean extraBool = r.nextBoolean();
                if (op == 0 && addRemTLN) {
                    pos = WorldEngine.getWorldSectionId(4, r.nextInt(5)-2, r.nextInt(5)-2, r.nextInt(5)-2);
                    boolean cont = tops.contains(pos);
                    if (cont&&extraBool&&tops.size()>1) {
                        extraBool = true;
                        test.remTopPos(pos);
                        tops.rem(pos);
                    } else if (!cont) {
                        extraBool = false;
                        test.putTopPos(pos);
                        tops.add(pos);
                    }
                } else if (op == 0) {
                    test.request(pos);
                }
                if (op == 1) {
                    test.childUpdate(pos, extra);
                }
                if (op == 2) {
                    test.meshUpdate(pos, extra, hasGeometry ? 100 : 0);
                }
                if (op == 3 && geoRemoval) {
                    test.nodeManager.removeNodeGeometry(pos);
                }
                test.printNodeChanges();
                test.verifyIntegrity();
            }
            for (long top : tops) {
                test.remTopPos(top);
            }
            test.printNodeChanges();
            test.verifyIntegrity();
            if (test.nodeManager.getCurrentMaxNodeId() != -1) {
                throw new IllegalStateException();
            }
            if (!test.cleaner.active.isEmpty()) {
                throw new IllegalStateException();
            }
            if (!test.watcher.updateTypes.isEmpty()) {
                throw new IllegalStateException();
            }
            if (test.geometryManager.memoryInUse != 0) {
                throw new IllegalStateException();
            }
            return true;
        } catch (Exception e) {
            var trace = new ArrayList<>(List.of(e.getStackTrace()));
            while (!trace.getLast().getMethodName().equals("runTest")) trace.removeLast();//Very hacky budget filter
            synchronized (traces) {
                if (traces.add(trace)) {
                    e.printStackTrace();
                }
            }
            return false;
        }
    }







    public static void main3(String[] args) {
        Logger.INSERT_CLASS = false;

        if (false) {
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 3, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Created full node");
            test.childUpdate(POS_A, 0);
            test.printNodeChanges();
            Logger.info("BB");
            test.meshUpdate(POS_A, 0, 0);
            test.printNodeChanges();
        }

        if (false) {
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 1, 0);
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.childUpdate(POS_A, 0);
        }


        if (false) {//Crash E
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 3, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Created full node");
            test.childUpdate(POS_A, 0b110);
            test.printNodeChanges();
            test.childUpdate(POS_A, 0b10);
            test.printNodeChanges();
        }

        if (false) {//Crash D
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 3, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Created full node");
            test.childUpdate(POS_A, 0b110);
            test.printNodeChanges();
            test.meshUpdate(makeChildPos(POS_A, 2), -1, 100);
            test.printNodeChanges();
        }

        if (false) {//Crash c
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 3, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Created full node");
            test.childUpdate(POS_A, 0b1111);
            test.printNodeChanges();
            test.meshUpdate(makeChildPos(POS_A, 2), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Executing funny");
            test.childUpdate(POS_A, 0b0110);
            test.printNodeChanges();
        }

        if (false) {//Crash b
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 3, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
            Logger.info("TEST: Created full node");
            test.childUpdate(POS_A, 0b1111);
            test.printNodeChanges();
            test.meshUpdate(makeChildPos(POS_A, 2), -1, 100);
            test.printNodeChanges();
            test.childUpdate(POS_A, 0b1110);
            test.printNodeChanges();
            test.childUpdate(POS_A, 0b0110);
            test.printNodeChanges();
        }

        if (false) {// Test case A, known crash
            var test = new TestBase();
            long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
            test.putTopPos(POS_A);
            test.meshUpdate(POS_A, 7, 0);
            test.printNodeChanges();
            test.request(POS_A);
            test.meshUpdate(makeChildPos(POS_A, 0), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.meshUpdate(makeChildPos(POS_A, 2), -1, 100);
            test.printNodeChanges();
            Logger.error("CHANGING CHILD A");
            test.childUpdate(POS_A, 1);
            test.printNodeChanges();
            Logger.error("CHANGING CHILD B");
            test.childUpdate(POS_A, 3);
            test.printNodeChanges();
            test.meshUpdate(makeChildPos(POS_A, 1), -1, 100);
            test.printNodeChanges();
        }

    }


    public static void main2(String[] args) {
        Logger.INSERT_CLASS = false;

        var test = new TestBase();
        long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
        test.putTopPos(POS_A);

        test.meshUpdate(POS_A, -1, 0);
        fillInALl(test, POS_A, a->-1);

        test.printNodeChanges();
        Logger.info("\n\n");

        test.removeNodeGeometry(WorldEngine.getWorldSectionId(0,0,0,0));
        test.printNodeChanges();
        test.removeNodeGeometry(WorldEngine.getWorldSectionId(3,0,0,0));
        test.printNodeChanges();
        Logger.info("changing child existance");
        test.childUpdate(WorldEngine.getWorldSectionId(4,0,0,0), 1);
        test.childUpdate(WorldEngine.getWorldSectionId(3,0,0,0), 1);
        test.childUpdate(WorldEngine.getWorldSectionId(2,0,0,0), 1);
        test.childUpdate(WorldEngine.getWorldSectionId(1,0,0,0), 1);
        test.printNodeChanges();
    }

    public static void main1(String[] args) {
        Logger.INSERT_CLASS = false;

        Random r = new Random(1234);
        Long2IntOpenHashMap aa = new Long2IntOpenHashMap();
        Long2IntFunction cc = p-> aa.computeIfAbsent(p, poss->{int b = r.nextInt()&0xFF;
            while (b==0) b = r.nextInt()&0xFF;
            return b;});

        var test = new TestBase();
        long POS_A = WorldEngine.getWorldSectionId(4, 0, 0, 0);
        test.putTopPos(POS_A);

        test.meshUpdate(POS_A, cc.get(POS_A), 0);
        fillInALl(test, POS_A, cc);

        test.printNodeChanges();
        Logger.info("\n\n");

        var positions = new ArrayList<>(aa.keySet().stream().filter(k->{
            return WorldEngine.getLevel(k)!=0;
        }).toList());
        positions.sort(Long::compareTo);
        Collections.shuffle(positions, r);

        Logger.info("Removing", WorldEngine.pprintPos(positions.get(0)));
        test.removeNodeGeometry(positions.get(0));
        test.printNodeChanges();
    }


    private static int getChildIdx(long pos) {
        int x = WorldEngine.getX(pos);
        int y = WorldEngine.getY(pos);
        int z = WorldEngine.getZ(pos);
        return (x&1)|((y&1)<<2)|((z&1)<<1);
    }

    private static long makeChildPos(long basePos, int addin) {
        int lvl = WorldEngine.getLevel(basePos);
        if (lvl == 0) {
            throw new IllegalArgumentException("Cannot create a child lower than lod level 0");
        }
        return WorldEngine.getWorldSectionId(lvl-1,
                (WorldEngine.getX(basePos)<<1)|(addin&1),
                (WorldEngine.getY(basePos)<<1)|((addin>>2)&1),
                (WorldEngine.getZ(basePos)<<1)|((addin>>1)&1));
    }

    private long makeParentPos(long pos) {
        int lvl = WorldEngine.getLevel(pos);
        if (lvl == MAX_LOD_LAYER) {
            throw new IllegalArgumentException("Cannot create a parent higher than LoD " + (MAX_LOD_LAYER));
        }
        return WorldEngine.getWorldSectionId(lvl+1,
                WorldEngine.getX(pos)>>1,
                WorldEngine.getY(pos)>>1,
                WorldEngine.getZ(pos)>>1);
    }

}
