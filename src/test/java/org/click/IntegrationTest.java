package org.click;

import org.click.interpreter.Interpreter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class IntegrationTest {

    private static final Expression TRUE = new Expression.Constant(true);
    private static final Expression FALSE = new Expression.Constant(false);

    private static final Expression ZERO = new Expression.Constant(0);
    private static final Expression ONE = new Expression.Constant(1);
    private static final Expression TWO = new Expression.Constant(2);

    @Test
    public void functionBlock() {
        assertProgram(ZERO,
                """
                        main :: () {
                            return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () {
                            0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () -> 0;
                        """);
    }

    @Test
    public void param() {
        assertProgram(ONE,
                """
                        main :: () {
                            return get();
                        }
                        get :: () -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () {
                            value :: get();
                            return value;
                        }
                        get :: () -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () {
                            get();
                        }
                        get :: () -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () -> get();
                        get :: () -> 1;
                        """);
    }

    @Test
    public void mathInteger() {
        assertProgram(ONE,
                """
                        main :: () -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: () -> 1 + 1;
                        """);

        assertProgram(TWO,
                """
                        main :: () i32 {
                            value := 1;
                            value = value + 1;
                            return value;
                        }
                        """);

        assertProgram(new Expression.Constant(14),
                """
                        main :: () -> 2 + 3 * 4;
                        """);
        assertProgram(new Expression.Constant(20),
                """
                        main :: () -> (2 + 3) * 4;
                        """);
    }

    @Test
    public void mathBool() {
        assertProgram(TRUE,
                """
                        main :: () -> true;
                        """);
        assertProgram(FALSE,
                """
                        main :: () -> false;
                        """);

        assertProgram(TRUE,
                """
                        main :: () -> 1 == 1;
                        """);
        assertProgram(TRUE,
                """
                        main :: () -> 1 + 1 == 2;
                        """);

        assertProgram(TRUE,
                """
                        main :: () -> true && true;
                        """);
        assertProgram(TRUE,
                """
                        main :: () -> true || true;
                        """);

        assertProgram(FALSE,
                """
                        main :: () -> true && false;
                        """);

        assertProgram(FALSE,
                """
                        main :: () -> false || false;
                        """);
    }

    @Test
    public void struct() {
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point -> Point {1, 2};
                        """);
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { Point {1, 2} }
                        """);
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return Point {1, 2} }
                        """);

        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return Point {x:1, y:2} }
                        """);

        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        main :: () Point {
                            Point :: struct {x: i32, y: i32}
                            return Point {1, 2};
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () i32 {
                            Point :: struct {x: i32, y: i32}
                            point :: Point {1, 2};
                            return point.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () i32 {
                            Point :: struct {x: i32, y: i32}
                            point :: Point {1, 2};
                            return point.y;
                        }
                        """);
    }

    @Test
    public void enumDecl() {
        assertProgram(ZERO,
                """
                        Direction :: enum {North,South}
                        main :: () i32 -> return Direction.North;
                        """);
        assertProgram(ONE,
                """
                        Direction :: enum {North,South}
                        main :: () i32 -> Direction.South;
                        """);
    }

    @Test
    public void loop() {
        assertProgram(new Expression.Constant(10),
                """
                        main :: () i32 {
                          value := 0;
                          for 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Expression.Constant(10),
                """
                        main :: () i32 {
                          value := 0;
                          for i: 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Expression.Constant(45),
                """
                        main :: () i32 {
                          value := 0;
                          for i: 0..10 -> value = value + i;
                          return value;
                        }
                        """);
    }

    @Test
    public void explicitType() {
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(ONE, TWO))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point {
                            value :Point: {1, 2};
                            return value;
                        }
                        """);
        assertProgram(new Expression.StructValue("Point", new Parameter.Passed.Positional(List.of(
                        new Expression.Constant(3), new Expression.Constant(4)))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point {
                          point :Point= {x: 1, y: 2}
                          point = {x: 3, y: 4}
                          return point;
                        }
                        """);
    }

    private static void assertProgram(Expression expected, String input) {
        assertProgram(expected, "main", input);
    }

    private static void assertProgram(Expression expected, String name, String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        var interpreter = new Interpreter(statements);
        var actual = interpreter.interpret("main");
        interpreter.stop();
        assertEquals(expected, actual);
    }
}
