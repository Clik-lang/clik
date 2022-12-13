package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public sealed interface Statement {
    record Declare(String name, Expression initializer, @Nullable Type explicitType) implements Statement {
    }

    record Assign(String name, Expression expression) implements Statement {
    }

    record Call(String name, List<Expression> arguments) implements Statement {
    }

    record Return(@Nullable Expression expression) implements Statement {
    }
}
