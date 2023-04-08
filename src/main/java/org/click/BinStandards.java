package org.click;

import org.click.utils.BinUtils;
import org.roaringbitmap.RoaringBitmap;
import org.roaringbitmap.buffer.ImmutableRoaringBitmap;

import java.nio.charset.StandardCharsets;

public final class BinStandards {
    public static ImmutableRoaringBitmap serialize(String name, String content) {
        return switch (name) {
            case "UTF8" -> BinStandards.serializeUTF8(content);
            case "I32" -> BinStandards.serializeI32(content);
            default -> throw new IllegalArgumentException("Unknown binary standard: " + name);
        };
    }

    private static ImmutableRoaringBitmap serializeUTF8(String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        RoaringBitmap bitmap = new RoaringBitmap();
        BinUtils.forEachBit(bytes, bitmap::add);
        return bitmap.toMutableRoaringBitmap().toImmutableRoaringBitmap();
    }

    public static ImmutableRoaringBitmap serializeI32(String content) {
        final int integer = Integer.parseInt(content);
        String bin = Integer.toBinaryString(integer);
        // Pad with zeros
        bin = "0".repeat(32 - bin.length()) + bin;
        return BinUtils.convertString(bin);
    }
}
