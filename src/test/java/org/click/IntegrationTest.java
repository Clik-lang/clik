package org.click;

import org.click.interpreter.VM;
import org.click.value.Value;
import org.junit.jupiter.api.Disabled;
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
                        main :: () {
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                            return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                            0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int -> 0;
                        """);

        assertProgram(ZERO, "main_score",
                """
                        main_score :: () int {
                            return 0;
                        }
                        """);
    }

    @Test
    public void comment() {
        assertProgram(ZERO,
                """
                        main :: () int {
                            // This is a comment
                            return 0;
                        }
                        """);
    }

    @Test
    public void param() {
        assertProgram(ONE,
                """
                        main :: () int {
                            return get();
                        }
                        get :: () int -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                            value :: get();
                            return value;
                        }
                        get :: () int -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          return get();
                        }
                        get :: () int -> 1;
                        """);
        assertProgram(ONE,
                """
                        main :: () int -> get();
                        get :: () int -> 1;
                        """);
    }

    @Test
    public void globalShared() {
        assertProgram(ZERO,
                """
                        shared :~ 0;
                        main :: () int {
                          compute();
                          return shared;
                        }
                        compute :: () {
                          shared = 1;
                        }
                        """);
    }

    @Test
    public void numberLiterals() {
        assertProgram(ONE,
                """
                        main :: () int -> 1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 1000),
                """
                        main :: () int -> 1_000;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.I32, 1),
                """
                        main :: () i32 -> 1i32;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.U8, 1),
                """
                        main :: () u8 -> 1u8;
                        """);

        assertProgram(new Value.FloatLiteral(Type.FLOAT, 5.5),
                """
                        main :: () float -> 5.5;
                        """);
        assertProgram(new Value.FloatLiteral(Type.FLOAT, 5000.5),
                """
                        main :: () float -> 5_000.5;
                        """);
        assertProgram(new Value.FloatLiteral(Type.FLOAT, 5000.55),
                """
                        main :: () float -> 5_000.5_5;
                        """);
        assertProgram(new Value.FloatLiteral(Type.F64, 5.5),
                """
                        main :: () f64 -> 5.5f64;
                        """);
        assertProgram(new Value.FloatLiteral(Type.F32, 5.5),
                """
                        main :: () f32 -> 5.5f32;
                        """);

        assertProgram(ONE,
                """
                        main :: () int -> 0x1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: () int -> 0xFF;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: () int -> 0xF_F;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: () int -> 0xfF;
                        """);

        assertProgram(ONE,
                """
                        main :: () int -> 0b1;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: () int -> 0b11111111;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 255),
                """
                        main :: () int -> 0b1111_1111;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 254),
                """
                        main :: () int -> 0b11111110;
                        """);
    }

    @Test
    public void stringLiterals() {
        assertProgram(new Value.StringLiteral("Hello"),
                """
                        main :: () string -> "Hello";
                        """);
        assertProgram(new Value.StringLiteral("\"Hello\""),
                """
                        main :: () string -> "\\"Hello\\"";
                        """);

        assertProgram(new Value.StringLiteral("Hello"),
                """
                        main :: () string -> `Hello`;
                        """);
        assertProgram(new Value.StringLiteral("\"Hello\""),
                """
                        main :: () string -> `"Hello"`;
                        """);
    }

    @Test
    public void runeLiterals() {
        assertProgram(new Value.RuneLiteral("a"),
                """
                        main :: () rune -> 'a';
                        """);
        assertProgram(new Value.RuneLiteral("β"),
                """
                        main :: () rune -> 'β';
                        """);
    }

    @Test
    public void lambda() {
        assertProgram(ONE,
                """
                        main :: () int {
                          function :: () int -> 1;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          number :: 1;
                          function :: () int -> number;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          function :: (arg: int) int -> arg;
                          return function(1);
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          constant :: 5;
                          function :: (arg: int) int -> arg + 5;
                          return function(5);
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          number := 1;
                          function :: () int -> number;
                          number = 0;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                          number :~ 0;
                          function :: () -> number = 1;
                          function();
                          return number;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          number :~ 0;
                          function :: () -> number = 1;
                          function();
                          return $number;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          number :~ 0;
                          function :: () -> number = 1;
                          function();
                          function();
                          return $number;
                        }
                        """);
    }

    @Test
    public void lambdaParam() {
        assertProgram(ONE,
                """
                        main :: () int {
                          function :: () int -> 1;
                          forward :: (function: () int) int -> function();
                          value :: forward(function);
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 11),
                """
                        main :: () int {
                          add :: (a: int, b: int) int -> a + b;
                          forward :: (a: int, b: int, function: (c: int, d: int) int) int -> function(a, b);
                          value :: forward(5, 6, add);
                          return value;
                        }
                        """);
    }

    @Test
    public void mathInteger() {
        assertProgram(ONE,
                """
                        main :: () int -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: () int -> 1 + 1;
                        """);

        assertProgram(TWO,
                """
                        main :: () int {
                            value := 1;
                            value = value + 1;
                            return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 14),
                """
                        main :: () int -> 2 + 3 * 4;
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 20),
                """
                        main :: () int -> (2 + 3) * 4;
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
        assertProgram(ZERO,
                """
                        main :: () int {
                          array :: [1]int;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          array :: [5]int {1, 2, 3, 4, 5};
                          return array[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array = [5]int {2, 3, 4, 5, 6};
                          return array[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array = {2, 3, 4, 5, 6};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          array :: [5]int {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          array :[5]int: [5]int {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(new Value.StringLiteral("Hello"),
                """
                        main :: () string {
                          array :: [2]string {"Hello", "World"};
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
                          array :: [1]Point {
                            Point {.x: 1, .y: 2},
                          }
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
                          array :: [1]Point {{1,2}}
                          return array[0];
                        }
                        """);
    }

    @Test
    public void range() {
        assertProgram(ONE,
                """
                        main :: () int {
                          array :[10]int: 0..10;
                          return array[1];
                        }
                        """);
    }

    @Test
    public void arrayLazy() {
        assertProgram(new Value.Array(new Type.Array(Type.INT, 5), List.of(
                        new Value.IntegerLiteral(Type.INT, 1),
                        new Value.IntegerLiteral(Type.INT, 2),
                        new Value.IntegerLiteral(Type.INT, 3),
                        new Value.IntegerLiteral(Type.INT, 4),
                        new Value.IntegerLiteral(Type.INT, 5))
                ),
                """
                        main :: () []int {
                          array := [5]int @ + 1;
                          return array;
                        }
                        """);
    }

    @Test
    public void arrayFilter() {
        assertProgram(new Value.Array(new Type.Array(Type.INT, 3), List.of(
                        new Value.IntegerLiteral(Type.INT, 3),
                        new Value.IntegerLiteral(Type.INT, 4),
                        new Value.IntegerLiteral(Type.INT, 5))
                ),
                """
                        main :: () []int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array = array where @ > 2;
                          return array;
                        }
                        """);
    }

    @Test
    public void arrayMutation() {
        assertProgram(ZERO,
                """
                        main :: () int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array[0] = 0;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array2[0];
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                          array := [5]int {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array[0];
                        }
                        """);
    }

    @Test
    @Disabled
    public void arrayTransmute() {
        assertProgram(new Value.IntegerLiteral(Type.I16, 256),
                """
                        main :: () int {
                          array := [2]i8 {0b00000000i8 ,0b00000001i8};
                          return array[0]i16;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.I16, 256),
                """
                        main :: () int {
                          array := [3]i8 {0i8, 0b00000000i8 ,0b00000001i8};
                          return array[1]i16;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.I16, 256),
                """
                        main :: () int {
                          array := [2]i8 {0i8, 0i8};
                          array[0]i16 = 256i16;
                          return array[0]i16;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.I16, 256),
                """
                        main :: () int {
                          array := [3]i8 {0i8, 0i8, 0i8};
                          array[1]i16 = 256i16;
                          return array[1]i16;
                        }
                        """);
    }

    @Test
    public void struct() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point -> Point {1, 2};
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point { Point {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point { return Point {1, 2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point { return Point {.x:1, .y:2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        main :: () Point {
                            Point :: struct {x: int, y: int}
                            return Point {1, 2};
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () int {
                            Point :: struct {x: int, y: int}
                            point :: Point {1, 2};
                            return point.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                            Point :: struct {x: int, y: int}
                            point :: Point {1, 2};
                            return point.y;
                        }
                        """);

        assertProgram(TWO,
                """
                        Point :: struct {x: int, y: int}
                        main :: () int {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: () Point -> Point {1, 2};
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                            point :: get_point();
                            return point.y;
                        }
                        get_point :: () Point -> Point {1, 2};
                        Point :: struct {x: int, y: int}
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                            point :: update_point(Point{1, 2});
                            return point.x;
                        }
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: int, y: int}
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                            point :: update_point({1, 2});
                            return point.x;
                        }
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        Point :: struct {x: int, y: int}
                        """);

        assertProgram(new Value.StringLiteral("test"),
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: int, y: int}
                        main :: () string {
                            player :: Player {"test", {1, 2}};
                            return player.name;
                        }
                        """);

        assertProgram(TWO,
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: int, y: int}
                        main :: () int {
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
                        main :: () Point {
                            value := Point{1, 2};
                            value.x = 2;
                            return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
                            value := Point{1, 2};
                            value2 := value;
                            value.x = 2;
                            return value2.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
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
                        main :: () int {
                            player :: Player {"test", {1, 2}};
                            player.point.x = 2;
                            return player.point.x;
                        }
                        """);

        assertProgram(ONE,
                """
                        Player :: struct {test: [1]int}
                        main :: () Point {
                            value := Player{[1]int{1}};
                            value2 := value;
                            value.test[0] = 2;
                            return value2.test[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        Player :: struct {test: [1]int}
                        main :: () Point {
                            value := Player{[1]int{1}};
                            value2 := value;
                            value.test[0] = 2;
                            return value.test[0];
                        }
                        """);
    }

    @Test
    public void enumDecl() {
        assertProgram(ZERO,
                """
                        Direction :: enum {North,South}
                        main :: () int -> return Direction.North;
                        """);
        assertProgram(ONE,
                """
                        Direction :: enum {North,South}
                        main :: () int -> Direction.South;
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
                          Component :: enum Point {
                            Position :: {.x: 1, .y: 2},
                          }
                          return Component.Position;
                        }
                        """);
        assertProgram(new Value.Enum("State", "LOGIN"),
                """
                        State :: enum {LOGIN, PLAY}
                        Player :: struct {state: State}
                        main :: () State {
                          player :: Player {State.LOGIN};
                          return player.state;
                        }
                        """);
        assertProgram(new Value.Enum("Test", "SECOND"),
                """
                        Point :: struct {x: int, y: int}
                        Test :: enum Point {
                          FIRST :: {1, 2},
                          SECOND :: {3, 4},
                        }
                        main :: () Test {
                          return Test.SECOND;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        Test :: enum Point {
                          FIRST :: {1, 2},
                          SECOND :: {3, 4},
                        }
                        main :: () Point {
                          return Test.FIRST;
                        }
                        """);
        assertProgram(new Value.Enum("Test", "FIRST"),
                """
                        Point :: struct {x: int, y: int}
                        Test :: enum Point {
                          FIRST :: {1, 2},
                          SECOND :: {3, 4},
                        }
                        Player :: struct {test: Test}
                        main :: () Test {
                          player :: Player {Test.FIRST};
                          return player.test;
                        }
                        """);
    }

    @Test
    public void union() {
        assertProgram(ONE,
                """
                        Point :: struct {x: int, y: int};
                        main :: () int {
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
                        main :: () int {
                          Component :: union {
                            Point,
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
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
                        main :: () Component {
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
                        main :: () Component {
                          return Position {1, 2};
                        }
                        """);
    }

    @Test
    public void branch() {
        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          if true {
                            value = 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          if true -> value = 1;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          if true value = 1;
                          else value = 2;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          if true {
                            value = 1;
                          } else {
                            value = 2;
                          }
                          return value;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          value := 0;
                          if false value = 1;
                          else value = 2;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          if !false {
                            value = 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value :: true;
                          if value {
                            return 1;
                          }
                          return 2;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          value :: false;
                          if !value {
                            return 1;
                          }
                          return 2;
                        }
                        """);
    }

    @Test
    public void loop() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value := 0;
                          for 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value := 0;
                          for i: 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 45),
                """
                        main :: () int {
                          value := 0;
                          for i: 0..10 -> value = value + i;
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () int {
                          value := 0;
                          for {
                            break;
                          }
                          return 1;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
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
                        main :: () int {
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
                        main :: () int {
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
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x: array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 12),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .y: array -> value = value + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x, .y: array -> value = value + x;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 21),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 9),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for p: array -> value = value + p.x;
                          return value;
                        }
                        """);
    }

    @Test
    public void selectStmt() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value := 5;
                          select {
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 6),
                """
                        main :: () int {
                          value := 5;
                          select {
                            {
                              value = 10;
                              if value == 10 value = 6;
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 6),
                """
                        main :: () int {
                          value := 5;
                          select {
                            {
                              value = 10;
                              if true {
                                value = 6;
                              }
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          for {
                            select {
                              -> break;
                            }
                          }
                          return 1;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value := 5;
                          select {
                            -> value = 10;
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :~ 5;
                          select {
                            -> value = 10;
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :~ 5;
                          select {
                            -> value = 10;
                            -> value = $value;
                          }
                          return value;
                        }
                        """);
    }

    @Test
    public void selectExpr() {
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :: select {
                            -> 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :int: select {
                            -> 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        main :: () int {
                          Point :: struct {x: int, y: int}
                          value :Point: select {
                            -> {1, 2};
                          }
                          return value;
                        }
                        """);
    }

    @Test
    public void join() {
        assertProgram(ZERO,
                """
                        main :: () int {
                          join {
                            spawn test :: 10;
                            spawn test2 :: 10;
                          }
                          return 0;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          shared :~ 0;
                          join {
                            spawn shared = 1;
                            spawn shared = 1;
                          }
                          return shared;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                          shared :~ 0;
                          lambda :: () -> shared = 1;
                          join {
                            spawn lambda();
                            spawn lambda();
                          }
                          return shared;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          shared :~ 0;
                          lambda :: () -> shared = 1;
                          join {
                            spawn lambda();
                            spawn lambda();
                          }
                          return $shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          stop :~ false;
                          join {
                            spawn stop = $stop;
                            spawn stop = $stop;
                            spawn stop = true;
                          }
                          return 1;
                        }
                        """);

        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :~ 0;
                          join {
                            for 0.. 10 {
                              value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.IntegerLiteral(Type.INT, 10),
                """
                        main :: () int {
                          value :~ 0;
                          join {
                            for 0.. 10 {
                              spawn value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);
    }

    @Test
    public void spawn() {
        assertProgram(ZERO,
                """
                        main :: () int {
                          spawn {
                          }
                          return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
                          shared :~ 0;
                          spawn shared = 1;
                          return shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () int {
                          shared :~ 0;
                          spawn shared = 1;
                          return $shared;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () int {
                          shared :~ 0;
                          shared = 1;
                          spawn shared = shared + 1;
                          return $shared;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () int {
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
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int,}
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point -> {.x: 1, .y: 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point -> {1, 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
                            value :Point: {1, 2};
                            return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of(
                        "x", new Value.IntegerLiteral(Type.INT, 3), "y", new Value.IntegerLiteral(Type.INT, 4))),
                """
                        Point :: struct {x: int, y: int}
                        main :: () Point {
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
                        main :: () int {
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
        var interpreter = new VM(null, statements);
        var actual = interpreter.interpret(name, List.of());
        interpreter.stop();
        assertEquals(expected, actual);
    }
}
