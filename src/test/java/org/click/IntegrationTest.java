package org.click;

import org.click.interpreter.VM;
import org.click.interpreter.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class IntegrationTest {
    private static final Value TRUE = new Value.Constant(Type.BOOL, true);
    private static final Value FALSE = new Value.Constant(Type.BOOL, false);

    private static final Value ZERO = new Value.Constant(Type.I32, 0);
    private static final Value ONE = new Value.Constant(Type.I32, 1);
    private static final Value TWO = new Value.Constant(Type.I32, 2);

    @Test
    public void functionBlock() {
        assertProgram(ZERO,
                """
                        main :: () i32 {
                            return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () i32 {
                            0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () i32 -> 0;
                        """);

        assertProgram(ZERO, "main_score",
                """
                        main_score :: () i32 {
                            return 0;
                        }
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
                        main :: () i32 -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: () i32 -> 1 + 1;
                        """);

        assertProgram(TWO,
                """
                        main :: () i32 {
                            value := 1;
                            value = value + 1;
                            return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 14),
                """
                        main :: () i32 -> 2 + 3 * 4;
                        """);
        assertProgram(new Value.Constant(Type.I32, 20),
                """
                        main :: () i32 -> (2 + 3) * 4;
                        """);
    }

    @Test
    public void mathBool() {
        assertProgram(TRUE,
                """
                        main :: () bool -> true;
                        """);
        assertProgram(FALSE,
                """
                        main :: () bool -> false;
                        """);

        assertProgram(TRUE,
                """
                        main :: () bool -> 1 == 1;
                        """);
        assertProgram(TRUE,
                """
                        main :: () bool -> 1 + 1 == 2;
                        """);

        assertProgram(TRUE,
                """
                        main :: () bool -> true && true;
                        """);
        assertProgram(TRUE,
                """
                        main :: () bool -> true || true;
                        """);

        assertProgram(FALSE,
                """
                        main :: () bool -> true && false;
                        """);

        assertProgram(FALSE,
                """
                        main :: () bool -> false || false;
                        """);
    }

    @Test
    public void array() {
        assertProgram(ONE,
                """
                        main :: () i32 {
                          array :: []i32 {1, 2, 3, 4, 5};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () i32 {
                          array :: []i32 {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                          array :: []Point {
                            Point {.x: 1, .y: 2},
                          }
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                          array :: []Point {{1,2}}
                          return array[0];
                        }
                        """);
    }

    @Test
    public void map() {
        assertProgram(new Value.Constant(Type.I32, 5),
                """
                        main :: () i32 {
                          values :: map[string]i32 {"test": 5};
                          return values["test"];
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 5),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                          values :: map[Point]i32 {{1, 2}: 5};
                          return values[{1,2}];
                        }
                        """);
    }

    @Test
    public void struct() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point -> Point {1, 2};
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { Point {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return Point {1, 2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return Point {.x:1, .y:2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
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

        assertProgram(TWO,
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: () Point -> Point {1, 2};
                        """);
        assertProgram(TWO,
                """
                        main :: () i32 {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: () Point -> Point {1, 2};
                        Point :: struct {x: i32, y: i32}
                        """);
        assertProgram(TWO,
                """
                        main :: () i32 {
                            point :: update_point(Point{1, 2});
                            return point.x;
                        }
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: i32, y: i32}
                        """);
        assertProgram(TWO,
                """
                        main :: () i32 {
                            point :: update_point({1, 2});
                            return point.x;
                        }
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: i32, y: i32}
                        """);

        assertProgram(new Value.Constant(Type.STRING, "test"),
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: i32, y: i32}
                        main :: () string {
                            player :: Player {"test", {1, 2}};
                            return player.name;
                        }
                        """);

        assertProgram(TWO,
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                            player :: Player {"test", {1, 2}};
                            return player.point.y;
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
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point {
                          Component :: enum Point {
                            Position :: {.x: 1, .y: 2},
                          }
                          return Component.Position;
                        }
                        """);
    }

    @Test
    public void union() {
        assertProgram(ONE,
                """
                        Point :: struct {x: i32, y: i32};
                        main :: () i32 {
                          Component :: union {
                            Point,
                            Velocity :: struct {x: i32, y: i32},
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: i32, y: i32};
                        main :: () i32 {
                          Component :: union {
                            Point,
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () i32 {
                          Component :: union {
                            Position :: struct {x: i32, y: i32},
                            Velocity :: struct {x: i32, y: i32},
                          }
                          value :: Position {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: i32, y: i32},
                            Velocity :: struct {x: i32, y: i32},
                        }
                        main :: () Component {
                          value :Component: Position {1, 2};
                          return value;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: i32, y: i32},
                            Velocity :: struct {x: i32, y: i32},
                        }
                        main :: () Component {
                          return Position {1, 2};
                        }
                        """);
    }

    @Test
    public void branch() {
        assertProgram(ONE,
                """
                        main :: () i32 {
                          value := 0;
                          if true -> value = 1;
                          return value;
                        }
                        """);
    }

    @Test
    public void loop() {
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value := 0;
                          for 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value := 0;
                          for i: 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 45),
                """
                        main :: () i32 {
                          value := 0;
                          for i: 0..10 -> value = value + i;
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () i32 {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            break;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value :~ 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 9),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x: array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 12),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .y: array -> value = value + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 9),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x, .y: array -> value = value + x;
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 9),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 21),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 9),
                """
                        main :: () i32 {
                          Point :: struct {x: i32, y: i32}
                          array :: []Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for p: array -> value = value + p.x;
                          return value;
                        }
                        """);
    }

    @Test
    public void fork() {
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value :~ 0;
                          fork 0..10 {
                            value = value + 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value :~ 0;
                          fork 0..10 {
                            {value = value + 1;}
                          }
                          return value;
                        }
                        """);
    }

    @Test
    public void select() {
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value := 5;
                          select {
                            test :: 10; -> value = test;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value := 5;
                          select {
                            value = 10; {}
                            #sleep(3000); {}
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 15),
                """
                        main :: () i32 {
                          value := 5;
                          select {
                            value = 10; {value = 15;}
                            #sleep(3000); {}
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.Constant(Type.I32, 10),
                """
                        main :: () i32 {
                          value :~ 0;
                          fork 0..10 {
                            select {
                              test :: 10; -> value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Constant(Type.I32, 110),
                """
                        main :: () i32 {
                          value :~ 0;
                          fork 0..10 {
                            select {
                              value = 10; -> value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () i32 {
                          value :~ 0;
                          fork i: 0..2 {
                            if i == 1 {
                              continue;
                            }
                            value = value + 1;
                          }
                          return value;
                        }
                        """);
    }

    @Test
    public void explicitType() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point -> {.x: 1, .y: 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point -> {1, 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point {
                            value :Point: {1, 2};
                            return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of(
                        "x", new Value.Constant(Type.I32, 3), "y", new Value.Constant(Type.I32, 4))),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () Point {
                          point :Point = {.x: 1, .y: 2}
                          point = {.x: 3, .y: 4}
                          return point;
                        }
                        """);
    }

    @Test
    public void multiReturn() {
        assertProgram(new Value.Constant(Type.I32, 3),
                """
                        Point :: struct {x: i32, y: i32}
                        main :: () i32 {
                            x, y :: get_point();
                            return x + y;
                        }
                        get_point :: () Point { return {1, 2} }
                        """);
    }

    private static void assertProgram(Value expected, String input) {
        assertProgram(expected, "main", input);
    }

    private static void assertProgram(Value expected, String name, String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        var interpreter = new VM(statements);
        var actual = interpreter.interpret(name, List.of());
        interpreter.stop();
        assertEquals(expected, actual);
    }
}
