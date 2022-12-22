package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Expression {
    record Constant(Value value) implements Expression {
    }

    record Enum(@Nullable Type type, Map<String, Expression> entries) implements Expression {
    }

    record Variable(String name) implements Expression {
    }

    record Access(Expression object, List<AccessPoint> accessPoints) implements Expression {
    }

    record VariableAwait(String name) implements Expression {
    }

    record Call(String name, Parameter.Passed arguments) implements Expression {
    }

    record Select(List<Statement.Block> blocks) implements Expression {
    }

    record Initialization(@Nullable Type type, Parameter.Passed parameters) implements Expression {
    }

    record Range(Expression start, Expression end, Expression step) implements Expression {
    }

    record Binary(Expression left, Token.Type operator, Expression right) implements Expression {
    }

    record Unary(Token.Type operator, Expression expression) implements Expression {
    }
}
