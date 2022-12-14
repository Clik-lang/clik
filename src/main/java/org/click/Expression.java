package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Expression {
    record Function(List<Parameter> parameters, Type returnType, List<Statement> body) implements Expression {
    }

    record Struct(List<Parameter> parameters) implements Expression {
    }

    record Enum(@Nullable Type type, Map<String, Expression> entries) implements Expression {
    }

    record Union(Map<String, @Nullable Struct> entries) implements Expression {
    }

    record Constant(Type type, Object value) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Reference(String name) implements Expression {
    }

    record Group(Expression expression) implements Expression {
    }

    record Field(Expression object, String name) implements Expression {
    }

    record ArrayAccess(Expression array, Expression index) implements Expression {
    }

    record Call(String name, Parameter.Passed arguments) implements Expression {
    }

    record StructValue(String name, Parameter.Passed fields) implements Expression {
    }

    record ArrayValue(Type type, Parameter.Passed parameters) implements Expression {
    }

    record MapValue(Type keyType, Type valueType,
                    Map<Expression, Expression> entries) implements Expression {
    }

    record InitializationBlock(Parameter.Passed parameters) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Token operator, Expression right) implements Expression {
    }
}
