package me.cortex.voxy.common.util;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class LevelHelper {
    public static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getWorldId(World world) {
        String data = world.getBiomeAccess().seed + world.getRegistryKey().toString();
        try {
            return bytesToHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes())).substring(0, 32);
        } catch (
                NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
