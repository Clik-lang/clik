package org.click;

import java.util.List;

public sealed interface Expression {
    record Function(List<Token> parameters, List<Statement> body) implements Expression {
    }

    record Constant(Object value) implements Expression {
    }
}
