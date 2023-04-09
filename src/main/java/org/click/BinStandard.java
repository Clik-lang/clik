package org.click;

import org.click.utils.BinUtils;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.nio.charset.StandardCharsets;

public interface BinStandard {
    MemorySegment serialize(String content);

    MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator);

    BinStandard UTF8 = new BinStandard() {
        @Override
        public MemorySegment serialize(String content) {
            final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            return BinUtils.convertBytes(bytes);
        }

        @Override
        public MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator) {
            if (operator != Token.Type.PLUS)
                throw new IllegalArgumentException("Unsupported string operator: " + operator);
            final long leftSize = left.byteSize();
            final long rightSize = right.byteSize();
            final MemorySegment segment = MemorySegment.allocateNative(leftSize + rightSize, SegmentScope.global());
            MemorySegment.copy(left, 0, segment, 0, leftSize);
            MemorySegment.copy(right, 0, segment, leftSize, rightSize);
            return segment;
        }
    };
    BinStandard I32 = new BinStandard() {
        @Override
        public MemorySegment serialize(String content) {
            final int integer = Integer.parseInt(content);
            String bin = Integer.toBinaryString(integer);
            // Pad with zeros
            bin = "0".repeat(32 - bin.length()) + bin;
            return BinUtils.convertString(bin);
        }

        @Override
        public MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator) {
            throw new UnsupportedOperationException("Not implemented");
        }
    };
    BinStandard I64 = new BinStandard() {
        @Override
        public MemorySegment serialize(String content) {
            final int integer = Integer.parseInt(content);
            String bin = Integer.toBinaryString(integer);
            // Pad with zeros
            bin = "0".repeat(64 - bin.length()) + bin;
            return BinUtils.convertString(bin);
        }

        @Override
        public MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator) {
            throw new UnsupportedOperationException("Not implemented");
        }
    };

    static BinStandard get(String name) {
        return switch (name) {
            case "UTF8" -> UTF8;
            case "I32" -> I32;
            case "I64" -> I64;
            default -> throw new IllegalArgumentException("Unknown binary standard: " + name);
        };
    }
}
