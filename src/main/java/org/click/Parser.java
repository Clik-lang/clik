package org.click;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
                } else if (match(TILDE)) {
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
                statement = new Statement.Call(name, new Parameter.Passed.Positional(arguments));
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
            statement = nextBranch();
        } else if (check(FOR)) {
            statement = nextLoop();
        } else if (check(LEFT_BRACE)) {
            if (checkNext(DOT) || checkNext(LITERAL)) {
                // Inline block return
                final Expression expression = nextExpression();
                assert expression != null;
                statement = new Statement.Return(expression);
            } else {
                statement = new Statement.Block(nextBlock());
            }
        } else {
            // Implicit return
            final Expression expression = nextExpression();
            assert expression != null;
            statement = new Statement.Return(expression);
        }

        if (previous().type() != RIGHT_BRACE && previous().type() != SEMICOLON)
            consume(SEMICOLON, "Expected ';' after expression.");
        while (match(SEMICOLON)) ;
        return statement;
    }

    Expression nextExpression() {
        if (check(SEMICOLON)) return null;
        // Pratt's algorithm
        return nextExpression(0);
    }

    Expression nextExpression(int precedence) {
        Expression expression = nextPrimary();
        while (precedence < getPrecedence(peek().type())) {
            final Token operator = advance();
            final Expression right = nextExpression(getPrecedence(operator.type()));
            expression = new Expression.Binary(expression, operator, right);
        }
        return expression;
    }

    private Expression nextPrimary() {
        // Range
        if (check(LITERAL) && checkNext(RANGE)) {
            final Token.Literal startLiteral = advance().literal();
            final Expression start = new Expression.Constant(startLiteral.type(), startLiteral.value());
            consume(RANGE, "Expected '..' after start of range.");
            final Token.Literal endLiteral = advance().literal();
            final Expression end = new Expression.Constant(endLiteral.type(), endLiteral.value());
            final Expression step;
            if (match(RANGE)) {
                final Token.Literal stepLiteral = advance().literal();
                step = new Expression.Constant(stepLiteral.type(), stepLiteral.value());
            } else {
                step = new Expression.Constant(Type.I32, 1);
            }
            return new Expression.Range(start, end, step);
        }

        if (check(LEFT_PAREN)) {
            return nextFunction();
        } else if (check(STRUCT)) {
            return nextStruct();
        } else if (check(ENUM)) {
            return nextEnum();
        } else if (check(UNION)) {
            return nextUnion();
        } else if (match(LITERAL)) {
            final Token literal = previous();
            final Type type = literal.literal().type();
            final Object value = literal.literal().value();
            return new Expression.Constant(type, value);
        } else if (match(TRUE)) {
            return new Expression.Constant(Type.BOOL, true);
        } else if (match(FALSE)) {
            return new Expression.Constant(Type.BOOL, false);
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
                return new Expression.Call(identifier.input(), new Parameter.Passed.Positional(arguments));
            } else if (check(LEFT_BRACE)) {
                // Struct
                if (checkNext(RIGHT_BRACE)) {
                    // Empty struct
                    consume(LEFT_BRACE, "Expected '{' after struct name.");
                    consume(RIGHT_BRACE, "Expected '}' after struct.");
                    return new Expression.StructValue(identifier.input(), new Parameter.Passed.Positional(List.of()));
                }
                final Parameter.Passed passed = nextPassedParameters();
                return new Expression.StructValue(identifier.input(), passed);
            } else if (match(DOT)) {
                // Field
                final Expression expression = new Expression.Variable(identifier.input());
                final Token field = consume(IDENTIFIER, "Expected field name.");
                return new Expression.Field(expression, field.input());
            } else if (match(LEFT_BRACKET)) {
                // Array
                final Expression index = nextExpression();
                consume(RIGHT_BRACKET, "Expected ']' after index.");
                return new Expression.ArrayAccess(new Expression.Variable(identifier.input()), index);
            } else {
                return new Expression.Variable(identifier.input());
            }
        } else if (check(LEFT_BRACKET)) {
            // Array
            consume(LEFT_BRACKET, "Expected '[' after array.");
            consume(RIGHT_BRACKET, "Expected ']' after array.");
            final Type arrayType = nextType();
            final Parameter.Passed passed = nextPassedParameters();
            return new Expression.ArrayValue(arrayType, passed);
        } else if (check(MAP)) {
            // Map
            consume(MAP, "Expected 'map' after '['.");
            consume(LEFT_BRACKET, "Expected '[' after map.");
            final Type keyType = nextType();
            consume(RIGHT_BRACKET, "Expected ']' after key type.");
            final Type valueType = nextType();
            Map<Expression, Expression> entries = new HashMap<>();
            if (check(LEFT_BRACE)) {
                consume(LEFT_BRACE, "Expected '{' after map.");
                while (!check(RIGHT_BRACE)) {
                    final Expression key = nextExpression();
                    consume(COLON, "Expected ':' after key.");
                    final Expression value = nextExpression();
                    entries.put(key, value);
                    if (check(COMMA)) {
                        consume(COMMA, "Expected ',' after entry.");
                    }
                }
                consume(RIGHT_BRACE, "Expected '}' after map.");
            }
            return new Expression.MapValue(keyType, valueType, entries);
        } else if (check(LEFT_BRACE)) {
            // Inline block
            final Parameter.Passed passed = nextPassedParameters();
            return new Expression.InitializationBlock(passed);
        } else {
            throw error(peek(), "Expect expression.");
        }
    }

    private Parameter.Passed nextPassedParameters() {
        consume(LEFT_BRACE, "Expected '{' after struct name.");
        if (check(DOT)) {
            // Named struct
            final Map<String, Expression> fields = new HashMap<>();
            do {
                consume(DOT, "Expected '.' before field name.");
                final String name = consume(IDENTIFIER, "Expected field name.").input();
                consume(COLON, "Expected ':' after field name.");
                final Expression value = nextExpression();
                fields.put(name, value);
            } while (match(COMMA));
            consume(RIGHT_BRACE, "Expected '}' after struct fields.");
            return new Parameter.Passed.Named(fields);
        } else {
            // Positional struct
            final List<Expression> fields = new ArrayList<>();
            do {
                fields.add(nextExpression());
            } while (match(COMMA));
            consume(RIGHT_BRACE, "Expected '}' after struct fields.");
            return new Parameter.Passed.Positional(fields);
        }
    }

    private int getPrecedence(Token.Type type) {
        return switch (type) {
            case OR, AND -> 10;
            case EQUAL_EQUAL, NOT_EQUAL,
                    LESS, LESS_EQUAL,
                    GREATER, GREATER_EQUAL -> 20;
            case PLUS, MINUS -> 30;
            case STAR, SLASH -> 40;
            default -> -1;
        };
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
        final List<Statement> body = nextBlock();
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

    private Expression.Enum nextEnum() {
        consume(ENUM, "Expect 'enum'.");
        final Type type = check(IDENTIFIER) ? nextType() : null;
        consume(LEFT_BRACE, "Expect '{'.");
        final Map<String, Expression> entries = new HashMap<>();
        int index = 0;
        if (!check(RIGHT_BRACE)) {
            do {
                if (entries.size() >= 255) {
                    throw error(peek(), "Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                final Expression value;
                if (type != null) {
                    consume(COLON, "Expect ':' after field name.");
                    consume(COLON, "Expect '::' after field name.");
                    value = nextExpression();
                } else {
                    value = new Expression.Constant(Type.I32, index++);
                }
                entries.put(identifier.input(), value);
            } while (match(COMMA) && !check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Expression.Enum(type, entries);
    }

    private Expression.Union nextUnion() {
        consume(UNION, "Expect 'union'.");
        consume(LEFT_BRACE, "Expect '{'.");
        Map<String, Expression.Struct> entries = new HashMap<>();
        if (!check(RIGHT_BRACE)) {
            do {
                if (entries.size() >= 255) {
                    throw error(peek(), "Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                Expression.Struct struct = null;
                if (match(COLON)) {
                    // Inline struct
                    consume(COLON, "Expect ':' after field name.");
                    struct = nextStruct();
                }
                entries.put(identifier.input(), struct);
            } while (match(COMMA) && !check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Expression.Union(entries);
    }

    Statement.Branch nextBranch() {
        consume(IF, "Expect 'if'.");
        final Expression condition = nextExpression();
        final List<Statement> thenBranch = nextBlock();
        final List<Statement> elseBranch = match(ELSE) ? nextBlock() : null;
        return new Statement.Branch(condition, thenBranch, elseBranch);
    }

    Statement.Loop nextLoop() {
        consume(FOR, "Expect 'for'.");
        // Infinite loop
        if (check(LEFT_BRACE) || check(ARROW)) {
            return new Statement.Loop(List.of(), null, nextBlock());
        }
        // Conditional loop
        if (check(IDENTIFIER) && checkNext(COLON)) {
            final Token identifier = consume(IDENTIFIER, "Expect identifier.");
            consume(COLON, "Expect ':'.");
            // For each
            final List<String> declarations = List.of(identifier.input());
            final Expression iterable = nextExpression();
            final List<Statement> body = nextBlock();
            return new Statement.Loop(declarations, iterable, body);
        } else {
            // For
            final Expression iterable = nextExpression();
            final List<Statement> body = nextBlock();
            return new Statement.Loop(List.of(), iterable, body);
        }
    }

    List<Statement> nextBlock() {
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
