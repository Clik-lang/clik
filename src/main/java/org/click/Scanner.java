package org.click;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            entry("fork", Token.Type.FORK),
            entry("break", Token.Type.BREAK),
            entry("continue", Token.Type.CONTINUE),
            entry("select", Token.Type.SELECT),
            entry("spawn", Token.Type.SPAWN),
            entry("defer", Token.Type.DEFER),
            entry("map", Token.Type.MAP),
            entry("struct", Token.Type.STRUCT),
            entry("enum", Token.Type.ENUM),
            entry("union", Token.Type.UNION)
    );

    public Scanner(String input) {
        this.input = input;
    }

    void skipWhitespace() {
        while (Character.isWhitespace(peek())) advance();
    }

    Token nextToken() {
        if (index >= input.length())
            return null;
        skipWhitespace();
        if (index >= input.length())
            return null;
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
        else if (c == '$') type = Token.Type.DOLLAR;
        else if (c == '#') type = Token.Type.HASH;
        else if (c == '=') {
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
            type = Token.Type.LITERAL;
            literal = nextString();
        } else if (Character.isDigit(c)) {
            type = Token.Type.LITERAL;
            literal = nextNumber();
        } else if (Character.isLetter(c)) {
            final String value = nextIdentifier();
            type = KEYWORDS.getOrDefault(value, Token.Type.IDENTIFIER);
        } else if (c == '\n') {
            line++;
            return nextToken();
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
        var start = index - 1;
        while (Character.isDigit(peek())) advance();
        if (peek() != '.' || !Character.isDigit(peekNext())) {
            // Integer
            final int value = Integer.parseInt(input.substring(start, index));
            return new Token.Literal(Type.I32, value);
        }
        // Float
        advance();
        while (Character.isDigit(peek())) advance();
        final String text = input.substring(start, index);
        final double value = Double.parseDouble(text);
        return new Token.Literal(Type.F64, value);
    }

    private Token.Literal nextString() {
        final var start = index;
        while (peek() != '\"') {
            if (peek() == '\n') line++;
            advance();
        }
        advance();
        final String value = input.substring(start, index - 1);
        // Escape string
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\\') {
                i++;
                if (value.charAt(i) == 'n') sb.append('\n');
                else if (value.charAt(i) == 't') sb.append('\t');
                else if (value.charAt(i) == 'r') sb.append('\r');
                else if (value.charAt(i) == '0') sb.append('\0');
                else if (value.charAt(i) == '\'') sb.append('\'');
                else if (value.charAt(i) == '\"') sb.append('\"');
                else if (value.charAt(i) == '\\') sb.append('\\');
                else throw new RuntimeException("Unexpected character: " + value.charAt(i));
            } else {
                sb.append(value.charAt(i));
            }
        }
        return new Token.Literal(Type.STRING, sb.toString());
    }

    char advance() {
        return input.charAt(index++);
    }

    char peekNext() {
        return input.charAt(index + 1);
    }

    char peek() {
        if (index >= input.length()) return '\0';
        return input.charAt(index);
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
