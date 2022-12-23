package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Context-free representation of a program.
 */
public interface Ast {
    sealed interface Expression {
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

    sealed interface Statement {
        record Declare(List<String> names, Statement.Declare.Type declarationType, Expression initializer,
                       @Nullable org.click.Type explicitType) implements Statement {
            public enum Type {
                CONSTANT, VARIABLE, SHARED
            }
        }

        record Assign(List<Statement.Assign.Target> targets, Expression expression) implements Statement {
            public record Target(String name, List<AccessPoint> accessPoints) {
            }
        }

        record Output(String name, Expression expression) implements Statement {
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
            Expression find(List<Parameter> parameters, Parameter parameter);

            List<Expression> expressions();

            record Positional(List<Expression> expressions) implements Parameter.Passed {
                @Override
                public Expression find(List<Parameter> parameters, Parameter parameter) {
                    final int index = parameters.indexOf(parameter);
                    final Expression expression = expressions.get(index);
                    if (expression == null)
                        throw new RuntimeException("Field not found: " + parameter);
                    return expression;
                }
            }

            record Named(Map<String, Expression> entries) implements Parameter.Passed {
                @Override
                public Expression find(List<Parameter> parameters, Parameter parameter) {
                    final Expression expression = entries.get(parameter.name());
                    if (expression == null)
                        throw new RuntimeException("Field not found: " + parameter);
                    return expression;
                }

                @Override
                public List<Expression> expressions() {
                    return List.copyOf(entries.values());
                }
            }

            record Mapped(Map<Expression, Expression> entries) implements Parameter.Passed {
                @Override
                public Expression find(List<Parameter> parameters, Parameter parameter) {
                    throw new RuntimeException("Mapped parameters are not supported");
                }

                @Override
                public List<Expression> expressions() {
                    throw new RuntimeException("Mapped parameters are not supported");
                }
            }
        }
    }

    sealed interface AccessPoint {
        record Field(String component) implements AccessPoint {
        }

        record Index(Expression expression, @Nullable Type transmuteType) implements AccessPoint {
        }
    }
}
