package org.click;

import org.click.value.Value;

import java.util.*;

import static org.click.Ast.*;
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
            List<Statement.Assign.Target> targets = new ArrayList<>();
            // Read all identifiers separated by commas
            do {
                final Token identifier = consume(IDENTIFIER, "Expected identifier");
                final String name = identifier.input();
                final List<AccessPoint> accessPoint = nextAccessPoint();
                targets.add(new Statement.Assign.Target(name, accessPoint));
            } while (match(COMMA));
            final List<String> names = targets.stream().map(Statement.Assign.Target::name).toList();
            if (match(EQUAL)) {
                // Assign
                final Expression expression = nextExpression();
                statement = new Statement.Assign(targets, expression);
            } else if (match(COLON)) {
                // Declare
                final Type explicitType = nextType();
                DeclarationType declarationType;
                if (match(COLON)) {
                    declarationType = DeclarationType.CONSTANT;
                } else if (match(EQUAL)) {
                    declarationType = DeclarationType.VARIABLE;
                } else if (match(TILDE)) {
                    declarationType = DeclarationType.SHARED;
                } else {
                    throw error("Expected declaration type");
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
                final Expression.Call call = new Expression.Call(name, new Parameter.Passed.Positional(arguments));
                statement = new Statement.Run(call);
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
        } else if (check(FOR)) {
            statement = nextLoop();
        } else if (match(BREAK)) {
            statement = new Statement.Break();
        } else if (match(CONTINUE)) {
            statement = new Statement.Continue();
        } else if (check(SELECT)) {
            final Expression.Select select = nextSelect();
            statement = new Statement.Run(select);
        } else if (check(JOIN)) {
            statement = nextJoin();
        } else if (check(SPAWN)) {
            statement = nextSpawn();
        } else if (check(LEFT_BRACE)) {
            if (checkTrailing(SEMICOLON, RETURN, IF, FOR, BREAK, CONTINUE, SELECT, SPAWN, HASH)) {
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

    List<AccessPoint> nextAccessPoint() {
        List<AccessPoint> accesses = new ArrayList<>();
        while (check(DOT) || check(LEFT_BRACKET)) {
            if (match(DOT)) {
                // Field access
                accesses.add(new AccessPoint.Field(advance().input()));
            } else if (match(LEFT_BRACKET)) {
                // Index access
                final Expression expression = nextExpression();
                consume(RIGHT_BRACKET, "Expected ']' after array index.");
                final Type transmutedType = check(IDENTIFIER) ? nextType() : null;
                accesses.add(new AccessPoint.Index(expression, transmutedType));
            }
        }
        return accesses;
    }

    Expression nextExpression() {
        if (check(SEMICOLON)) return null;
        // Range
        if (check(NUMBER_LITERAL) && checkNext(RANGE)) {
            final Expression start = nextExpression(0);
            consume(RANGE, "Expected '..' after start of range.");
            final Expression end = nextExpression(0);
            final Expression step = match(RANGE) ? nextExpression(0) :
                    new Expression.Constant(new Value.NumberLiteral(1));
            return new Expression.Range(start, end, step);
        }
        // Pratt's algorithm
        return nextExpression(0);
    }

    Expression nextExpression(int precedence) {
        Expression expression = nextPrimary();
        while (precedence < getPrecedence(peek().type())) {
            final Token operator = advance();
            final Expression right = nextExpression(getPrecedence(operator.type()));
            expression = new Expression.Operation(expression, operator.type(), right);
        }
        return expression;
    }

    private Expression nextPrimary() {
        if (check(LEFT_PAREN)) {
            if (checkNext(RIGHT_PAREN) || checkNext(IDENTIFIER) && checkNextNext(COLON)) {
                return new Expression.Constant(nextFunction());
            } else {
                consume(LEFT_PAREN, "Expected '(' after expression.");
                final Expression expression = nextExpression();
                consume(RIGHT_PAREN, "Expected ')' after expression.");
                return expression;
            }
        } else if (check(STRUCT)) {
            return new Expression.Constant(nextStruct());
        } else if (check(ENUM)) {
            return nextEnum();
        } else if (check(UNION)) {
            return new Expression.Constant(nextUnion());
        } else if (match(STRING_LITERAL)) {
            final Token literal = previous();
            final Object value = literal.value();
            return new Expression.Constant(new Value.StringLiteral((String) value));
        } else if (match(RUNE_LITERAL)) {
            final Token literal = previous();
            final Object value = literal.value();
            return new Expression.Constant(new Value.RuneLiteral((String) value));
        } else if (match(NUMBER_LITERAL)) {
            final Object value = previous().value();
            return new Expression.Constant(new Value.NumberLiteral((Number) value));
        } else if (match(TRUE)) {
            return new Expression.Constant(new Value.BooleanLiteral(true));
        } else if (match(FALSE)) {
            return new Expression.Constant(new Value.BooleanLiteral(false));
        } else if (match(EXCLAMATION)) {
            final Expression expression = nextExpression();
            return new Expression.Unary(EXCLAMATION, expression);
        } else if (match(AT)) {
            final Expression.Contextual contextual = new Expression.Contextual();
            if (check(DOT) || check(LEFT_BRACKET)) {
                final List<AccessPoint> accessPoint = nextAccessPoint();
                return new Expression.Access(contextual, accessPoint);
            } else {
                return contextual;
            }
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
                final Type type = Type.of(identifier.input());
                // Struct
                if (checkNext(RIGHT_BRACE)) {
                    // Empty struct
                    consume(LEFT_BRACE, "Expected '{' after struct name.");
                    consume(RIGHT_BRACE, "Expected '}' after struct.");
                    return new Expression.Initialization(type, new Parameter.Passed.Positional(List.of()));
                }
                final int index = this.index;
                try {
                    final Class<? extends Parameter.Passed> paramType = check(LEFT_BRACE) && checkNext(DOT) ?
                            Parameter.Passed.Named.class : Parameter.Passed.Positional.class;
                    final Parameter.Passed passed = nextPassedParameters(paramType);
                    return new Expression.Initialization(type, passed);
                } catch (RuntimeException e) {
                    // Variable
                    this.index = index;
                    return new Expression.Variable(identifier.input());
                }
            } else if (check(DOT) && (checkNext(STRING_LITERAL) || checkNext(NUMBER_LITERAL))) {
                // Binary literal
                consume(DOT, "Expected '.' after binary literal.");
                final String name = identifier.input();
                final String content = advance().input();
                return new Expression.Binary(name, content);
            } else {
                // Access
                final Expression.Variable variable = new Expression.Variable(identifier.input());
                final List<AccessPoint> accessPoint = nextAccessPoint();
                if (match(WHERE)) {
                    final Expression constraint = nextExpression();
                    return new Expression.Constraint(variable, accessPoint, constraint);
                } else {
                    return accessPoint.isEmpty() ? variable :
                            new Expression.Access(variable, accessPoint);
                }
            }
        } else if (check(SELECT)) {
            return nextSelect();
        } else if (match(DOLLAR)) {
            // Variable await
            final Token identifier = consume(IDENTIFIER, "Expected variable name.");
            return new Expression.VariableAwait(identifier.input());
        } else if (check(LEFT_BRACKET)) {
            final Type.Array type = (Type.Array) nextType();
            assert type != null;
            if (check(LEFT_BRACE)) {
                // Array initialization
                final Parameter.Passed.Positional passed = nextPassedParameters(Parameter.Passed.Positional.class);
                assert type.length() == passed.expressions().size() : "Array length does not match passed parameters.";
                return new Expression.Initialization(type, passed);
            }
            if (check(SEMICOLON)) {
                // Empty array
                return new Expression.Initialization(type, new Parameter.Passed.Positional(List.of()));
            }
            // Array function
            final Expression expression = nextExpression();
            return new Expression.Initialization(type, new Parameter.Passed.Supplied(expression));
        } else if (check(LEFT_BRACE)) {
            // Inline block
            final Parameter.Passed passed = nextPassedParameters(null);
            return new Expression.Initialization(null, passed);
        } else {
            throw error("Expect expression: " + peek());
        }
    }

    private <T extends Parameter.Passed> T nextPassedParameters(Class<T> preferred) {
        consume(LEFT_BRACE, "Expected '{' after struct name.");
        if (preferred == null) {
            if (check(DOT)) {
                preferred = (Class<T>) Parameter.Passed.Named.class;
            } else {
                preferred = (Class<T>) Parameter.Passed.Positional.class;
            }
        }
        if (preferred == Parameter.Passed.Named.class) {
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
        if (!(check(IDENTIFIER) || check(LEFT_BRACKET) || check(LEFT_PAREN))) return null;
        if (match(LEFT_BRACKET)) {
            // Array
            long length = -1;
            if (check(NUMBER_LITERAL)) {
                // TODO: make mandatory, currently not possible because of function param
                final Token lengthLiteral = consume(NUMBER_LITERAL, "Expected number for array length.");
                length = ((Long) lengthLiteral.value()).longValue();
            }
            consume(RIGHT_BRACKET, "Expected ']'.");
            final Type arrayType = nextType();
            return new Type.Array(arrayType, length);
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

    Value nextFunction() {
        consume(LEFT_PAREN, "Expect '('.");
        final List<Parameter> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    throw error("Cannot have more than 255 parameters.");
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
        if (match(SEMICOLON)) {
            // External function
            return new Value.ExternFunctionDecl(parameters, returnType);
        }
        final List<Statement> body = nextBlock();
        return new Value.FunctionDecl(parameters, returnType, body, null);
    }

    private Value.StructDecl nextStruct() {
        consume(STRUCT, "Expect 'struct'.");
        consume(LEFT_BRACE, "Expect '{'.");
        final List<Parameter> fields = new ArrayList<>();
        if (!check(RIGHT_BRACE)) {
            do {
                if (fields.size() >= 255) {
                    throw error("Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                consume(COLON, "Expect ':' after field name.");
                final Type type = nextType();
                final Parameter parameter = new Parameter(identifier.input(), type);
                fields.add(parameter);
            } while (match(COMMA) && !check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Value.StructDecl(fields);
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
                    throw error("Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                final Expression value;
                if (type != null) {
                    consume(COLON, "Expect ':' after field name.");
                    consume(COLON, "Expect '::' after field name.");
                    value = nextExpression();
                } else {
                    value = new Expression.Constant(new Value.NumberLiteral(index++));
                }
                entries.put(identifier.input(), value);
            } while (match(COMMA) && !check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Expression.Enum(type, entries);
    }

    private Value.UnionDecl nextUnion() {
        consume(UNION, "Expect 'union'.");
        consume(LEFT_BRACE, "Expect '{'.");
        Map<String, Value.StructDecl> entries = new HashMap<>();
        if (!check(RIGHT_BRACE)) {
            do {
                if (entries.size() >= 255) {
                    throw error("Cannot have more than 255 fields.");
                }
                final Token identifier = consume(IDENTIFIER, "Expect field name.");
                Value.StructDecl structDecl = null;
                if (match(COLON)) {
                    // Inline struct
                    consume(COLON, "Expect ':' after field name.");
                    structDecl = nextStruct();
                }
                entries.put(identifier.input(), structDecl);
            } while (match(COMMA) && !check(RIGHT_BRACE));
        }
        consume(RIGHT_BRACE, "Expect '}'.");
        return new Value.UnionDecl(entries);
    }

    Statement.Branch nextBranch() {
        consume(IF, "Expect 'if'.");
        final Expression condition = nextExpression();
        final Statement thenBranch = nextStatement();
        final Statement elseBranch = match(ELSE) ? nextStatement() : null;
        return new Statement.Branch(condition, thenBranch, elseBranch);
    }

    Statement.Loop nextLoop() {
        consume(FOR, "Expect 'for' or 'fork'.");
        // Infinite loop
        if (check(LEFT_BRACE) || check(ARROW)) {
            return new Statement.Loop(null, null, nextStatement());
        }
        List<Statement.Loop.Declaration> declarations = new ArrayList<>();
        if (check(LEFT_PAREN) || check(DOT) || check(IDENTIFIER) && (checkNext(COLON) || checkNext(COMMA))) {
            final boolean paren = match(LEFT_PAREN);
            do {
                if (declarations.size() >= 255) {
                    throw error("Cannot have more than 255 declarations.");
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
        return new Statement.Loop(declarations, iterable, body);
    }

    Expression.Select nextSelect() {
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
        return new Expression.Select(blocks);
    }

    Statement.Join nextJoin() {
        consume(JOIN, "Expect 'join'.");
        final Statement.Block block = new Statement.Block(nextBlock());
        return new Statement.Join(block);
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
        throw error("Expect '{' or '->'.");
    }

    Directive.Statement nextDirectiveStatement() {
        consume(HASH, "Expect '#'.");
        final Token directive = consume(IDENTIFIER, "Expect directive name.");
        final String name = directive.input();
        if (name.equals("load")) {
            final Token literal = consume(STRING_LITERAL, "Expect string literal.");
            final String path = (String) literal.value();
            return new Directive.Statement.Load(path);
        } else {
            throw error("Unknown directive.");
        }
    }

    Token consume(Token.Type type, String message) {
        if (check(type)) return advance();
        throw error(message);
    }

    Token advance() {
        if (!isAtEnd()) index++;
        return previous();
    }

    Token previous() {
        return tokens.get(index - 1);
    }

    boolean match(Token.Type... types) {
        for (Token.Type type : types) {
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
        return peekNext().type() == type;
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

    Token peekNext() {
        return tokens.get(index + 1);
    }

    private RuntimeException error(String message) {
        return new RuntimeException("Error at line " + peek().line() + ": " + message);
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
