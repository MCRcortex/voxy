package me.cortex.voxy.common.util.cpu;

import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinNT;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.Logger;
import me.cortex.voxy.common.util.ThreadUtils;
import org.lwjgl.system.Platform;
import oshi.SystemInfo;

import java.util.Arrays;
import java.util.Random;

//Represents the layout of the current cpu running on
public class CpuLayout {
    private CpuLayout(){}

    public static void setThreadAffinity(Core... cores) {
        var affinity = new Affinity[cores.length];
        for (int i = 0; i < cores.length; i++) {
            affinity[i] = cores[i].affinity;
        }
        setThreadAffinity(affinity);
    }

    public static void setThreadAffinity(Affinity... affinities) {
        var platform = Platform.get();
        if (platform == Platform.WINDOWS) {
            long[] msks = new long[affinities.length];
            short[] groups = new short[affinities.length];Arrays.fill(groups, (short) -1);
            int i = 0;
            for (var a : affinities) {
                int idx;
                for (idx = 0; idx<i && groups[idx]!=a.group; idx++);
                if (idx == i) {groups[idx] = a.group; i++;}
                msks[idx] |= a.msk;
            }
            ThreadUtils.SetThreadSelectedCpuSetMasksWin32(Arrays.copyOf(msks, i), Arrays.copyOf(groups, i));
        } else if (platform == Platform.LINUX) {
            Arrays.sort(affinities, (a, b) -> a.group - b.group);
            long[] msks = new long[affinities.length];
            for (int i=0; i<affinities.length; i++) {
                msks[i] = affinities[i].msk;
            }
            ThreadUtils.schedSetaffinityLinux(msks);
        } else {
            Logger.error("Don't know how to set thread affinity on this platform.");
        }
    }

    private static Core[] generateCoreLayoutWindows() {
        var cores = Kernel32Util.getLogicalProcessorInformationEx(WinNT.LOGICAL_PROCESSOR_RELATIONSHIP.RelationProcessorCore);
        boolean allSameClass = true;
        for (var coreO : cores) {
            var core = (WinNT.PROCESSOR_RELATIONSHIP) coreO;
            allSameClass &= core.efficiencyClass == 0;
        }

        int i = 0;
        var res = new Core[cores.length];
        for (var coreO : cores) {
            var core = (WinNT.PROCESSOR_RELATIONSHIP) coreO;
            boolean smt = (core.flags&1)==1;
            byte eclz = core.efficiencyClass;
            if (core.groupMask.length!=1) {
                throw new IllegalStateException("Unsupported architecture");
            }
            var msk = core.groupMask[0].mask.longValue();
            if (Long.bitCount(msk)>1 != smt) {
                throw new IllegalStateException("Logic issue");
            }
            res[i++] = new Core((!allSameClass)&&eclz==0, new Affinity(msk, core.groupMask[0].group));
        }
        sort(res);
        return res;
    }

    private static Core[] generateCoreLayoutLinux() {
        var processor = new SystemInfo().getHardware().getProcessor();
        Int2ObjectOpenHashMap<Affinity> affinityMsk = new Int2ObjectOpenHashMap<>();
        for (var thread : processor.getLogicalProcessors()) {
            var aff = affinityMsk.getOrDefault(thread.getPhysicalProcessorNumber(), new Affinity(0, (short) thread.getProcessorGroup()));
            if (thread.getProcessorGroup() != aff.group) {
                throw new IllegalStateException();
            }
            affinityMsk.put(thread.getPhysicalProcessorNumber(), new Affinity(aff.msk|(1L<<thread.getProcessorNumber()), (short) thread.getProcessorGroup()));
        }

        var cores = new Core[processor.getPhysicalProcessors().size()];
        int i = 0;
        boolean allSameEfficiency = true;
        for (var core : processor.getPhysicalProcessors()) {
            if (core.getEfficiency() != 0) {
                allSameEfficiency = false;
                break;
            }
        }
        for (var core : processor.getPhysicalProcessors()) {
            var aff = affinityMsk.remove(core.getPhysicalProcessorNumber());
            if (aff == null) {
                throw new IllegalStateException();
            }
            cores[i++] = new Core(core.getEfficiency()==0&&!allSameEfficiency, aff);
        }
        sort(cores);
        return cores;
    }


    private static void sort(Core[] cores) {
        Arrays.sort(cores, (a,b)->{
            if (a.isEfficiency == b.isEfficiency) {
                int c = Short.compareUnsigned(a.affinity.group, b.affinity.group);
                if (c==0) {
                    return Long.compareUnsigned(a.affinity.msk, b.affinity.msk);
                }
                return c;
            } else {
                return a.isEfficiency?1:-1;
            }
        });
    }

    public record Affinity(long msk, short group) {}
    public record Core(boolean isEfficiency, Affinity affinity) {

    }

    public static final Core[] CORES;
    static {
        if (Platform.get() == Platform.WINDOWS) {
            CORES = generateCoreLayoutWindows();
        } else if (Platform.get() == Platform.LINUX) {
            CORES = generateCoreLayoutLinux();
        } else {
            CORES = null;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.err.println(Arrays.toString(CORES));
        setThreadAffinity(CORES[0], CORES[1]);
        for (int i = 0; i < 20; i++) {
            int finalI = i;
            new Thread(()->{
                setThreadAffinity(CORES[finalI&3]);
                Random r = new Random();
                int j= 0;
                while (r.nextLong()!=0) {
                    j++;
                }
                System.out.println(j);
            }).start();
        }
        while (true) {
            Thread.sleep(100);
        }
    }
}
