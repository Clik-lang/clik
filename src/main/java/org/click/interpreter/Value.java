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
    }

    record EnumDecl(@Nullable Type type, java.util.Map<String, Value> entries) implements Value {
    }

    record UnionDecl(java.util.Map<String, StructDecl> entries) implements Value {
    }

    // VALUES

    record Constant(Type type, Object value) implements Value {
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
