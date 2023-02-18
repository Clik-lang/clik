package org.click;

import org.click.interpreter.VM;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

@Disabled
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
    public void invalidType() {
        assertInvalidProgram("""
                main :: () int {
                  value :int: "string";
                  return 1;
                }
                """);
        assertInvalidProgram("""
                main :: () int {
                  value :int: "string";
                  return value;
                }
                """);
        assertInvalidProgram("""
                Point :: struct {x: int, y: int}
                main :: () Point {
                  value :Point: "string";
                  return value;
                }
                """);
        assertInvalidProgram("""
                Point :: struct {x: int, y: int}
                main :: () Point {
                  value :int: Point {.x: 1, .y: 2};
                  return value;
                }
                """);
    }

    @Test
    public void alreadyDeclared() {
        assertInvalidProgram("""
                main :: () int {
                  value :: 5;
                  value :: 5;
                  return value;
                }
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

    @Test
    public void distinct() {
        assertInvalidProgram("""
                positive_int :: distinct int where @ > 1;
                main :: () positive_int {
                  return 1;
                }
                """);
    }

    private static void assertInvalidProgram(String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        try {
            var interpreter = new VM(null, statements, Map.of(), Map.of());
            interpreter.interpret("main", List.of());
            fail("Expected compilation to fail.");
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
