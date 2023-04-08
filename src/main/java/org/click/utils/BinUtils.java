package org.click.utils;

import java.util.function.IntConsumer;

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

    public static void forEachBit(byte[] bytes, IntConsumer consumer) {
        int bitIndex = 0;
        for (byte b : bytes) {
            int unsignedByte = b & 0xff;
            for (int i = 0; i < 8; i++) {
                if ((unsignedByte & (1 << i)) != 0) {
                    final int position = bitIndex + (7 - i);
                    consumer.accept(position);
                }
            }
            bitIndex += 8;
        }
    }
}
