package org.click;

import java.util.ArrayList;
import java.util.List;

public final class Scanner {
    private final String input;
    private int index;
    private int line;

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
        Object value = null;

        char c = advance();
        if (c == '(') type = Token.Type.LEFT_PAREN;
        else if (c == ')') type = Token.Type.RIGHT_PAREN;
        else if (c == '{') type = Token.Type.LEFT_BRACE;
        else if (c == '}') type = Token.Type.RIGHT_BRACE;
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
        else if (c == '=') type = Token.Type.EQUAL;
        else if (c == ':') type = Token.Type.COLON;
        else if (c == '~') type = Token.Type.TIDE;
        else if (c == '\"') {
            type = Token.Type.LITERAL;
            value = nextString();
        } else if (Character.isDigit(c)) {
            type = Token.Type.LITERAL;
            value = nextNumber();
        } else if (Character.isLetter(c)) {
            value = nextIdentifier();
            if (value.equals("return")) {
                type = Token.Type.RETURN;
            } else if (value.equals("if")) {
                type = Token.Type.IF;
            } else if (value.equals("else")) {
                type = Token.Type.ELSE;
            } else if (value.equals("true")) {
                type = Token.Type.TRUE;
            } else if (value.equals("false")) {
                type = Token.Type.FALSE;
            } else if (value.equals("for")) {
                type = Token.Type.FOR;
            } else if (value.equals("struct")) {
                type = Token.Type.STRUCT;
            } else {
                type = Token.Type.IDENTIFIER;
            }
        } else if (c == '\n') {
            line++;
            return nextToken();
        } else {
            throw new RuntimeException("Unexpected character: " + c);
        }

        final String text = input.substring(startIndex, index);
        return new Token(type, line, text, value);
    }

    private String nextIdentifier() {
        var start = index - 1;
        while (Character.isLetterOrDigit(peek())) advance();
        return input.substring(start, index);
    }

    private Number nextNumber() {
        var start = index - 1;
        while (Character.isDigit(peek())) advance();
        if (peek() != '.' || !Character.isDigit(peekNext())) {
            // Integer
            return Integer.parseInt(input.substring(start, index));
        }
        // Float
        advance();
        while (Character.isDigit(peek())) advance();
        var text = input.substring(start, index);
        return Double.parseDouble(text);
    }

    private String nextString() {
        final var start = index;
        while (peek() != '\"') {
            if (peek() == '\n') line++;
            advance();
        }
        advance();
        return input.substring(start, index - 1);
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
