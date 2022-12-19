package org.click;

import java.util.*;

import static org.click.Token.Type.*;

public final class Parser {
    private final List<Token> tokens;
    private int index;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    Statement nextStatement() {
        final int startIndex = index;
        final Statement statement;

        if (check(IDENTIFIER)) {
            List<Statement.AssignTarget> assignTargets = new ArrayList<>();
            // Read all identifiers separated by commas
            do {
                final Token identifier = consume(IDENTIFIER, "Expected identifier");
                final String name = identifier.input();
                final AccessPoint accessPoint = nextAccessPoint();
                assignTargets.add(new Statement.AssignTarget(name, accessPoint));
            } while (match(COMMA));
            final List<String> names = assignTargets.stream().map(Statement.AssignTarget::name).toList();
            if (match(EQUAL)) {
                // Assign
                final Expression expression = nextExpression();
                statement = new Statement.Assign(assignTargets, expression);
            } else if (match(COLON)) {
                // Declare
                final Type explicitType = nextType();
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
                statement = new Statement.Declare(names, declarationType, initializer, explicitType);
            } else if (match(LEFT_PAREN) && names.size() == 1) {
                // Call
                final String name = names.get(0);
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
                index = startIndex;
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
        } else if (check(FOR) || check(FORK)) {
            statement = nextLoop();
        } else if (match(BREAK)) {
            statement = new Statement.Break();
        } else if (match(CONTINUE)) {
            statement = new Statement.Continue();
        } else if (check(SELECT)) {
            statement = nextSelect();
        } else if (check(JOIN)) {
            statement = nextJoin();
        } else if (check(SPAWN)) {
            statement = nextSpawn();
        } else if (match(DEFER)) {
            final Statement deferred = nextStatement();
            statement = new Statement.Defer(deferred);
        } else if (check(LEFT_BRACE)) {
            if (checkTrailing(SEMICOLON, RETURN, IF, FOR, FORK, BREAK, CONTINUE, SELECT, SPAWN, DEFER, HASH)) {
                statement = new Statement.Block(nextBlock());
            } else {
                // Inline block return
                final Expression expression = nextExpression();
                assert expression != null;
                statement = new Statement.Return(expression);
            }
        } else if (check(ARROW)) {
            statement = new Statement.Block(nextBlock());
        } else if (check(HASH)) {
            // Directive
            final Directive.Statement directive = nextDirectiveStatement();
            statement = new Statement.Directive(directive);
        } else {
            // Implicit return
            final Expression expression = nextExpression();
            assert expression != null;
            statement = new Statement.Return(expression);
        }

        if (previous().type() != RIGHT_BRACE && previous().type() != SEMICOLON)
            consume(SEMICOLON, "Expected ';' after expression. Got: " + statement);
        while (match(SEMICOLON)) ;
        return statement;
    }

    boolean checkTrailing(Token.Type... types) {
        final int start = index;
        if (!check(LEFT_BRACE)) return false;
        if (checkNext(RIGHT_BRACE)) return true;
        // Verify if there is at least one semicolon or statement keyword between the braces
        int braceLevel = 0;
        while (!checkNext(RIGHT_BRACE) || braceLevel > 0) {
            if (checkNext(LEFT_BRACE)) braceLevel++;
            if (checkNext(RIGHT_BRACE)) {
                braceLevel--;
            }
            for (Token.Type type : types) {
                if (checkNext(type)) {
                    index = start;
                    return true;
                }
            }
            advance();
        }
        index = start;
        return false;
    }

    AccessPoint nextAccessPoint() {
        List<AccessPoint.Access> accesses = new ArrayList<>();
        while (check(DOT) || check(LEFT_BRACKET)) {
            if (match(DOT)) {
                // Field access
                accesses.add(new AccessPoint.Field(advance().input()));
            } else if (match(LEFT_BRACKET)) {
                // Index access
                final Expression expression = nextExpression();
                consume(RIGHT_BRACKET, "Expected ']' after array index.");
                accesses.add(new AccessPoint.Index(expression));
            }
        }
        return new AccessPoint(accesses);
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
        if (check(INTEGER_LITERAL) && checkNext(RANGE)) {
            final Token.Literal startLiteral = advance().literal();
            final Expression start = new Expression.IntegerLiteral(startLiteral.type(), (Long) startLiteral.value());
            consume(RANGE, "Expected '..' after start of range.");
            final Token.Literal endLiteral = advance().literal();
            final Expression end = new Expression.IntegerLiteral(endLiteral.type(), (Long) endLiteral.value());
            final Expression step;
            if (match(RANGE)) {
                final Token.Literal stepLiteral = advance().literal();
                step = new Expression.IntegerLiteral(stepLiteral.type(), (Long) stepLiteral.value());
            } else {
                step = new Expression.IntegerLiteral(Type.INT, 1);
            }
            return new Expression.Range(start, end, step);
        }

        if (check(LEFT_PAREN)) {
            if (checkNext(RIGHT_PAREN) || checkNext(IDENTIFIER) && checkNextNext(COLON)) {
                return nextFunction();
            } else {
                consume(LEFT_PAREN, "Expected '(' after expression.");
                final Expression expression = nextExpression();
                consume(RIGHT_PAREN, "Expected ')' after expression.");
                return new Expression.Group(expression);
            }
        } else if (check(STRUCT)) {
            return nextStruct();
        } else if (check(ENUM)) {
            return nextEnum();
        } else if (check(UNION)) {
            return nextUnion();
        } else if (match(STRING_LITERAL)) {
            final Token literal = previous();
            final Object value = literal.literal().value();
            return new Expression.StringLiteral((String) value);
        } else if (match(INTEGER_LITERAL)) {
            final var literal = previous().literal();
            final Object value = literal.value();
            return new Expression.IntegerLiteral(literal.type(), (Long) value);
        } else if (match(FLOAT_LITERAL)) {
            final var literal = previous().literal();
            final Object value = literal.value();
            return new Expression.FloatLiteral(literal.type(), (Double) value);
        } else if (match(TRUE)) {
            return new Expression.BooleanLiteral(true);
        } else if (match(FALSE)) {
            return new Expression.BooleanLiteral(false);
        } else if (check(IDENTIFIER) && checkNext(DOT)) {
            // Field
            final Token identifier = advance();
            final Expression expression = new Expression.Variable(identifier.input());
            final AccessPoint accessPoint = nextAccessPoint();
            return new Expression.Field(expression, accessPoint);
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
                final Class<? extends Parameter.Passed> paramType = check(LEFT_BRACE) && checkNext(DOT) ?
                        Parameter.Passed.Named.class : Parameter.Passed.Positional.class;
                final Parameter.Passed passed = nextPassedParameters(paramType);
                return new Expression.StructValue(identifier.input(), passed);
            } else if (match(LEFT_BRACKET)) {
                // Array
                final Expression index = nextExpression();
                consume(RIGHT_BRACKET, "Expected ']' after index.");
                return new Expression.ArrayAccess(new Expression.Variable(identifier.input()), index);
            } else {
                return new Expression.Variable(identifier.input());
            }
        } else if (match(DOLLAR)) {
            // Variable await
            final Token identifier = consume(IDENTIFIER, "Expected variable name.");
            return new Expression.VariableAwait(identifier.input());
        } else if (check(LEFT_BRACKET)) {
            final Type.Array type = (Type.Array) nextType();
            assert type != null;
            List<Expression> expressions = null;
            if (check(LEFT_BRACE)) {
                final Parameter.Passed.Positional passed = nextPassedParameters(Parameter.Passed.Positional.class);
                expressions = passed.expressions();
                assert type.length() == passed.expressions().size() : "Array length does not match passed parameters.";
            }
            return new Expression.ArrayValue(type, expressions);
        } else if (check(MAP)) {
            final Type.Map type = (Type.Map) nextType();
            final Parameter.Passed.Mapped mapped = nextPassedParameters(Parameter.Passed.Mapped.class);
            return new Expression.MapValue(type, mapped);
        } else if (check(LEFT_BRACE)) {
            // Inline block
            final Parameter.Passed passed = nextPassedParameters(null);
            return new Expression.InitializationBlock(passed);
        } else {
            throw error(peek(), "Expect expression.");
        }
    }

    private <T extends Parameter.Passed> T nextPassedParameters(Class<T> preferred) {
        final boolean colon = checkTrailing(COLON);
        consume(LEFT_BRACE, "Expected '{' after struct name.");
        if (preferred == null) {
            if (!check(DOT) && colon) {
                preferred = (Class<T>) Parameter.Passed.Mapped.class;
            } else if (check(DOT)) {
                preferred = (Class<T>) Parameter.Passed.Named.class;
            } else {
                preferred = (Class<T>) Parameter.Passed.Positional.class;
            }
        }
        if (preferred == Parameter.Passed.Mapped.class) {
            // Mapped
            Map<Expression, Expression> entries = new HashMap<>();
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
            return (T) new Parameter.Passed.Mapped(entries);
        } else if (preferred == Parameter.Passed.Named.class) {
            // Named struct
            final Map<String, Expression> fields = new HashMap<>();
            do {
                consume(DOT, "Expected '.' before field name.");
                final String name = consume(IDENTIFIER, "Expected field name.").input();
                consume(COLON, "Expected ':' after field name.");
                final Expression value = nextExpression();
                fields.put(name, value);
            } while (match(COMMA) && !check(RIGHT_BRACE));
            consume(RIGHT_BRACE, "Expected '}' after struct fields.");
            return (T) new Parameter.Passed.Named(fields);
        } else {
            assert preferred == Parameter.Passed.Positional.class : "Expected positional parameters.";
            // Positional struct
            final List<Expression> fields = new ArrayList<>();
            do {
                fields.add(nextExpression());
            } while (match(COMMA) && !check(RIGHT_BRACE));
            consume(RIGHT_BRACE, "Expected '}' after struct fields.");
            return (T) new Parameter.Passed.Positional(fields);
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
        if (!(check(IDENTIFIER) || check(LEFT_BRACKET) ||
                check(MAP) || check(LEFT_PAREN))) return null;
        if (match(LEFT_BRACKET)) {
            // Array
            long length = -1;
            if (check(INTEGER_LITERAL)) {
                // TODO: make mandatory, currently not possible because of function param
                final Token lengthLiteral = consume(INTEGER_LITERAL, "Expected integer literal for array length.");
                length = ((Long) lengthLiteral.literal().value()).longValue();
            }
            consume(RIGHT_BRACKET, "Expected ']'.");
            final Type arrayType = nextType();
            return new Type.Array(arrayType, length);
        }
        if (match(MAP)) {
            // Map
            consume(LEFT_BRACKET, "Expected '[' after map.");
            final Type keyType = nextType();
            consume(RIGHT_BRACKET, "Expected ']' after key type.");
            final Type valueType = nextType();
            return new Type.Map(keyType, valueType);
        }
        if (match(LEFT_PAREN)) {
            // Function
            final List<Parameter> parameterTypes = new ArrayList<>();
            if (!check(RIGHT_PAREN)) {
                do {
                    final Token identifier = consume(IDENTIFIER, "Expected parameter name.");
                    consume(COLON, "Expected ':' after parameter name.");
                    final Type type = nextType();
                    parameterTypes.add(new Parameter(identifier.input(), type));
                } while (match(COMMA));
            }
            consume(RIGHT_PAREN, "Expected ')' after parameters.");
            Type returnType = Type.VOID;
            if (!check(SEMICOLON) && !check(COMMA)) {
                returnType = nextType();
            }
            return new Type.Function(parameterTypes, returnType);
        }
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
        final Type returnType = Objects.requireNonNullElse(nextType(), Type.VOID);
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
            } while (match(COMMA) && !check(RIGHT_BRACE));
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
                    value = new Expression.IntegerLiteral(Type.INT, index++);
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
        final Statement thenBranch = nextStatement();
        final Statement elseBranch = match(ELSE) ? nextStatement() : null;
        return new Statement.Branch(condition, thenBranch, elseBranch);
    }

    Statement.Loop nextLoop() {
        final boolean fork = match(FORK);
        if (!fork) consume(FOR, "Expect 'for' or 'fork'.");
        // Infinite loop
        if (check(LEFT_BRACE) || check(ARROW)) {
            return new Statement.Loop(null, null, nextStatement(), fork);
        }
        List<Statement.Loop.Declaration> declarations = new ArrayList<>();
        if (check(LEFT_PAREN) || check(DOT) || check(IDENTIFIER) && (checkNext(COLON) || checkNext(COMMA))) {
            final boolean paren = match(LEFT_PAREN);
            do {
                if (declarations.size() >= 255) {
                    throw error(peek(), "Cannot have more than 255 declarations.");
                }
                final boolean ref = match(DOT);
                final Token identifier = consume(IDENTIFIER, "Expect declaration name.");
                declarations.add(new Statement.Loop.Declaration(ref, identifier.input()));
            } while (match(COMMA));
            if (paren) consume(RIGHT_PAREN, "Expect ')'.");
        }
        if (!declarations.isEmpty()) {
            consume(COLON, "Expect ':' after declaration name.");
        }
        final Expression iterable = nextExpression();
        final Statement body = nextStatement();
        return new Statement.Loop(declarations, iterable, body, fork);
    }

    Statement.Select nextSelect() {
        consume(SELECT, "Expect 'select'.");
        consume(LEFT_BRACE, "Expect '{'.");
        List<Statement.Block> blocks = new ArrayList<>();
        if (!check(RIGHT_BRACE)) {
            do {
                final List<Statement> statements = nextBlock();
                final Statement.Block block = new Statement.Block(statements);
                blocks.add(block);
            } while (!check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Statement.Select(blocks);
    }

    Statement.Join nextJoin() {
        consume(JOIN, "Expect 'join'.");
        consume(LEFT_BRACE, "Expect '{'.");
        List<Statement.Block> blocks = new ArrayList<>();
        if (!check(RIGHT_BRACE)) {
            do {
                final List<Statement> statements = nextBlock();
                final Statement.Block block = new Statement.Block(statements);
                blocks.add(block);
            } while (!check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Statement.Join(blocks);
    }

    Statement.Spawn nextSpawn() {
        consume(SPAWN, "Expect 'spawn'.");
        final Statement statement = nextStatement();
        return new Statement.Spawn(statement);
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

    Directive.Statement nextDirectiveStatement() {
        consume(HASH, "Expect '#'.");
        final Token directive = consume(IDENTIFIER, "Expect directive name.");
        final String name = directive.input();
        if (name.equals("load")) {
            final Token literal = consume(STRING_LITERAL, "Expect string literal.");
            final String path = (String) literal.literal().value();
            return new Directive.Statement.Load(path);
        } else if (name.equals("intrinsic")) {
            return new Directive.Statement.Intrinsic();
        } else {
            throw error(directive, "Unknown directive.");
        }
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

    boolean checkNextNext(Token.Type type) {
        if (isAtEnd()) return false;
        return tokens.get(index + 2).type() == type;
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
