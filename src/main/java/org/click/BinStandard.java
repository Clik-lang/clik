package org.click;

import org.click.value.LiteralValue;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

public interface BinStandard {
    MemorySegment serialize(LiteralValue literal);

    String asString(MemorySegment segment);

    MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator);

    BinStandard UTF8 = new BinStandard() {
        @Override
        public MemorySegment serialize(LiteralValue literal) {
            if (!(literal instanceof LiteralValue.Text text))
                throw new IllegalArgumentException("Expected string literal, got: " + literal);
            final byte[] bytes = text.value().getBytes(StandardCharsets.UTF_8);
            return MemorySegment.ofArray(bytes);
        }

        @Override
        public String asString(MemorySegment segment) {
            final byte[] bytes = segment.toArray(ValueLayout.JAVA_BYTE);
            return new String(bytes, StandardCharsets.UTF_8);
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
        public MemorySegment serialize(LiteralValue literal) {
            if (!(literal instanceof LiteralValue.Number number))
                throw new IllegalArgumentException("Expected number literal, got: " + literal);
            final int num = number.value().intValue();
            return MemorySegment.ofArray(new int[]{num});
        }

        @Override
        public String asString(MemorySegment segment) {
            final int num = segment.get(ValueLayout.JAVA_INT, 0);
            return String.valueOf(num);
        }

        @Override
        public MemorySegment operate(MemorySegment left, MemorySegment right, Token.Type operator) {
            throw new UnsupportedOperationException("Not implemented");
        }
    };
    BinStandard I64 = new BinStandard() {
        @Override
        public MemorySegment serialize(LiteralValue literal) {
            if (!(literal instanceof LiteralValue.Number number))
                throw new IllegalArgumentException("Expected number literal, got: " + literal);
            final long num = number.value().longValue();
            return MemorySegment.ofArray(new long[]{num});
        }

        @Override
        public String asString(MemorySegment segment) {
            final long num = segment.get(ValueLayout.JAVA_LONG, 0);
            return String.valueOf(num);
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
