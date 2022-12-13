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
                if (match(IDENTIFIER)) {
                    final Token type = peek();
                    explicitType = new Type(type.input(), false);
                }

                if (match(COLON)) {
                    // Constant
                } else if (match(EQUAL)) {
                    // Variable
                } else if (match(TIDE)) {
                    // Shared
                }

                final Expression initializer = nextExpression();
                statement = new Statement.Declare(name, initializer, explicitType);
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
                throw error(identifier, "Expected ':' or '=' after identifier.");
            }
        } else {
            throw error(peek(), "Expect statement.");
        }

        if (previous().type() != RIGHT_BRACE)
            consume(SEMICOLON, "Expected ';' after expression.");
        return statement;
    }

    Expression nextExpression() {
        final Expression expression;
        if (check(LEFT_PAREN)) {
            expression = nextFunction();
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
        } else if (match(IDENTIFIER)) {
            // Variable
            final Token identifier = previous();
            expression = new Expression.Variable(identifier.input());
        } else {
            throw error(peek(), "Expect expression.");
        }
        return expression;
    }

    Expression.Function nextFunction() {
        consume(LEFT_PAREN, "Expect '('.");
        consume(RIGHT_PAREN, "Expect ')'.");
        final List<Statement> body = readBlock();
        return new Expression.Function(List.of(), body);
    }

    List<Statement> readBlock() {
        consume(LEFT_BRACE, "Expect '{'.");
        final List<Statement> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(nextStatement());
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return statements;
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

    boolean isAtEnd() {
        return peek().type() == Token.Type.EOF;
    }

    Token peek() {
        return tokens.get(index);
    }

    private RuntimeException error(Token peek, String message) {
        return new RuntimeException(message + ": got token " + peek);
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
