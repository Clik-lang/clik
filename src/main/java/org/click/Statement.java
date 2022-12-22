package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public sealed interface Statement {
    record Declare(List<String> names, DeclarationType type, Expression initializer,
                   @Nullable Type explicitType) implements Statement {
    }

    record AssignTarget(String name, List<AccessPoint> accessPoints) {
    }

    record Assign(List<AssignTarget> assignTargets, Expression expression) implements Statement {
    }

    record Run(Expression expression) implements Statement {
    }

    record Branch(Expression condition, Statement thenBranch,
                  @Nullable Statement elseBranch) implements Statement {
    }

    record Loop(List<Declaration> declarations, Expression iterable,
                Statement body) implements Statement {
        public record Declaration(boolean ref, String name) {
        }
    }

    record Break() implements Statement {
    }

    record Continue() implements Statement {
    }

    record Join(Block block) implements Statement {
    }

    record Spawn(Statement statement) implements Statement {
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
