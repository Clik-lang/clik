package org.click.value;

import org.click.Ast;
import org.click.BinStandard;
import org.click.Type;
import org.click.interpreter.Executor;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.click.Ast.Parameter;
import static org.click.Ast.Statement;

public sealed interface Value {
    // DECLARATIONS

    record ExternFunctionDecl(List<Parameter> parameters, Type returnType) implements Value {
        public ExternFunctionDecl {
            parameters = List.copyOf(parameters);
        }
    }

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

    record DistinctDecl(Type type, Ast.Expression constraint) implements Value {
    }

    // VALUES

    record Break() implements Value {
    }

    record Continue() implements Value {
    }

    record Interrupt() implements Value {
    }

    record NumberLiteral(BigDecimal value) implements Value {
        public NumberLiteral(Number value) {
            this(new BigDecimal(value.toString()));
        }

        public NumberLiteral(String value) {
            this(new BigDecimal(value));
        }
    }

    record BooleanLiteral(boolean value) implements Value {
    }

    record Binary(BinStandard standard, MemorySegment segment) implements Value {
        public Binary {
            segment = segment.asReadOnly();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Binary binary)) return false;
            return Objects.equals(standard, binary.standard) && segment.mismatch(binary.segment) == -1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(standard, segment);
        }
    }

    record Struct(String name, java.util.Map<String, Value> parameters) implements Value {
        public Struct {
            parameters = java.util.Map.copyOf(parameters);
        }
    }

    record Enum(String name, String entry) implements Value {
    }

    record Union(String name, Value value) implements Value {
    }

    record Array(Type.Array arrayType, List<Value> elements) implements Value {
        public Array {
            elements = java.util.List.copyOf(elements);
        }
    }
}
