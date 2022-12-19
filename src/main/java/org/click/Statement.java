package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface Statement {
    record Declare(List<String> names, DeclarationType type, Expression initializer,
                   @Nullable Type explicitType) implements Statement {
    }

    record AssignTarget(String name, AccessPoint accessPoint) {
    }

    record Assign(List<AssignTarget> assignTargets, Expression expression) implements Statement {
    }

    record Call(String name, Parameter.Passed arguments) implements Statement {
    }

    record Branch(Expression condition, Statement thenBranch,
                  @Nullable Statement elseBranch) implements Statement {
    }

    record Loop(List<Declaration> declarations, Expression iterable,
                Statement body, boolean fork) implements Statement {
        public record Declaration(boolean ref, String name) {
        }
    }

    record Break() implements Statement {
    }

    record Continue() implements Statement {
    }

    record Select(Map<Statement, Block> cases) implements Statement {
    }

    record Join(List<Block> blocks) implements Statement {
    }

    record Spawn(Statement statement) implements Statement {
    }

    record Defer(Statement statement) implements Statement {
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
