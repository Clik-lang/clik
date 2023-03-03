package org.click;

import org.jetbrains.annotations.Nullable;

public record Token(Type type, int line, String input,
                    @Nullable Literal literal) {
    public Token(Type type, int line, String input) {
        this(type, line, input, null);
    }

    public record Literal(org.click.Type type, Object value) {
    }

    public enum Type {
        // Single character tokens
        LEFT_PAREN, RIGHT_PAREN,
        LEFT_BRACE, RIGHT_BRACE,
        LEFT_BRACKET, RIGHT_BRACKET,
        AT,
        COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR, MODULO,
        DOLLAR, HASH,

        // Boolean operators
        EXCLAMATION, AND, OR, EQUAL_EQUAL, NOT_EQUAL,
        LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,

        // Declarations
        EQUAL, COLON, TILDE, RANGE, ARROW,

        // Values
        IDENTIFIER, STRING_LITERAL, RUNE_LITERAL, NUMBER_LITERAL,

        // Keywords
        RETURN, IF, ELSE, TRUE, FALSE,
        FOR, BREAK, CONTINUE, SELECT, JOIN, SPAWN,
        STRUCT, ENUM, UNION, WHERE,

        // End of file
        EOF,
    }
}
