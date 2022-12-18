package org.click;

import org.click.interpreter.VM;
import org.click.interpreter.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class IntegrationTest {
    private static final Value TRUE = new Value.BooleanLiteral(true);
    private static final Value FALSE = new Value.BooleanLiteral(false);

    private static final Value ZERO = new Value.IntegerLiteral(Type.INT, 0);
    private static final Value ONE = new Value.IntegerLiteral(Type.INT, 1);
    private static final Value TWO = new Value.IntegerLiteral(Type.INT, 2);

    @Test
    public void functionBlock() {
        assertProgram(null,
                """
                        main :: fn() {
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                            return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                            0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int -> 0;
                        """);

        assertProgram(ZERO, "main_score",
                """
                        main_score :: fn() int {
                            return 0;
                        }
                        """);
    }

    @Test
    public void comment() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                            // This is a comment
                            return 0;
                        }
                        """);
    }

    @Test
    public void param() {
        assertProgram(ONE,
                """
                        main :: fn() {
                            return get();
                        }
                        get :: fn() -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: fn() {
                            value :: get();
                            return value;
                        }
                        get :: fn() -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: fn() {
                            get();
                        }
                        get :: fn() -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: fn() -> get();
                        get :: fn() -> 1;
                        """);
    }

    @Test
    public void numberLiterals() {
        assertProgram(ONE,
                """
                        main :: fn() int -> 1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.I32, 1),
                """
                        main :: fn() i32 -> 1i32;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.U8, 1),
                """
                        main :: fn() u8 -> 1u8;
                        """);

        assertProgram(new Value.FloatLiteral(Type.F64, 5.5),
                """
                        main :: fn() f64 -> 5.5;
                        """);
        assertProgram(new Value.FloatLiteral(Type.F64, 5.5),
                """
                        main :: fn() f64 -> 5.5f64;
                        """);
        assertProgram(new Value.FloatLiteral(Type.F32, 5.5),
                """
                        main :: fn() f32 -> 5.5f32;
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int -> 0x1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: fn() int -> 0xFF;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: fn() int -> 0xfF;
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int -> 0b1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: fn() int -> 0b11111111;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 254),
                """
                        main :: fn() int -> 0b11111110;
                        """);
    }

    @Test
    public void lambda() {
        assertProgram(ONE,
                """
                        main :: fn() int {
                          function :: fn() int -> 1;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          number :: 1;
                          function :: fn() int -> number;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          function :: fn(arg: int) int -> arg;
                          return function(1);
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          constant :: 5;
                          function :: fn(arg: int) int -> arg + 5;
                          return function(5);
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          number := 1;
                          function :: fn() int -> number;
                          number = 0;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          number :~ 0;
                          function :: fn() -> number = 1;
                          function();
                          return $number;
                        }
                        """);
    }

    @Test
    public void mathInteger() {
        assertProgram(ONE,
                """
                        main :: fn() int -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int -> 1 + 1;
                        """);

        assertProgram(TWO,
                """
                        main :: fn() int {
                            value := 1;
                            value = value + 1;
                            return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 14),
                """
                        main :: fn() int -> 2 + 3 * 4;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 20),
                """
                        main :: fn() int -> (2 + 3) * 4;
                        """);
    }

    @Test
    public void mathBool() {
        assertProgram(TRUE,
                """
                        main :: fn() bool -> true;
                        """);
        assertProgram(FALSE,
                """
                        main :: fn() bool -> false;
                        """);

        assertProgram(TRUE,
                """
                        main :: fn() bool -> 1 == 1;
                        """);
        assertProgram(TRUE,
                """
                        main :: fn() bool -> 1 + 1 == 2;
                        """);

        assertProgram(TRUE,
                """
                        main :: fn() bool -> true && true;
                        """);
        assertProgram(TRUE,
                """
                        main :: fn() bool -> true || true;
                        """);

        assertProgram(FALSE,
                """
                        main :: fn() bool -> true && false;
                        """);

        assertProgram(FALSE,
                """
                        main :: fn() bool -> false || false;
                        """);
    }

    @Test
    public void array() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          array :: [1]int;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          array :: [5]int {1, 2, 3, 4, 5};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          array :: [5]int {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          array :[5]int: [5]int {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                          array :: [1]Point {
                            Point {.x: 1, .y: 2},
                          }
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                          array :: [1]Point {{1,2}}
                          return array[0];
                        }
                        """);
    }

    @Test
    public void arrayMutation() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array[0] = 0;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array2[0];
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array[0];
                        }
                        """);
    }

    @Test
    public void map() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 5),
                """
                        main :: fn() int {
                          values :: map[string]int {"test": 5};
                          return values["test"];
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 5),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                          values :: map[Point]int {{1, 2}: 5};
                          return values[{1,2}];
                        }
                        """);
    }

    @Test
    public void mapMutation() {
        assertProgram(ONE,
                """
                        main :: fn() int {
                          values := map[string]int {"test": 5};
                          values["test"] = 1;
                          return values["test"];
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 5),
                """
                        main :: fn() int {
                          values := map[string]int {"test": 5};
                          values2 := values;
                          values["test"] = 1;
                          return values2["test"];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          values := map[string]int {"test": 5};
                          values2 := values;
                          values["test"] = 1;
                          return values["test"];
                        }
                        """);
    }

    @Test
    public void struct() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point -> Point {1, 2};
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point { Point {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point { return Point {1, 2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point { return Point {.x:1, .y:2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        main :: fn() Point {
                            Point :: struct {x: int, y: int}
                            return Point {1, 2};
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int {
                            Point :: struct {x: int, y: int}
                            point :: Point {1, 2};
                            return point.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int {
                            Point :: struct {x: int, y: int}
                            point :: Point {1, 2};
                            return point.y;
                        }
                        """);

        assertProgram(TWO,
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: fn() Point -> Point {1, 2};
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: fn() Point -> Point {1, 2};
                        Point :: struct {x: int, y: int}
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int {
                            point :: update_point(Point{1, 2});
                            return point.x;
                        }
                        update_point :: fn(point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: int, y: int}
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int {
                            point :: update_point({1, 2});
                            return point.x;
                        }
                        update_point :: fn(point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: int, y: int}
                        """);

        assertProgram(new Value.StringLiteral("test"),
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: int, y: int}
                        main :: fn() string {
                            player :: Player {"test", {1, 2}};
                            return player.name;
                        }
                        """);

        assertProgram(TWO,
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                            player :: Player {"test", {1, 2}};
                            return player.point.y;
                        }
                        """);
    }

    @Test
    public void structMutation() {
        assertProgram(new Value.Struct("Point", Map.of("x", TWO, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
                            value := Point{1, 2};
                            value.x = 2;
                            return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
                            value := Point{1, 2};
                            value2 := value;
                            value.x = 2;
                            return value2.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
                            value := Point{1, 2};
                            value2 := value;
                            value.x = 2;
                            return value.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                            player :: Player {"test", {1, 2}};
                            player.point.x = 2;
                            return player.point.x;
                        }
                        """);

        assertProgram(ONE,
                """
                        Player :: struct {test: map[string]int}
                        main :: fn() Point {
                            value := Player{map[string]int{"test": 1}};
                            value2 := value;
                            value.test["test"] = 2;
                            return value2.test["test"];
                        }
                        """);
        assertProgram(TWO,
                """
                        Player :: struct {test: map[string]int}
                        main :: fn() Point {
                            value := Player{map[string]int{"test": 1}};
                            value2 := value;
                            value.test["test"] = 2;
                            return value.test["test"];
                        }
                        """);
    }

    @Test
    public void enumDecl() {
        assertProgram(ZERO,
                """
                        Direction :: enum {North,South}
                        main :: fn() int -> return Direction.North;
                        """);
        assertProgram(ONE,
                """
                        Direction :: enum {North,South}
                        main :: fn() int -> Direction.South;
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
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
                        Point :: struct {x: int, y: int};
                        main :: fn() int {
                          Component :: union {
                            Point,
                            Velocity :: struct {x: int, y: int},
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: int, y: int};
                        main :: fn() int {
                          Component :: union {
                            Point,
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          Component :: union {
                            Position :: struct {x: int, y: int},
                            Velocity :: struct {x: int, y: int},
                          }
                          value :: Position {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: int, y: int},
                            Velocity :: struct {x: int, y: int},
                        }
                        main :: fn() Component {
                          value :Component: Position {1, 2};
                          return value;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: int, y: int},
                            Velocity :: struct {x: int, y: int},
                        }
                        main :: fn() Component {
                          return Position {1, 2};
                        }
                        """);
    }

    @Test
    public void branch() {
        assertProgram(ONE,
                """
                        main :: fn() int {
                          value := 0;
                          if true -> value = 1;
                          return value;
                        }
                        """);
    }

    @Test
    public void loop() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value := 0;
                          for 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value := 0;
                          for i: 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 45),
                """
                        main :: fn() int {
                          value := 0;
                          for i: 0..10 -> value = value + i;
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int {
                          value := 0;
                          for {
                            break;
                          }
                          return 1;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            break;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value :~ 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x: array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 12),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .y: array -> value = value + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x, .y: array -> value = value + x;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 21),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: fn() int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for p: array -> value = value + p.x;
                          return value;
                        }
                        """);
    }

    @Test
    public void fork() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value :~ 0;
                          fork 0..10 {
                            value = value + 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value :~ 0;
                          fork 0..10 {
                            {value = value + 1;}
                          }
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int {
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
    public void select() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value := 5;
                          select {
                            test :: 10; -> value = test;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: fn() int {
                          value :~ 0;
                          fork 0..10 {
                            select {
                              test :: 10; -> value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 110),
                """
                        main :: fn() int {
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
                        main :: fn() int {
                          for {
                            select {
                              test :: 10; -> break;
                            }
                          }
                          return 1;
                        }
                        """);
    }

    @Test
    public void join() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          join {
                            -> test :: 10;
                            -> test2:: 10;
                          }
                          return 0;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: fn() int {
                          shared :~ 0;
                          join {
                            -> shared = 1;
                            -> shared = 1;
                          }
                          return shared;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          shared :~ 0;
                          lambda :: fn() -> shared = 1;
                          join {
                            -> lambda();
                            -> lambda();
                          }
                          return shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: fn() int {
                          shared :~ 0;
                          lambda :: fn() -> shared = 1;
                          join {
                            -> lambda();
                            -> lambda();
                          }
                          return $shared;
                        }
                        """);
    }

    @Test
    public void spawn() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          something();
                          return 0;
                        }
                        something :: spawn() {
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          stop :~ false;
                          something :: spawn() {
                            stop = true;
                          }
                          something();
                          stop = $stop;
                          return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          number :~ 0;
                          something :: spawn() {
                            number = 1;
                          }
                          something();
                          return number;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          stop :~ false;
                          something :: spawn() {
                            stop = $stop;
                          }
                          something();
                          stop = true;
                          return 0;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: fn() int {
                          number :~ 0;
                          something :: spawn(value: int) {
                            number = value;
                          }
                          something(1);
                          return $number;
                        }
                        """);
    }

    @Test
    public void spawnInline() {
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          spawn {
                          }
                          return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: fn() int {
                          stop :~ false;
                          spawn {
                            stop = true;
                          }
                          stop = $stop;
                          return 0;
                        }
                        """);
    }

    @Test
    public void explicitType() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int,}
                        main :: fn() Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point -> {.x: 1, .y: 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point -> {1, 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
                            value :Point: {1, 2};
                            return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of(
                        "x", new Value.IntegerLiteral(Type.INT, 3), "y", new Value.IntegerLiteral(Type.INT, 4))),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() Point {
                          point :Point = {.x: 1, .y: 2}
                          point = {.x: 3, .y: 4}
                          return point;
                        }
                        """);
    }

    @Test
    public void multiReturn() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 3),
                """
                        Point :: struct {x: int, y: int}
                        main :: fn() int {
                            x, y :: get_point();
                            return x + y;
                        }
                        get_point :: fn() Point { return {1, 2} }
                        """);
    }

    private static void assertProgram(Value expected, String input) {
        assertProgram(expected, "main", input);
    }

    private static void assertProgram(Value expected, String name, String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        var interpreter = new VM(null, statements);
        var actual = interpreter.interpret(name, List.of());
        interpreter.stop();
        assertEquals(expected, actual);
    }
}
