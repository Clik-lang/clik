package org.click.value;

import org.click.Type;

public final class ValueType {
    public static Value defaultValue(Type type) {
        if (type == Type.U8 || type == Type.U16 || type == Type.U32 || type == Type.U64 ||
                type == Type.I8 || type == Type.I16 || type == Type.I32 || type == Type.I64 || type == Type.INT) {
            return new Value.IntegerLiteral(type, 0);
        }
        if (type == Type.F32 || type == Type.F64 || type == Type.FLOAT) {
            return new Value.FloatLiteral(type, 0);
        }
        if (type == Type.BOOL) {
            return new Value.BooleanLiteral(false);
        }
        throw new RuntimeException("Unknown type: " + type);
    }

    public static long sizeOf(Type type) {
        if (type == Type.BOOL) return 1;
        if (type == Type.I8) return 1;
        if (type == Type.U8) return 1;
        if (type == Type.I16) return 2;
        if (type == Type.U16) return 2;
        if (type == Type.I32) return 4;
        if (type == Type.U32) return 4;
        if (type == Type.I64) return 8;
        if (type == Type.U64) return 8;
        if (type == Type.F32) return 4;
        if (type == Type.F64) return 8;

        if (type == Type.INT) return 8;
        if (type == Type.UINT) return 8;
        throw new RuntimeException("Unknown type: " + type);
    }

    public static Type extractAssignmentType(Value expression) {
        return switch (expression) {
            case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
            case Value.FloatLiteral floatLiteral -> floatLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.Struct struct -> Type.of(struct.name());
            case Value.Array array -> array.arrayType();
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }
}
