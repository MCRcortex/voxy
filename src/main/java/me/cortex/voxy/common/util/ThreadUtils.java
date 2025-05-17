package me.cortex.voxy.common.util;

import org.lwjgl.system.JNI;
import org.lwjgl.system.Platform;
import org.lwjgl.system.windows.Kernel32;

//Platform specific code to assist in thread utilities
public class ThreadUtils {
    public static final int WIN32_THREAD_PRIORITY_TIME_CRITICAL = 15;
    public static final int WIN32_THREAD_PRIORITY_LOWEST = -2;
    public static final int WIN32_THREAD_MODE_BACKGROUND_BEGIN = 0x00010000;
    public static final int WIN32_THREAD_MODE_BACKGROUND_END = 0x00020000;
    private static final boolean isWindows = Platform.get() == Platform.WINDOWS;
    private static final long SetThreadPriority;
    static {
        if (isWindows) {
            SetThreadPriority = Kernel32.getLibrary().getFunctionAddress("SetThreadPriority");
        } else {
            SetThreadPriority = 0;
        }
    }

    public static boolean SetSelfThreadPriorityWin32(int priority) {
        if (SetThreadPriority == 0 || !isWindows) {
            return false;
        }
        if (JNI.callPI(Kernel32.GetCurrentThread(), priority, SetThreadPriority)==0) {
            throw new IllegalStateException("Operation failed");
        }
        return true;
    }
}
