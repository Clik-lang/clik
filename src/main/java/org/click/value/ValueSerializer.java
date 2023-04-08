package org.click.value;

import java.util.List;
import java.util.Map;

public final class ValueSerializer {
    public static String serialize(Value expression) {
        return switch (expression) {
            case Value.NumberLiteral numberLiteral -> String.valueOf(numberLiteral.value());
            case Value.BooleanLiteral booleanLiteral -> String.valueOf(booleanLiteral.value());
            case Value.Struct struct -> {
                final Map<String, Value> parameters = struct.parameters();
                final StringBuilder builder = new StringBuilder();
                builder.append(struct.name()).append("{");
                for (var entry : parameters.entrySet()) {
                    final String name = entry.getKey();
                    final Value value = entry.getValue();
                    builder.append(name).append(": ").append(serialize(value)).append(", ");
                }
                builder.append("}");

                final String content = builder.toString();
                yield content.replace(", }", "}");
            }
            case Value.Union union -> {
                final Value field = union.value();
                yield union.name() + "." + serialize(field);
            }
            case Value.Array array -> {
                final List<Value> values = array.elements();
                final StringBuilder builder = new StringBuilder();
                builder.append("[");
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    builder.append(serialize(value));
                    if (i < values.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append("]");
                yield builder.toString();
            }
            default -> throw new RuntimeException("Unknown expression: " + expression);
        };
    }
}
