package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Context-free representation of a program.
 * <p>
 * Must be validated before being interpreted.
 */
public interface Ast {
    sealed interface Expression {
        record Constant(Value value) implements Expression {
        }

        record Enum(@Nullable Type type, Map<String, Expression> entries) implements Expression {
        }

        record Distinct(Type type, Expression constraint) implements Expression {
        }

        record Variable(String name) implements Expression {
        }

        record Contextual() implements Expression {
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

    sealed interface Statement {
        record Declare(List<String> names, DeclarationType declarationType, Expression initializer,
                       @Nullable org.click.Type explicitType) implements Statement {
        }

        record Assign(List<Statement.Assign.Target> targets, Expression expression) implements Statement {
            public record Target(String name, List<AccessPoint> accessPoints) {
            }
        }

        record Run(Expression expression) implements Statement {
        }

        record Branch(Expression condition, Statement thenBranch,
                      @Nullable Statement elseBranch) implements Statement {
        }

        record Loop(List<Statement.Loop.Declaration> declarations, Expression iterable,
                    Statement body) implements Statement {
            public record Declaration(boolean ref, String name) {
            }
        }

        record Break() implements Statement {
        }

        record Continue() implements Statement {
        }

        record Join(Statement.Block block) implements Statement {
        }

        record Spawn(Statement statement) implements Statement {
        }

        record Block(List<Statement> statements) implements Statement {
        }

        record Directive(org.click.Directive.Statement directive) implements Statement {
        }

        record Return(@Nullable Expression expression) implements Statement {
        }
    }

    record Parameter(String name, Type type) {
        public sealed interface Passed {
            record Positional(List<Expression> expressions) implements Parameter.Passed {
            }

            record Named(Map<String, Expression> entries) implements Parameter.Passed {
            }

            record Supplied(Expression expression) implements Parameter.Passed {
            }
        }
    }

    sealed interface AccessPoint {
        record Field(String component) implements AccessPoint {
        }

        record Index(Expression expression, @Nullable Type transmuteType) implements AccessPoint {
        }

        record Constraint(Expression expression) implements AccessPoint {
        }
    }
}
