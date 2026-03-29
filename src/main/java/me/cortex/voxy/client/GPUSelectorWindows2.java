package me.cortex.voxy.client;

import me.cortex.voxy.client.compat.Kernel32;
import me.cortex.voxy.common.Logger;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.windows.GDI32;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.lwjgl.system.MemoryUtil.NULL;

public class GPUSelectorWindows2 {
    private static long apiGetFunctionAddressOptional(SharedLibrary library, String functionName) {
        long a = library.getFunctionAddress(functionName);
        if (a == NULL) {
            Logger.error("[LWJGL] Failed to locate address for " + library.getName() + " function " + functionName + "\n");
        }
        return a;
    }

    private static final long D3DKMTSetProperties = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTSetProperties");
    private static final long D3DKMTEnumAdapters2 = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTEnumAdapters2");
    private static final long D3DKMTCloseAdapter = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTCloseAdapter");
    private static final long D3DKMTQueryAdapterInfo = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTQueryAdapterInfo");

    private static int setPCIProperties(int type, int vendor, int device, int subSys) {
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(0x10).order(ByteOrder.nativeOrder());
            buff.putInt(0, vendor);
            buff.putInt(4, device);
            buff.putInt(8, subSys);
            buff.putInt(12, 0);
            return setProperties(type, buff);
        }
    }

    private static int setProperties(int type, ByteBuffer payload) {
        if (D3DKMTSetProperties == 0) {
            return -1;
        }
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(0x18).order(ByteOrder.nativeOrder());
            buff.putInt(0, type);
            buff.putInt(4, payload.remaining());
            buff.putLong(16, MemoryUtil.memAddress(payload));
            return JNI.callPI(MemoryUtil.memAddress(buff), D3DKMTSetProperties);
        }
    }

    private static int query(int handle, int type, ByteBuffer payload) {
        if (D3DKMTQueryAdapterInfo == 0) {
            return -1;
        }
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(0x14).order(ByteOrder.nativeOrder());
            buff.putInt(0, handle);
            buff.putInt(4, type);
            buff.putLong(8, MemoryUtil.memAddress(payload));
            buff.putInt(16, payload.remaining());
            return JNI.callPI(MemoryUtil.memAddress(buff), D3DKMTQueryAdapterInfo);
        }
    }

    private static int closeHandle(int handle) {
        if (D3DKMTCloseAdapter == 0) {
            return -1;
        }
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(0x4).order(ByteOrder.nativeOrder());
            buff.putInt(0, handle);
            return JNI.callPI(MemoryUtil.memAddress(buff), D3DKMTCloseAdapter);
        }
    }

    private static int queryAdapterType(int handle, int[] out) {
        int ret;
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(0x4).order(ByteOrder.nativeOrder());
            //KMTQAITYPE_ADAPTERTYPE
            if ((ret=query(handle, 15, buff))<0) {
                return ret;
            }
            out[0] = buff.getInt(0);
        }
        return 0;
    }

    private static int queryAdapterIcd(int handle, String[] out) {
        int ret;
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(260*2+8).order(ByteOrder.nativeOrder());
            //KMTQAITYPE_UMOPENGLINFO
            if ((ret=query(handle, 2, buff))<0) {
                return ret;
            }
            int len = Math.min(MemoryUtil.memLengthNT2(buff), 260*2);
            out[0] = MemoryUtil.memUTF16(buff.limit(len));
        }
        return 0;
    }

    private record AdapterInfo(String icdPath, int type, long luid, int vendor, int device, int subSystem) {
        @Override
        public String toString() {
            String LUID = Integer.toHexString((int) ((luid>>>32)&0xFFFFFFFFL))+"-"+Integer.toHexString((int) (luid&0xFFFFFFFFL));
            return "{type=%s, luid=%s, vendor=%s, device=%s, subSys=%s, icd=\"%s\"}".formatted(Integer.toString(type),LUID, Integer.toHexString(vendor), Integer.toHexString(device), Integer.toHexString(subSystem), icdPath);
        }
    }

    private record PCIDeviceId(int vendor, int device, int subVendor, int subSystem, int revision, int busType) {}
    private static int queryPCIAddress(int handle, int index, PCIDeviceId[] deviceOut) {
        int ret = 0;
        try (var stack = MemoryStack.stackPush()) {
            var buff = stack.calloc(4*7).order(ByteOrder.nativeOrder());
            buff.putInt(0, index);
            //KMTQAITYPE_PHYSICALADAPTERDEVICEIDS
            if ((ret = query(handle, 31, buff)) < 0) {
                return ret;
            }
            deviceOut[0] = new PCIDeviceId(buff.getInt(4),buff.getInt(8),buff.getInt(12),buff.getInt(16),buff.getInt(20),buff.getInt(24));
            return 0;
        }
    }

    private static int enumAdapters(Consumer<AdapterInfo> consumer) {
        if (D3DKMTEnumAdapters2 == 0) {
            return -1;
        }

        int ret = 0;
        try (var stack = MemoryStack.stackPush()) {
            var query = stack.calloc(0x10).order(ByteOrder.nativeOrder());
            if ((ret = JNI.callPI(MemoryUtil.memAddress(query), D3DKMTEnumAdapters2)) < 0) {
                return ret;
            }
            int adapterCount = query.getInt(0);
            var adapterList = stack.calloc(0x14 * adapterCount).order(ByteOrder.nativeOrder());
            query.putLong(8, MemoryUtil.memAddress(adapterList));
            if ((ret = JNI.callPI(MemoryUtil.memAddress(query), D3DKMTEnumAdapters2)) < 0) {
                return ret;
            }
            adapterCount = query.getInt(0);
            for (int adapterIndex = 0; adapterIndex < adapterCount; adapterIndex++) {
                var adapter = adapterList.slice(adapterIndex*0x14, 0x14).order(ByteOrder.nativeOrder());
                //We only care about these 2
                int handle = adapter.getInt(0);
                long luid = adapter.getLong(4);

                int[] type = new int[1];
                if ((ret = queryAdapterType(handle, type))<0) {
                    Logger.error("Query type error: " + ret);
                    //We errored
                    if (closeHandle(handle) < 0) {
                        throw new IllegalStateException();
                    }
                    continue;
                }

                String[] icd = new String[1];
                if ((ret = queryAdapterIcd(handle, icd))<0) {
                    Logger.error("Query icd error: " + ret);
                    //We errored
                    if (closeHandle(handle) < 0) {
                        throw new IllegalStateException();
                    }
                    continue;
                }


                PCIDeviceId[] out = new PCIDeviceId[1];
                //Get the root adapter device
                if ((ret = queryPCIAddress(handle, 0, out)) < 0) {
                    Logger.error("Query pci error: " + ret);
                    //We errored
                    if (closeHandle(handle) < 0) {
                        throw new IllegalStateException();
                    }
                    continue;
                }

                int subSys = (out[0].subSystem<<16)|out[0].subVendor;//It seems the pci subsystem address is a joined integer
                consumer.accept(new AdapterInfo(icd[0], type[0], luid, out[0].vendor, out[0].device, subSys));

                if (closeHandle(handle) < 0) {
                    throw new IllegalStateException();
                }
            }
        }

        return 0;
    }


    //=======================================================================================

    private static final int[] HDC_STUB = { 0x48, 0x83, 0xC1, 0x0C, 0x48, 0xB8, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x1F, 0x48, 0x89, 0x01, 0x48, 0xB8, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x2F, 0x51, 0xFF, 0xD0, 0x59, 0x8B, 0x41, 0x08, 0x89, 0x41, 0xFC, 0x48, 0x31, 0xC0, 0x89, 0x41, 0x08, 0xC3 };
    private static void insertLong(long l, byte[] out, int offset) {
        for (int i = 0; i < 8; i++) {
            out[i+offset] = (byte)(l & 0xFF);
            l >>= 8;
        }
    }
    private static byte[] createFinishedHDCStub(long luid) {
        byte[] stub = new byte[HDC_STUB.length];
        for (int i = 0; i < stub.length; i++) {
            stub[i] = (byte) HDC_STUB[i];
        }
        insertLong(luid, stub, 6);
        insertLong(D3DKMTOpenAdapterFromLuid, stub, 19);
        return stub;
    }

    private static final long D3DKMTOpenAdapterFromLuid = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTOpenAdapterFromLuid");
    private static final long D3DKMTOpenAdapterFromHdc = apiGetFunctionAddressOptional(GDI32.getLibrary(), "D3DKMTOpenAdapterFromHdc");
    private static final long VirtualProtect = apiGetFunctionAddressOptional(Kernel32.getLibrary(), "VirtualProtect");

    private static byte[] toByteArray(int... array) {
        byte[] res = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            res[i] = (byte) array[i];
        }
        return res;
    }
    private static void VirtualProtect(long addr, long size) {
        try (var stack = MemoryStack.stackPush()) {
            var oldProtection = stack.calloc(4);
            JNI.callPPPPI(addr, size, 0x40/*PAGE_EXECUTE_READWRITE*/, MemoryUtil.memAddress(oldProtection), VirtualProtect);
        }
    }
    private static void memcpy(long ptr, byte[] data) {
        for (int i = 0; i < data.length; i++) {
            MemoryUtil.memPutByte(ptr + i, data[i]);
        }
    }

    private static void installHDCStub(long adapterLuid) {
        if (D3DKMTOpenAdapterFromHdc == 0 || VirtualProtect == 0 || D3DKMTOpenAdapterFromLuid == 0) {
            return;
        }
        Logger.info("AdapterLuid callback at: " + Long.toHexString(D3DKMTOpenAdapterFromLuid));
        var stub = createFinishedHDCStub(adapterLuid);

        VirtualProtect(D3DKMTOpenAdapterFromHdc, stub.length);
        memcpy(D3DKMTOpenAdapterFromHdc, stub);
    }


    private static byte[] createIntelStub(long origA, long origB, long jmpA, long jmpB) {
        byte[] stub = toByteArray(0xFE, 0x0D, 0x63, 0x00, 0x00, 0x00, 0x80, 0x3D, 0x5C, 0x00, 0x00, 0x00, 0x02, 0x75, 0x07, 0x48, 0x31, 0xC0, 0x48, 0xF7, 0xD0, 0xC3, 0x48, 0xB8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x48, 0x8B, 0x0D, 0x43, 0x00, 0x00, 0x00, 0x48, 0x89, 0x08, 0x48, 0x8B, 0x0D, 0x41, 0x00, 0x00, 0x00, 0x48, 0x89, 0x48, 0x08, 0x80, 0x3D, 0x2D, 0x00, 0x00, 0x00, 0x00, 0x74, 0x1D, 0x50, 0xFF, 0xD0, 0x58, 0x48, 0x8B, 0x0D, 0x31, 0x00, 0x00, 0x00, 0x48, 0x89, 0x08, 0x48, 0x8B, 0x0D, 0x2F, 0x00, 0x00, 0x00, 0x48, 0x89, 0x48, 0x08, 0x48, 0x31, 0xC0, 0xC3, 0x48, 0x8B, 0x41, 0x08, 0xC7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x48, 0x31, 0xC0, 0xC3, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0);
        insertLong(D3DKMTQueryAdapterInfo, stub, 24);
        stub[0x69] = 3;
        insertLong(origA, stub, 0x6a);
        insertLong(origB, stub, 0x6a+8);
        insertLong(jmpA, stub, 0x6a+16);
        insertLong(jmpB, stub, 0x6a+24);
        return stub;
    }

    private static byte[] createSimpleStub(long origA, long origB) {
        byte[] stub = toByteArray(0x48, 0xB8, 0, 0, 0, 0, 0, 0, 0, 0, 0x48, 0x8B, 0x0D, 0x15, 0x00, 0x00, 0x00, 0x48, 0x89, 0x08, 0x48, 0x8B, 0x0D, 0x13, 0x00, 0x00, 0x00, 0x48, 0x89, 0x48, 0x08, 0x48, 0x31, 0xC0, 0x48, 0xF7, 0xD0, 0xC3, 0,0,0,0,0,0,0,0, 0,0,0,0,0,0,0,0);
        insertLong(D3DKMTQueryAdapterInfo, stub, 2);
        insertLong(origA, stub, 38);
        insertLong(origB, stub, 38+8);
        return stub;
    }

    private static void installQueryStub(boolean installIntelBypass) {
        if (D3DKMTQueryAdapterInfo == 0 || VirtualProtect == 0) {
            return;
        }
        VirtualProtect(D3DKMTQueryAdapterInfo, 0x10);
        int MAX_STUB_SIZE = 1024;
        long stubPtr = MemoryUtil.nmemAlloc(MAX_STUB_SIZE);
        VirtualProtect(stubPtr, MAX_STUB_SIZE);
        Logger.info("Do stub at: " + Long.toHexString(stubPtr));

        long origA = MemoryUtil.memGetLong(D3DKMTQueryAdapterInfo);
        long origB = MemoryUtil.memGetLong(D3DKMTQueryAdapterInfo+8);

        var jmpStub = new byte[]{ 0x48, (byte) 0xB8, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xE0};
        insertLong(stubPtr, jmpStub, 2);

        memcpy(D3DKMTQueryAdapterInfo, jmpStub);
        Logger.info("D3DKMTQueryAdapterInfo at: " + Long.toHexString(D3DKMTQueryAdapterInfo));

        long jmpA = MemoryUtil.memGetLong(D3DKMTQueryAdapterInfo);
        long jmpB = MemoryUtil.memGetLong(D3DKMTQueryAdapterInfo+8);

        byte[] stub;
        if (installIntelBypass) {
            stub = createIntelStub(origA, origB, jmpA, jmpB);
        } else {
            stub = createSimpleStub(origA, origB);
        }
        memcpy(stubPtr, stub);
        Logger.info("QueryAdapterInfo stubs installed");
    }


    public static void doSelector(int index) {
        List<AdapterInfo> adapters = new ArrayList<>();
        //Must support rendering and must not be software renderer
        if (enumAdapters(adapter->{if ((adapter.type&5)==1) adapters.add(adapter); }) < 0) {
            return;
        }
        for (var adapter : adapters) {
            Logger.error(adapter.toString());
        }
        var adapter = adapters.get(index);

        installHDCStub(adapter.luid);
        installQueryStub(adapter.icdPath.matches("\\\\ig[a-z0-9]+icd(32|64)\\.dll$"));

        setPCIProperties(1/*USER*/, adapter.vendor, adapter.device, adapter.subSystem);
        setPCIProperties(2/*USER GLOBAL*/, adapter.vendor, adapter.device, adapter.subSystem);
    }
}
