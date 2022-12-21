package org.click.value;

import org.click.Parameter;
import org.click.Statement;
import org.click.Type;
import org.click.interpreter.Executor;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.List;

public sealed interface Value {
    // DECLARATIONS

    record FunctionDecl(List<Parameter> parameters, Type returnType, List<Statement> body,
                        @Nullable Executor lambdaExecutor) implements Value {
        public FunctionDecl {
            parameters = List.copyOf(parameters);
            body = List.copyOf(body);
        }
    }

    record StructDecl(List<Parameter> parameters) implements Value {
        public StructDecl {
            parameters = List.copyOf(parameters);
        }

        public Type get(String name) {
            for (Parameter parameter : parameters) {
                if (parameter.name().equals(name))
                    return parameter.type();
            }
            return null;
        }
    }

    record EnumDecl(@Nullable Type type, java.util.Map<String, Value> entries) implements Value {
        public EnumDecl {
            entries = java.util.Map.copyOf(entries);
        }
    }

    record UnionDecl(java.util.Map<String, StructDecl> entries) implements Value {
        public UnionDecl {
            entries = java.util.Map.copyOf(entries);
        }
    }

    // VALUES

    record Break() implements Value {
    }

    record Continue() implements Value {
    }

    record Interrupt() implements Value {
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

    record RuneLiteral(String character) implements Value {
    }

    record Struct(String name, java.util.Map<String, Value> parameters) implements Value {
        public Struct {
            parameters = java.util.Map.copyOf(parameters);
        }
    }

    record Enum(String name, String enumName) implements Value {
    }

    record Union(String name, Value value) implements Value {
    }

    record ArrayRef(Type.Array arrayType, List<Value> elements) implements Value {
        public ArrayRef {
            elements = java.util.List.copyOf(elements);
        }
    }

    record ArrayValue(Type.Array arrayType, MemorySegment data) implements Value {
        public ArrayValue {
            data = data.asReadOnly();
        }
    }

    record Map(Type.Map mapType, java.util.Map<Value, Value> entries) implements Value {
        public Map {
            entries = java.util.Map.copyOf(entries);
        }
    }

    record Range(Value start, Value end, Value step) implements Value {
    }

    // TODO is this really necessary?
    record JavaObject(Object object) implements Value {
    }
}
