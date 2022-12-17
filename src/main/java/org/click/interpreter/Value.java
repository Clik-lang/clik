package org.click.interpreter;

import org.click.Parameter;
import org.click.Statement;
import org.click.Type;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public sealed interface Value {
    // DECLARATIONS

    record FunctionDecl(List<Parameter> parameters, Type returnType, List<Statement> body,
                        @Nullable Executor lambdaExecutor) implements Value {
    }

    record StructDecl(List<Parameter> parameters) implements Value {
        Type get(String name) {
            for (Parameter parameter : parameters) {
                if (parameter.name().equals(name))
                    return parameter.type();
            }
            return null;
        }
    }

    record EnumDecl(@Nullable Type type, java.util.Map<String, Value> entries) implements Value {
    }

    record UnionDecl(java.util.Map<String, StructDecl> entries) implements Value {
    }

    // VALUES

    record Break() implements Value {
    }

    record Continue() implements Value {
    }

    record IntegerLiteral(Type type, long value) implements Value {
        public IntegerLiteral {
            if (type != Type.U8 && type != Type.U16 && type != Type.U32 && type != Type.U64 &&
                    type != Type.I8 && type != Type.I16 && type != Type.I32 && type != Type.I64 && type != Type.INT)
                throw new RuntimeException("Invalid integer type: " + type);
        }
    }

    record FloatLiteral(Type type, double value) implements Value {
        public FloatLiteral {
            if (type != Type.F32 && type != Type.F64)
                throw new IllegalArgumentException("Constant type must be f32 or f64");
        }
    }

    record BooleanLiteral(boolean value) implements Value {
    }

    record StringLiteral(String value) implements Value {
    }

    record Struct(String name, java.util.Map<String, Value> parameters) implements Value {
    }

    record Union(String name, Value value) implements Value {
    }

    record Array(Type.Array type, List<Value> elements) implements Value {
    }

    record Map(Type.Map type, java.util.Map<Value, Value> entries) implements Value {
    }

    record Range(Value start, Value end, Value step) implements Value {
    }

    // TODO is this really necessary?
    record JavaObject(Object object) implements Value {
    }
}
