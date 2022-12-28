package org.click.value;

import org.click.Type;
import org.click.interpreter.Executor;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.click.Ast.Parameter;
import static org.click.Ast.Statement;

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

    record UnionDecl(java.util.Map<String, @Nullable StructDecl> entries) implements Value {
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
                throw new IllegalArgumentException("Invalid integer type: " + type);
        }
    }

    record FloatLiteral(Type type, double value) implements Value {
        public FloatLiteral {
            if (type != Type.F32 && type != Type.F64 && type != Type.FLOAT)
                throw new IllegalArgumentException("Invalid float type: " + type);
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

    record Table(Type.Table tableType, List<Value> values) implements Value {
        public Table {
            values = java.util.List.copyOf(values);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Table table)) return false;
            if (!tableType.equals(table.tableType)) return false;
            // Unordered comparison
            java.util.Map<Value, Integer> thisOccurrences = new HashMap<>();
            java.util.Map<Value, Integer> otherOccurrences = new HashMap<>();
            for (Value value : values) thisOccurrences.merge(value, 1, Integer::sum);
            for (Value value : table.values) otherOccurrences.merge(value, 1, Integer::sum);
            return thisOccurrences.equals(otherOccurrences);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tableType, values);
        }
    }

    record Range(Value start, Value end, Value step) implements Value {
    }

    // TODO is this really necessary?
    record JavaObject(Object object) implements Value {
    }
}
