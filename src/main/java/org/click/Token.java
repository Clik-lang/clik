package org.click;

import org.jetbrains.annotations.Nullable;

public record Token(Type type, int line, String input,
                    @Nullable Object value) {
    public Token(Type type, int line, String input) {
        this(type, line, input, null);
    }

    public enum Type {
        // Single character tokens
        LEFT_PAREN, RIGHT_PAREN,
        LEFT_BRACE, RIGHT_BRACE,
        LEFT_BRACKET, RIGHT_BRACKET,
        COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

        // Boolean operators
        AND, OR, EQUAL_EQUAL, NOT_EQUAL,
        LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,

        // Declarations
        EQUAL, COLON, TIDE, RANGE, ARROW,

        // Values
        IDENTIFIER, LITERAL,

        // Keywords
        RETURN, IF, ELSE, TRUE, FALSE, FOR, STRUCT, ENUM, UNION,

        // End of file
        EOF,
    }
}
