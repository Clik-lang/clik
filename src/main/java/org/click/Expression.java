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

    record Constant(Object value) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Field(Expression object, String name) implements Expression {
    }

    record Call(String name, List<Expression> arguments) implements Expression {
    }

    record StructInit(String name, List<Expression> fields) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Token operator, Expression right) implements Expression {
    }
}
