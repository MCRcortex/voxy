package me.cortex.voxy.client.compat;

import me.cortex.voxy.common.Logger;
import org.lwjgl.system.*;

import static org.lwjgl.system.APIUtil.*;
import static org.lwjgl.system.Checks.*;
import static org.lwjgl.system.JNI.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Native bindings to Kernel32 library. */
public class Kernel32 {

    private static SharedLibrary KERNEL32;

    /** Contains the function pointers loaded from the kernel32 {@link SharedLibrary}. */
    public static final class Functions {

        private Functions() {}

        /** Function address. */
        public static Long GetCurrentProcess, GetCurrentProcessId, GetProcessId, GetCurrentThread, GetCurrentThreadId, GetThreadId, GetProcessIdOfThread, GetCurrentProcessorNumber;

        public static void init() {
            if (GetCurrentProcess == null)
                GetCurrentProcess           = apiGetFunctionAddress(Kernel32.getLibrary(), "GetCurrentProcess");
            if (GetCurrentProcessId == null)
                GetCurrentProcessId         = apiGetFunctionAddress(Kernel32.getLibrary(), "GetCurrentProcessId");
            if (GetProcessId == null)
                GetProcessId                = apiGetFunctionAddress(Kernel32.getLibrary(), "GetProcessId");
            if (GetCurrentThread == null)
                GetCurrentThread            = apiGetFunctionAddress(Kernel32.getLibrary(), "GetCurrentThread");
            if (GetCurrentThreadId == null)
                GetCurrentThreadId          = apiGetFunctionAddress(Kernel32.getLibrary(), "GetCurrentThreadId");
            if (GetThreadId == null)
                GetThreadId                 = apiGetFunctionAddressOptional(Kernel32.getLibrary(), "GetThreadId");
            if (GetProcessIdOfThread == null)
                GetProcessIdOfThread        = apiGetFunctionAddressOptional(Kernel32.getLibrary(), "GetProcessIdOfThread");
            if (GetCurrentProcessorNumber == null)
                GetCurrentProcessorNumber   = apiGetFunctionAddressOptional(Kernel32.getLibrary(), "GetCurrentProcessorNumber");
        }

        private static long apiGetFunctionAddressOptional(SharedLibrary library, String functionName) {
            long a = library.getFunctionAddress(functionName);
            if (a == NULL) {
                Logger.error("[LWJGL] Failed to locate address for " + library.getName() + " function " + functionName + "\n");
            }
            return a;
        }
    }

    /** Returns the kernel32 {@link SharedLibrary}. */
    public static SharedLibrary getLibrary() {
        if (KERNEL32 == null) {
            KERNEL32 = Library.loadNative(me.cortex.voxy.client.compat.Kernel32.class, "org.lwjgl", "kernel32");
        }
        return KERNEL32;
    }

    protected Kernel32() {
        throw new UnsupportedOperationException();
    }

    // --- [ GetCurrentProcess ] ---

    @NativeType("HANDLE")
    public static long GetCurrentProcess() {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetCurrentProcess;
        return callP(__functionAddress);
    }

    // --- [ GetCurrentProcessId ] ---

    @NativeType("DWORD")
    public static int GetCurrentProcessId() {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetCurrentProcessId;
        return callI(__functionAddress);
    }

    // --- [ GetProcessId ] ---

    @NativeType("DWORD")
    public static int GetProcessId(@NativeType("HANDLE") long Process) {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetProcessId;
        if (CHECKS) {
            check(Process);
        }
        return callPI(Process, __functionAddress);
    }

    // --- [ GetCurrentThread ] ---

    @NativeType("HANDLE")
    public static long GetCurrentThread() {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetCurrentThread;
        return callP(__functionAddress);
    }

    // --- [ GetCurrentThreadId ] ---

    @NativeType("DWORD")
    public static int GetCurrentThreadId() {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetCurrentThreadId;
        return callI(__functionAddress);
    }

    // --- [ GetThreadId ] ---

    @NativeType("DWORD")
    public static int GetThreadId(@NativeType("HANDLE") long Thread) {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetThreadId;
        if (CHECKS) {
            check(__functionAddress);
            check(Thread);
        }
        return callPI(Thread, __functionAddress);
    }

    // --- [ GetProcessIdOfThread ] ---

    @NativeType("DWORD")
    public static int GetProcessIdOfThread(@NativeType("HANDLE") long Thread) {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetProcessIdOfThread;
        if (CHECKS) {
            check(__functionAddress);
            check(Thread);
        }
        return callPI(Thread, __functionAddress);
    }

    // --- [ GetCurrentProcessorNumber ] ---

    @NativeType("DWORD")
    public static int GetCurrentProcessorNumber() {
        me.cortex.voxy.client.compat.Kernel32.Functions.init();
        long __functionAddress = me.cortex.voxy.client.compat.Kernel32.Functions.GetCurrentProcessorNumber;
        if (CHECKS) {
            check(__functionAddress);
        }
        return callI(__functionAddress);
    }

}