package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Statement {
    record Declare(String name, DeclarationType type, Expression initializer,
                   @Nullable Type explicitType) implements Statement {
    }

    record Assign(String name, Expression expression) implements Statement {
    }

    record Call(String name, Parameter.Passed arguments) implements Statement {
    }

    record Branch(Expression condition,
                  List<Statement> thenBranch,
                  @Nullable List<Statement> elseBranch) implements Statement {
    }

    record Loop(List<Declaration> declarations, Expression iterable,
                List<Statement> body, boolean fork) implements Statement {
        public record Declaration(boolean ref, String name) {
        }
    }

    record Break() implements Statement {
    }

    record Select(Map<Statement, Block> cases) implements Statement {
    }

    record Block(List<Statement> statements) implements Statement {
    }

    record Directive(org.click.Directive.Statement directive) implements Statement {
    }

    record Return(@Nullable Expression expression) implements Statement {
    }

    enum DeclarationType {
        CONSTANT, VARIABLE, SHARED
    }
}
