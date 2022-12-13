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
        EQUAL, COLON, TIDE, RANGE, ARROW,

        // Values
        IDENTIFIER, LITERAL,

        // Keywords
        RETURN, IF, ELSE, TRUE, FALSE, FOR, STRUCT, ENUM, UNION,

        // End of file
        EOF,
    }
}
