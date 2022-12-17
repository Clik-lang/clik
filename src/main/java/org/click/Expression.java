package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Expression {
    record Function(boolean async, List<Parameter> parameters, Type returnType,
                    List<Statement> body) implements Expression {
    }

    record Struct(List<Parameter> parameters) implements Expression {
    }

    record Enum(@Nullable Type type, Map<String, Expression> entries) implements Expression {
    }

    record Union(Map<String, @Nullable Struct> entries) implements Expression {
    }

    record IntegerLiteral(Type type, long value) implements Expression {
        public IntegerLiteral {
            if (type != Type.U8 && type != Type.U16 && type != Type.U32 && type != Type.U64 &&
                    type != Type.I8 && type != Type.I16 && type != Type.I32 && type != Type.I64 && type != Type.INT)
                throw new RuntimeException("Invalid integer type: " + type);
        }
    }

    record FloatLiteral(Type type, double value) implements Expression {
        public FloatLiteral {
            if (type != Type.F32 && type != Type.F64)
                throw new IllegalArgumentException("Constant type must be f32 or f64");
        }
    }

    record BooleanLiteral(boolean value) implements Expression {
    }

    record StringLiteral(String value) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Group(Expression expression) implements Expression {
    }

    record Field(Expression object, String name) implements Expression {
    }

    record VariableAwait(String name) implements Expression {
    }

    record ArrayAccess(Expression array, Expression index) implements Expression {
    }

    record Call(String name, Parameter.Passed arguments) implements Expression {
    }

    record StructValue(String name, Parameter.Passed fields) implements Expression {
    }

    record ArrayValue(Type.Array type, @Nullable List<Expression> expressions) implements Expression {
    }

    record MapValue(Type.Map type, Map<Expression, Expression> entries) implements Expression {
    }

    record InitializationBlock(Parameter.Passed parameters) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Token operator, Expression right) implements Expression {
    }
}
