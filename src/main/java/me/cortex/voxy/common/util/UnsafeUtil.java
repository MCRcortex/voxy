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

    public static void memcpy(long src, long dst, long length) {
        UNSAFE.copyMemory(src, dst, length);
    }

}
