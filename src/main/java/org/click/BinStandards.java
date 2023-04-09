package org.click;

import org.click.utils.BinUtils;

import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

public final class BinStandards {
    public static MemorySegment serialize(String name, String content) {
        return switch (name) {
            case "UTF8" -> BinStandards.serializeUTF8(content);
            case "I32" -> BinStandards.serializeI32(content);
            default -> throw new IllegalArgumentException("Unknown binary standard: " + name);
        };
    }

    private static MemorySegment serializeUTF8(String content) {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return BinUtils.convertBytes(bytes);
    }

    private static MemorySegment serializeI32(String content) {
        final int integer = Integer.parseInt(content);
        String bin = Integer.toBinaryString(integer);
        // Pad with zeros
        bin = "0".repeat(32 - bin.length()) + bin;
        return BinUtils.convertString(bin);
    }
}
