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

    public static void memcpy(long src, long dst, long length) {
        UNSAFE.copyMemory(src, dst, length);
    }



    //Copy the entire length of src to the dst memory where dst is a byte array (source length from dst)
    public static void memcpy(long src, byte[] dst) {
        UNSAFE.copyMemory(0, src, dst, BYTE_ARRAY_BASE_OFFSET, dst.length);
    }

    //Copy the entire length of src to the dst memory where src is a byte array (source length from src)
    public static void memcpy(byte[] src, long dst) {
        UNSAFE.copyMemory(src, BYTE_ARRAY_BASE_OFFSET, null, dst, src.length);
    }


}
