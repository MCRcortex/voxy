package me.cortex.voxy.client.core.rendering;

import me.cortex.voxy.client.core.gl.shader.IShaderProcessor;
import me.cortex.voxy.client.core.gl.shader.PrintfInjector;

import java.util.ArrayList;
import java.util.List;

public final class PrintfDebugUtil {
    public static final boolean ENABLE_PRINTF_DEBUGGING = System.getProperty("voxy.enableShaderDebugPrintf", "false").equals("true");

    private static final List<String> printfQueue2 = new ArrayList<>();
    private static final List<String> printfQueue = new ArrayList<>();
    private static final IShaderProcessor PRINTF;
    public static final PrintfInjector PRINTF_object;


    static {
        if (ENABLE_PRINTF_DEBUGGING) {
            PRINTF_object = new PrintfInjector(50000, 10, line -> {
                if (line.startsWith("LOG")) {
                    System.err.println(line);
                }
                printfQueue.add(line);
            }, printfQueue::clear);
            PRINTF = PRINTF_object;
        } else {
            PRINTF_object = null;
            //Todo add a dummy processor that just removes all the printf calls
            PRINTF = null;
        }
    }

    public static void tick() {
        if (ENABLE_PRINTF_DEBUGGING) {
            printfQueue2.clear();
            printfQueue2.addAll(printfQueue);
            printfQueue.clear();
            PRINTF_object.download();
        }
    }

    public static void addToOut(List<String> out) {
        if (ENABLE_PRINTF_DEBUGGING) {
            out.add("Printf Queue: ");
            out.addAll(printfQueue2);
        }
    }
}
