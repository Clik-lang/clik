package org.click;

import org.jetbrains.annotations.Nullable;

public record Token(Type type, int line, String input,
                    @Nullable Object value) {
    public Token(Type type, int line, String input) {
        this(type, line, input, null);
    }

    enum Type {
        // Single character tokens
        LEFT_PAREN, RIGHT_PAREN,
        LEFT_BRACE, RIGHT_BRACE,
        COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

        // Declarations
        EQUAL, COLON, TIDE,

        // Values
        IDENTIFIER, LITERAL,

        // Keywords
        IF, ELSE, STRUCT, ENUM, UNION,

        // End of file
        EOF,
    }
}
