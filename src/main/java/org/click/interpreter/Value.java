package org.click.interpreter;

import org.click.Parameter;
import org.click.Statement;
import org.click.Type;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public sealed interface Value {
    // DECLARATIONS

    record FunctionDecl(List<Parameter> parameters, Type returnType, List<Statement> body) implements Value {
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

    record Constant(Type type, Object value) implements Value {
        public Constant {
            if (!type.primitive())
                throw new IllegalArgumentException("Constant type must be primitive");
        }
    }

    record Reference(String name) implements Value {
    }

    record Struct(String name, java.util.Map<String, Value> parameters) implements Value {
    }

    record Union(String name, Value value) implements Value {
    }

    record Array(Type type, List<Value> values) implements Value {
    }

    record Map(Type keyType, Type valueType, java.util.Map<Value, Value> entries) implements Value {
    }

    record Range(Value start, Value end, Value step) implements Value {
    }
}
