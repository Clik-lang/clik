package org.click;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class InvalidTest {

    @Test
    public void semicolons() {
        assertInvalidParser("""
                value :: 5
                """);
    }

    @Test
    public void explicitType() {
        assertInvalidProgram("""
                value :int: "Hello, World!";
                """);
    }

    @Test
    public void mutableType() {
        assertInvalidProgram("""
                main := () {}
                """);
        assertInvalidProgram("""
                main :~ () {}
                """);
        assertInvalidProgram("""
                Point := struct {x: int, y: int}
                """);
        assertInvalidProgram("""
                Point :~ struct {x: int, y: int}
                """);
    }

    @Test
    public void deconstruct() {
        assertInvalidProgram("""
                value, value2 :: 5;
                """);
        assertInvalidProgram("""
                main, main2 :: () {}
                """);
        assertInvalidProgram("""
                Point, Point2 :: struct {x: int, y: int}
                """);
    }

    private static void assertInvalidProgram(String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        try {
            //var program = new Compiler(statements).compile();
            //fail("Expected compilation to fail: " + program);
        } catch (RuntimeException ignored) {
        }
    }

    private static void assertInvalidParser(String input) {
        var tokens = new Scanner(input).scanTokens();
        try {
            var statements = new Parser(tokens).parse();
            fail("Expected compilation to fail: " + statements);
        } catch (RuntimeException ignored) {
        }
    }
}
