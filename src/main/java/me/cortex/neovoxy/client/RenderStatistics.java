package me.cortex.neovoxy.client;

import me.cortex.neovoxy.common.world.WorldEngine;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RenderStatistics {
    public static boolean enabled = false;

    public static final int[] hierarchicalTraversalCounts = new int[WorldEngine.MAX_LOD_LAYER+1];
    public static final int[] hierarchicalRenderSections = new int[WorldEngine.MAX_LOD_LAYER+1];
    public static final int[] visibleSections = new int[WorldEngine.MAX_LOD_LAYER+1];
    public static final int[] quadCount = new int[WorldEngine.MAX_LOD_LAYER+1];


    public static void addDebug(List<String> debug) {
        if (!enabled) {
            return;
        }
        debug.add("HTC: [" + Arrays.stream(flipCopy(RenderStatistics.hierarchicalTraversalCounts)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
        debug.add("HRS: [" + Arrays.stream(flipCopy(RenderStatistics.hierarchicalRenderSections)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
        debug.add("VS: [" + Arrays.stream(flipCopy(RenderStatistics.visibleSections)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
        debug.add("QC: [" + Arrays.stream(flipCopy(RenderStatistics.quadCount)).mapToObj(Integer::toString).collect(Collectors.joining(", "))+"]");
    }

    private static int[] flipCopy(int[] array) {
        int[] ret = new int[array.length];
        int i = ret.length;
        for (int j : array) {
            ret[--i] = j;
        }
        return ret;
    }
}
