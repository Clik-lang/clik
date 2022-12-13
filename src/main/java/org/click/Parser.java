package org.click;

import java.util.ArrayList;
import java.util.List;

import static org.click.Token.Type.*;

public final class Parser {
    private final List<Token> tokens;
    private int index;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Statement nextStatement() {
        final Statement statement;

        if (match(IDENTIFIER)) {
            final Token identifier = previous();
            final String name = identifier.input();
            if (match(EQUAL)) {
                // Assign
                final Expression expression = nextExpression();
                statement = new Statement.Assign(name, expression);
            } else if (match(COLON)) {
                // Declare
                Type explicitType = null;
                if (check(IDENTIFIER)) {
                    explicitType = nextType();
                }

                Statement.DeclarationType declarationType;
                if (match(COLON)) {
                    declarationType = Statement.DeclarationType.CONSTANT;
                } else if (match(EQUAL)) {
                    declarationType = Statement.DeclarationType.VARIABLE;
                } else if (match(TIDE)) {
                    declarationType = Statement.DeclarationType.SHARED;
                } else {
                    throw error(peek(), "Expected declaration type");
                }

                final Expression initializer = nextExpression();
                statement = new Statement.Declare(name, declarationType, initializer, explicitType);
            } else if (match(LEFT_PAREN)) {
                // Call
                final List<Expression> arguments = new ArrayList<>();
                if (!check(RIGHT_PAREN)) {
                    do {
                        arguments.add(nextExpression());
                    } while (match(COMMA));
                }
                consume(RIGHT_PAREN, "Expected ')' after arguments.");
                statement = new Statement.Call(name, arguments);
            } else {
                // Implicit return
                index--;
                final Expression expression = nextExpression();
                assert expression != null;
                statement = new Statement.Return(expression);
            }
        } else if (check(RETURN)) {
            // Explicit return
            consume(RETURN, "Expected 'return'.");
            statement = new Statement.Return(nextExpression());
        } else if (check(IF)) {
            statement = readBranch();
        } else if (check(FOR)) {
            statement = readLoop();
        } else if (check(LEFT_BRACE)) {
            statement = new Statement.Block(readBlock());
        } else {
            // Implicit return
            final Expression expression = nextExpression();
            assert expression != null;
            statement = new Statement.Return(expression);
        }

        if (previous().type() != RIGHT_BRACE && previous().type() != SEMICOLON)
            consume(SEMICOLON, "Expected ';' after expression.");
        return statement;
    }

    Expression nextExpression() {
        if (check(SEMICOLON)) return null;

        // Range
        if (check(LITERAL) && checkNext(RANGE)) {
            final Expression start = new Expression.Constant(advance().value());
            consume(RANGE, "Expected '..' after start of range.");
            final Expression end = new Expression.Constant(advance().value());
            final Expression step;
            if (match(RANGE)) {
                step = new Expression.Constant(advance().value());
            } else {
                step = new Expression.Constant(1);
            }
            return new Expression.Range(start, end, step);
        }

        final Expression expression;
        if (check(LEFT_PAREN)) {
            expression = nextFunction();
        } else if (check(STRUCT)) {
            expression = nextStruct();
        } else if (match(LITERAL)) {
            final Token literal = previous();
            final Object value = literal.value();
            if (value instanceof String) {
                expression = new Expression.Constant(value);
            } else if (value instanceof Number) {
                // Parse math
                expression = new Expression.Constant(value);
            } else {
                throw error(literal, "Unexpected literal type: " + value);
            }
        } else if (match(TRUE)) {
            expression = new Expression.Constant(true);
        } else if (match(FALSE)) {
            expression = new Expression.Constant(false);
        } else if (match(IDENTIFIER)) {
            // Variable
            final Token identifier = previous();
            if (match(LEFT_PAREN)) {
                // Call
                final List<Expression> arguments = new ArrayList<>();
                if (!check(RIGHT_PAREN)) {
                    do {
                        arguments.add(nextExpression());
                    } while (match(COMMA));
                }
                consume(RIGHT_PAREN, "Expected ')' after arguments.");
                expression = new Expression.Call(identifier.input(), arguments);
            } else if (match(LEFT_BRACE)) {
                // Struct
                final List<Expression> fields = new ArrayList<>();
                if (!check(RIGHT_BRACE)) {
                    do {
                        fields.add(nextExpression());
                    } while (match(COMMA));
                }
                consume(RIGHT_BRACE, "Expected '}' after fields.");
                expression = new Expression.StructInit(identifier.input(), fields);
            } else {
                expression = new Expression.Variable(identifier.input());
            }
        } else {
            throw error(peek(), "Expect expression.");
        }
        return expression;
    }

    Type nextType() {
        final Token identifier = consume(IDENTIFIER, "Expected type name.");
        return Type.of(identifier.input());
    }

    Expression.Function nextFunction() {
        consume(LEFT_PAREN, "Expect '('.");
        final List<Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    throw error(peek(), "Cannot have more than 255 parameters.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect parameter name.");
                consume(COLON, "Expect ':' after parameter name.");
                final Type type = nextType();
                final Parameter parameter = new Parameter(identifier.input(), type);
                parameters.add(parameter);
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')'.");
        Type returnType = Type.VOID;
        if (check(IDENTIFIER)) {
            returnType = nextType();
        }
        final List<Statement> body = readBlock();
        return new Expression.Function(parameters, returnType, body);
    }

    private Expression.Struct nextStruct() {
        consume(STRUCT, "Expect 'struct'.");
        consume(LEFT_BRACE, "Expect '{'.");
        final List<Parameter> fields = new ArrayList<>();
        if (!check(RIGHT_BRACE)) {
            do {
                if (fields.size() >= 255) {
                    throw error(peek(), "Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                consume(COLON, "Expect ':' after field name.");
                final Type type = nextType();
                final Parameter parameter = new Parameter(identifier.input(), type);
                fields.add(parameter);
            } while (match(COMMA));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Expression.Struct(fields);
    }

    Statement.Branch readBranch() {
        consume(IF, "Expect 'if'.");
        final Expression condition = nextExpression();
        final List<Statement> thenBranch = readBlock();
        final List<Statement> elseBranch = match(ELSE) ? readBlock() : null;
        return new Statement.Branch(condition, thenBranch, elseBranch);
    }

    Statement.Loop readLoop() {
        consume(FOR, "Expect 'for'.");
        // Infinite loop
        if (check(LEFT_BRACE) || check(ARROW)) {
            return new Statement.Loop(List.of(), null, readBlock());
        }
        // Conditional loop
        if (check(IDENTIFIER) && checkNext(COLON)) {
            final Token identifier = consume(IDENTIFIER, "Expect identifier.");
            consume(COLON, "Expect ':'.");
            // For each
            final List<String> declarations = List.of(identifier.input());
            final Expression iterable = nextExpression();
            final List<Statement> body = readBlock();
            return new Statement.Loop(declarations, iterable, body);
        } else {
            // For
            final Expression iterable = nextExpression();
            final List<Statement> body = readBlock();
            return new Statement.Loop(List.of(), iterable, body);
        }
    }

    List<Statement> readBlock() {
        if (match(LEFT_BRACE)) {
            final List<Statement> statements = new ArrayList<>();
            while (!check(RIGHT_BRACE) && !isAtEnd()) {
                statements.add(nextStatement());
            }
            consume(RIGHT_BRACE, "Expect '}'.");
            return statements;
        } else if (match(ARROW)) {
            final Statement statement = nextStatement();
            return List.of(statement);
        }
        throw error(peek(), "Expect '{' or '->'.");
    }

    Token consume(Token.Type type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    Token advance() {
        if (!isAtEnd()) index++;
        return previous();
    }

    Token previous() {
        return tokens.get(index - 1);
    }

    boolean match(Token.Type... types) {
        for (var type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    boolean check(Token.Type type) {
        if (isAtEnd()) return false;
        return peek().type() == type;
    }

    boolean checkNext(Token.Type type) {
        if (isAtEnd()) return false;
        return tokens.get(index + 1).type() == type;
    }

    boolean isAtEnd() {
        return peek().type() == Token.Type.EOF;
    }

    Token peek() {
        return tokens.get(index);
    }

    private RuntimeException error(Token peek, String message) {
        return new RuntimeException(message + ": got token " + peek + " previous: " + previous());
    }

    public List<Statement> parse() {
        List<Statement> statements = new ArrayList<>();
        Statement statement;
        while (peek().type() != EOF && (statement = nextStatement()) != null) {
            statements.add(statement);
        }
        return List.copyOf(statements);
    }
}
