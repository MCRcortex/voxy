package me.cortex.voxy.common.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class UnsafeUtil {
    private static final Unsafe UNSAFE;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe)field.get(null);
        } catch (Exception e) {throw new RuntimeException(e);}
    }

    private static final long BYTE_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final long SHORT_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(short[].class);
    private static final long LONG_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(long[].class);

    public static void memcpy(long src, long dst, long length) {
        UNSAFE.copyMemory(src, dst, length);
    }



    //Copy the entire length of src to the dst memory where dst is a byte array (source length from dst)
    public static void memcpy(long src, byte[] dst) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET, dst.length);
    }

    public static void memcpy(long src, int length, byte[] dst) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET, length);
    }

    public static void memcpy(long src, int length, byte[] dst, int offset) {
        UNSAFE.copyMemory(null, src, dst, BYTE_ARRAY_BASE_OFFSET+offset, length);
    }

    //Copy the entire length of src to the dst memory where src is a byte array (source length from src)
    public static void memcpy(byte[] src, long dst) {
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET, null, dst, src.length);
    }

    public static void memcpy(byte[] src, int len, long dst) {
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET, null, dst, len);
    }
    public static void memcpy(short[] src, long dst) {
        UNSAFE.copyMemory(src, SHORT_ARRAY_BASE_OFFSET, null, dst, (long) src.length <<1);
    }
    public static void memcpy(long[] src, long dst) {
        UNSAFE.copyMemory(src, LONG_ARRAY_BASE_OFFSET, null, dst, (long) src.length <<3);
    }


    //Cause lwjgl is being a clown with var handles, we need to do things ourselves


    public static boolean memGetBoolean(long ptr) { return UNSAFE.getByte(null, ptr) != 0; }
    public static byte memGetByte(long ptr)       { return UNSAFE.getByte(null, ptr); }
    public static short memGetShort(long ptr)     { return UNSAFE.getShort(null, ptr); }
    public static int memGetInt(long ptr)         { return UNSAFE.getInt(null, ptr); }
    public static long memGetLong(long ptr)       { return UNSAFE.getLong(null, ptr); }
    public static float memGetFloat(long ptr)     { return UNSAFE.getFloat(null, ptr); }
    public static double memGetDouble(long ptr)   { return UNSAFE.getDouble(null, ptr); }

    public static void memPutByte(long ptr, byte value)     { UNSAFE.putByte(null, ptr, value); }
    public static void memPutShort(long ptr, short value)   { UNSAFE.putShort(null, ptr, value); }
    public static void memPutInt(long ptr, int value)       { UNSAFE.putInt(null, ptr, value); }
    public static void memPutLong(long ptr, long value)     { UNSAFE.putLong(null, ptr, value); }
    public static void memPutFloat(long ptr, float value)   { UNSAFE.putFloat(null, ptr, value); }
    public static void memPutDouble(long ptr, double value) { UNSAFE.putDouble(null, ptr, value); }

}
