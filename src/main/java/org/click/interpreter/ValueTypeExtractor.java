package org.click.interpreter;

import org.click.Type;

public final class ValueTypeExtractor {
    public static Type extractType(Value expression) {
        return switch (expression) {
            case Value.Constant constant -> {
                final Object value = constant.value();
                if (value instanceof String) yield Type.STRING;
                if (value instanceof Integer) yield Type.I32;
                throw new RuntimeException("Unknown constant type: " + value);
            }
            case Value.Struct struct -> Type.of(struct.name());
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }
}
