package org.click.value;

import org.click.Type;

public final class ValueType {
    public static Value defaultValue(Type type) {
        if (type == Type.NATURAL || type == Type.INT ||
                type == Type.RATIONAL || type == Type.REAL) {
            return new Value.NumberLiteral(type, 0);
        }
        if (type == Type.BOOL) {
            return new Value.BooleanLiteral(false);
        }
        throw new RuntimeException("Unknown type: " + type);
    }

    public static long requireInteger(Value value) {
        if (!(value instanceof Value.NumberLiteral))
            throw new RuntimeException("Expected integer, got " + value);
        return ((Value.NumberLiteral) value).value().longValue();
    }

    public static Type extractAssignmentType(Value expression) {
        return switch (expression) {
            case Value.NumberLiteral numberLiteral -> numberLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.RuneLiteral ignored -> Type.RUNE;
            case Value.Struct struct -> Type.of(struct.name());
            case Value.Enum en -> Type.of(en.name());
            case Value.Union union -> Type.of(union.name());
            case Value.Input input -> input.type();
            case Value.Output output -> output.type();
            case Value.Array array -> array.arrayType();
            case Value.FunctionDecl functionDecl ->
                    new Type.Function(functionDecl.parameters(), functionDecl.returnType());
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }
}
