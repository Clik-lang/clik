package org.click;

import org.click.value.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executable code.
 */
public interface Program {
    Map<String, Expression> variables();

    Map<String, Struct> structs();

    Map<String, Function> functions();

    record Struct(List<TypedName> fields) {
    }

    record Function(Type.Function functionType, Set<String> captures, List<Statement> body) {
        record Parameter(TypedName typedName, boolean readOnly) {
        }
    }

    sealed interface Statement {
        record Declare(String name, DeclarationType declarationType,
                       Expression expression) implements Statement {
        }

        record Assign(String name, Expression expression) implements Statement {
        }

        record Capture(String name, Set<String> names) implements Statement {
        }

        record Run(Expression expression) implements Statement {
        }

        record Block(List<Statement> statements) implements Statement {
        }

        record Join(Statement block, Set<String> captures) implements Statement {
        }

        record Spawn(Statement block, Set<String> captures) implements Statement {
        }

        record Branch(Expression condition, Statement thenBranch,
                      @Nullable Statement elseBranch) implements Statement {
        }

        record Return(@Nullable Expression expression) implements Statement {
        }
    }

    sealed interface Expression {
        Type expressionType();

        record Constant(Value value) implements Expression {
            @Override
            public Type expressionType() {
                return switch (value) {
                    case Value.FunctionDecl functionDecl ->
                            new Type.Function(functionDecl.parameters(), functionDecl.returnType());
                    case Value.StructDecl structDecl -> new Type.Struct(structDecl.parameters());
                    case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
                    case Value.FloatLiteral floatLiteral -> floatLiteral.type();
                    case Value.BooleanLiteral ignored -> Type.BOOL;
                    case Value.StringLiteral ignored -> Type.STRING;
                    case Value.RuneLiteral ignored -> Type.RUNE;
                    case Value.Function function -> function.functionType();
                    default -> throw new UnsupportedOperationException("Unsupported value: " + value);
                };
            }
        }

        record Variable(Type type, String name) implements Expression {
            @Override
            public Type expressionType() {
                return type;
            }
        }

        record Call(Type type, String name, List<Expression> arguments) implements Expression {
            @Override
            public Type expressionType() {
                return type;
            }
        }

        record Binary(Type type, Expression left, Token.Type operator, Expression right) implements Expression {
            @Override
            public Type expressionType() {
                assert left.expressionType().equals(right.expressionType());
                return type;
            }
        }

        record Unary(Type type, Token.Type operator, Expression expression) implements Expression {
            @Override
            public Type expressionType() {
                return type;
            }
        }

        record Array(Type.Array arrayType, TypedName.Passed.Positional positional) implements Expression {
            @Override
            public Type expressionType() {
                return arrayType;
            }
        }

        record Struct(Type.Identifier structType, TypedName.Passed passed) implements Expression {
            @Override
            public Type expressionType() {
                return structType;
            }
        }

        record Map(Type.Map mapType, TypedName.Passed.Mapped mapped) implements Expression {
            @Override
            public Type expressionType() {
                return mapType;
            }
        }

        record Table(Type.Table tableType, TypedName.Passed.Positional positional) implements Expression {
            @Override
            public Type expressionType() {
                return tableType;
            }
        }
    }

    record TypedName(String name, Type type) {
        public sealed interface Passed {
            record Positional(List<Expression> expressions) implements Passed {
            }

            record Named(Map<String, Expression> entries) implements Passed {
            }

            record Mapped(Map<Expression, Expression> entries) implements Passed {
            }
        }
    }
}
