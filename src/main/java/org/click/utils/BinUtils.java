package org.click.utils;

import java.lang.foreign.MemorySegment;
import java.util.BitSet;

public final class BinUtils {
    public static String convertByteArray(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int unsignedByte = b & 0xff;
            String binaryString = Integer.toBinaryString(unsignedByte);
            // pad the binary string with zeros to ensure it has 8 digits
            binaryString = String.format("%8s", binaryString).replace(' ', '0');
            sb.append(binaryString);
        }
        return sb.toString();
    }

    public static MemorySegment convertBytes(byte[] bytes) {
        return MemorySegment.ofArray(bytes.clone());
    }

    public static MemorySegment convertString(String value) {
        BitSet bitSet = new BitSet();
        int i = 0;
        for (char c : value.toCharArray()) {
            if (c == ' ') continue;
            if (c != '0' && c != '1')
                throw new IllegalArgumentException("Invalid binary string: " + value);
            if (c == '1') bitSet.set(i);
            i++;
        }
        final byte[] bytes = bitSet.toByteArray();
        return convertBytes(bytes);
    }
}
