package org.click;

import java.util.List;

public sealed interface Expression {
    record Function(List<Parameter> parameters, Type returnType, List<Statement> body) implements Expression {
    }

    record Struct(List<Parameter> parameters) implements Expression {
    }

    record Constant(Object value) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Call(String name, List<Expression> arguments) implements Expression {
    }

    record StructInit(String name, List<Expression> fields) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Operator operator, Expression right) implements Expression {
    }

    enum Operator {
        ADD, SUBTRACT,
        MULTIPLY, DIVIDE,
        MODULO,
        EQUAL, NOT_EQUAL,
        LESS_THAN, LESS_THAN_OR_EQUAL,
        GREATER_THAN, GREATER_THAN_OR_EQUAL,
        AND, OR
    }
}
