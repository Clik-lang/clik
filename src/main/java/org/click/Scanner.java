package org.click;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.HexFormat.isHexDigit;
import static java.util.Map.entry;

public final class Scanner {
    private final String input;
    private int index;
    private int line;

    private static final Map<String, Token.Type> KEYWORDS = Map.ofEntries(
            entry("return", Token.Type.RETURN),
            entry("if", Token.Type.IF),
            entry("else", Token.Type.ELSE),
            entry("true", Token.Type.TRUE),
            entry("false", Token.Type.FALSE),
            entry("for", Token.Type.FOR),
            entry("break", Token.Type.BREAK),
            entry("continue", Token.Type.CONTINUE),
            entry("select", Token.Type.SELECT),
            entry("join", Token.Type.JOIN),
            entry("spawn", Token.Type.SPAWN),
            entry("struct", Token.Type.STRUCT),
            entry("enum", Token.Type.ENUM),
            entry("union", Token.Type.UNION),
            entry("where", Token.Type.WHERE),
            entry("distinct", Token.Type.DISTINCT)
    );

    public Scanner(String input) {
        this.input = input;
    }

    void skipWhitespace() {
        while (Character.isWhitespace(peek())) advance();
    }

    void skipComment() {
        if (peek() == '/' && peekNext() == '/') {
            while (peek() != '\n' && !isAtEnd()) advance();
        }
    }

    Token nextToken() {
        if (isAtEnd()) return null;
        if (peek() == '\n') {
            advance();
            line++;
            return nextToken();
        }
        skipWhitespace();
        if (isAtEnd()) return null;
        skipComment();
        if (peek() == '\n') {
            advance();
            line++;
            return nextToken();
        }
        final int startIndex = index;

        Token.Type type;
        Token.Literal literal = null;

        char c = advance();
        if (c == '(') type = Token.Type.LEFT_PAREN;
        else if (c == ')') type = Token.Type.RIGHT_PAREN;
        else if (c == '{') type = Token.Type.LEFT_BRACE;
        else if (c == '}') type = Token.Type.RIGHT_BRACE;
        else if (c == '[') type = Token.Type.LEFT_BRACKET;
        else if (c == ']') type = Token.Type.RIGHT_BRACKET;
        else if (c == '<') {
            if (peek() == '=') {
                advance();
                type = Token.Type.LESS_EQUAL;
            } else if (peek() == '<') {
                advance();
                type = Token.Type.OUTPUT;
            } else {
                type = Token.Type.LESS;
            }
        } else if (c == '>') {
            if (peek() == '=') {
                advance();
                type = Token.Type.GREATER_EQUAL;
            } else {
                type = Token.Type.GREATER;
            }
        } else if (c == '@') type = Token.Type.AT;
        else if (c == ',') type = Token.Type.COMMA;
        else if (c == '.') {
            if (peek() == '.') {
                advance();
                type = Token.Type.RANGE;
            } else {
                type = Token.Type.DOT;
            }
        } else if (c == '-') {
            if (peek() == '>') {
                advance();
                type = Token.Type.ARROW;
            } else {
                type = Token.Type.MINUS;
            }
        } else if (c == '+') type = Token.Type.PLUS;
        else if (c == ';') type = Token.Type.SEMICOLON;
        else if (c == '*') type = Token.Type.STAR;
        else if (c == '/') type = Token.Type.SLASH;
        else if (c == '%') type = Token.Type.MODULO;
        else if (c == '$') type = Token.Type.DOLLAR;
        else if (c == '#') type = Token.Type.HASH;
        else if (c == '!') {
            if (peek() == '=') {
                advance();
                type = Token.Type.NOT_EQUAL;
            } else {
                type = Token.Type.EXCLAMATION;
            }
        } else if (c == '=') {
            if (peek() == '=') {
                advance();
                type = Token.Type.EQUAL_EQUAL;
            } else {
                type = Token.Type.EQUAL;
            }
        } else if (c == ':') type = Token.Type.COLON;
        else if (c == '~') type = Token.Type.TILDE;
        else if (c == '|') {
            if (peek() == '|') {
                advance();
                type = Token.Type.OR;
            } else {
                throw new RuntimeException("Unexpected character: " + c);
            }
        } else if (c == '&') {
            if (peek() == '&') {
                advance();
                type = Token.Type.AND;
            } else {
                throw new RuntimeException("Unexpected character: " + c);
            }
        } else if (c == '\"') {
            type = Token.Type.STRING_LITERAL;
            literal = nextString();
        } else if (c == '\'') {
            type = Token.Type.RUNE_LITERAL;
            literal = nextRune();
        } else if (c == '`') {
            type = Token.Type.STRING_LITERAL;
            literal = nextRawString();
        } else if (Character.isDigit(c)) {
            literal = nextNumber();
            type = literal.value() instanceof Long ? Token.Type.INTEGER_LITERAL : Token.Type.FLOAT_LITERAL;
        } else if (Character.isLetter(c)) {
            final String value = nextIdentifier();
            type = KEYWORDS.getOrDefault(value, Token.Type.IDENTIFIER);
        } else {
            throw new RuntimeException("Unexpected character: " + c);
        }

        final String text = input.substring(startIndex, index);
        return new Token(type, line, text, literal);
    }

    private String nextIdentifier() {
        var start = index - 1;
        char peek;
        while (Character.isLetterOrDigit((peek = peek())) || peek == '_') advance();
        return input.substring(start, index);
    }

    private Token.Literal nextNumber() {
        final int start = index - 1;

        if (peek() == 'x') {
            // Hexadecimal integer
            advance();
            while (isHexDigit(peek())) {
                advance();
                if (peek() == '_' && isHexDigit(peekNext())) advance();
            }
            final String text = input.substring(start + 2, index).replace("_", "");
            final Type type = nextNumberSuffix(Type.INT, 'i', 'u');
            final long value = Long.parseLong(text, 16);
            return new Token.Literal(type, value);
        }

        if (peek() == 'b') {
            // Binary integer
            advance();
            while (peek() == '0' || peek() == '1') {
                advance();
                if (peek() == '_' && (peekNext() == '0' || peekNext() == '1')) advance();
            }
            final String text = input.substring(start + 2, index).replace("_", "");
            final Type type = nextNumberSuffix(Type.INT, 'i', 'u');
            final long value = Long.parseLong(text, 2);
            return new Token.Literal(type, value);
        }

        do {
            if (peek() == '_' && Character.isDigit(peekNext())) advance();
            if (Character.isDigit(peek())) advance();
        } while (Character.isDigit(peek()) || peek() == '_');
        if (peek() != '.' || !Character.isDigit(peekNext())) {
            // Integer
            final String text = input.substring(start, index).replace("_", "");
            final long value = Long.parseLong(text);
            final Type type = nextNumberSuffix(Type.INT, 'i', 'u');
            return new Token.Literal(type, value);
        }
        // Float
        advance();
        do {
            if (peek() == '_' && Character.isDigit(peekNext())) advance();
            if (Character.isDigit(peek())) advance();
        } while (Character.isDigit(peek()) || peek() == '_');
        final String text = input.substring(start, index).replace("_", "");
        final double value = Double.parseDouble(text);
        final Type type = nextNumberSuffix(Type.FLOAT, 'f');
        return new Token.Literal(type, value);
    }

    private @Nullable Type nextNumberSuffix(Type defaultType, char... allowedSuffixes) {
        if (Character.isLetter(peek())) {
            final char suffix = advance();
            boolean found = false;
            for (char allowedSuffix : allowedSuffixes) {
                if (suffix == allowedSuffix) {
                    found = true;
                    break;
                }
            }
            if (!found) throw new RuntimeException("Unexpected number suffix: " + suffix);
            StringBuilder builder = new StringBuilder("" + suffix);
            while (Character.isLetterOrDigit(peek())) {
                builder.append(advance());
            }
            final String name = builder.toString();
            return Type.of(name);
        }
        return defaultType;
    }

    private Token.Literal nextString() {
        final StringBuilder builder = new StringBuilder();
        while (peek() != '\"') {
            if (peek() == '\\') advance();
            final char c = advance();
            builder.append(c);
        }
        advance();
        return new Token.Literal(Type.STRING, builder.toString());
    }

    private Token.Literal nextRune() {
        final StringBuilder builder = new StringBuilder();
        while (peek() != '\'') {
            if (peek() == '\\') advance();
            final char c = advance();
            builder.append(c);
        }
        advance();
        return new Token.Literal(Type.RUNE, builder.toString());
    }

    private Token.Literal nextRawString() {
        final StringBuilder builder = new StringBuilder();
        while (peek() != '`') {
            final char c = advance();
            builder.append(c);
        }
        advance();
        return new Token.Literal(Type.STRING, builder.toString());
    }

    char advance() {
        return input.charAt(index++);
    }

    char peekNext() {
        return input.charAt(index + 1);
    }

    char peek() {
        if (isAtEnd()) return '\0';
        return input.charAt(index);
    }

    private boolean isAtEnd() {
        return index >= input.length();
    }

    public List<Token> scanTokens() {
        List<Token> tokens = new ArrayList<>();
        Token token;
        while ((token = nextToken()) != null) {
            tokens.add(token);
        }
        tokens.add(new Token(Token.Type.EOF, line, ""));
        return List.copyOf(tokens);
    }
}
