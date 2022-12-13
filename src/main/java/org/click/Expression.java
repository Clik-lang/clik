package org.click;

import java.util.List;

public sealed interface Expression {
    record Function(List<String> parameters, Type returnType, List<Statement> body) implements Expression {
    }

    record Struct(List<String> fields) implements Expression {
    }

    record Constant(Object value) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Call(String name, List<Expression> arguments) implements Expression {
    }

    record StructAlloc(String name, List<Expression> fields) implements Expression {
    }
}
