package org.click;

import org.click.external.ExternalFunction;
import org.click.interpreter.VM;
import org.click.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class IntegrationTest {
    private static final Value TRUE = new Value.BooleanLiteral(true);
    private static final Value FALSE = new Value.BooleanLiteral(false);

    private static final Value ZERO = new Value.NumberLiteral("0");
    private static final Value ONE = new Value.NumberLiteral("1");
    private static final Value TWO = new Value.NumberLiteral("2");

    @Test
    public void functionBlock() {
        assertProgram(null,
                """
                        main :: () {
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
                            return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
                            0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number -> 0;
                        """);
    }

    @Test
    public void comment() {
        assertProgram(ZERO,
                """
                        main :: () number {
                            // This is a comment
                            return 0;
                        }
                        """);
    }

    @Test
    public void param() {
        assertProgram(ONE,
                """
                        get :: () number -> 1;
                        main :: () number {
                            return get();
                        }
                        """);
        assertProgram(ONE,
                """
                        get :: () number -> 1;
                        main :: () number {
                            value :: get();
                            return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        get :: () number -> 1;
                        main :: () number {
                          return get();
                        }
                        """);
        assertProgram(ONE,
                """
                        get :: () number -> 1;
                        main :: () number -> get();
                        """);
    }

    @Test
    public void globalShared() {
        assertProgram(ZERO,
                """
                        shared :~ 0;
                        compute :: () {
                          shared = 1;
                        }
                        main :: () number {
                          compute();
                          return shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        shared :~ 0;
                        compute :: () {
                          shared = 1;
                        }
                        main :: () number {
                          compute();
                          return $shared;
                        }
                        """);
    }

    @Test
    public void numberLiterals() {
        assertProgram(ONE,
                """
                        main :: () number -> 1;
                        """);
        assertProgram(new Value.NumberLiteral("1000"),
                """
                        main :: () number -> 1_000;
                        """);

        assertProgram(new Value.NumberLiteral("5.5"),
                """
                        main :: () number -> 5.5;
                        """);
        assertProgram(new Value.NumberLiteral("5000.5"),
                """
                        main :: () number -> 5_000.5;
                        """);
        assertProgram(new Value.NumberLiteral("5000.55"),
                """
                        main :: () number -> 5_000.5_5;
                        """);

        assertProgram(ONE,
                """
                        main :: () number -> 0x1;
                        """);
        assertProgram(new Value.NumberLiteral("255"),
                """
                        main :: () number -> 0xFF;
                        """);
        assertProgram(new Value.NumberLiteral("255"),
                """
                        main :: () number -> 0xF_F;
                        """);
        assertProgram(new Value.NumberLiteral("255"),
                """
                        main :: () number -> 0xfF;
                        """);

        assertProgram(ONE,
                """
                        main :: () number -> 0b1;
                        """);
        assertProgram(new Value.NumberLiteral("255"),
                """
                        main :: () number -> 0b11111111;
                        """);
        assertProgram(new Value.NumberLiteral("255"),
                """
                        main :: () number -> 0b1111_1111;
                        """);
        assertProgram(new Value.NumberLiteral("254"),
                """
                        main :: () number -> 0b11111110;
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
    public void stringConcatenate() {
        assertProgram(new Value.StringLiteral("Hello 5"),
                """
                        main :: () string -> "Hello " + 5;
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
                        main :: () number {
                          function :: () number -> 1;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          number :: 1;
                          function :: () number -> number;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          function :: (arg: number) number -> arg;
                          return function(1);
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          constant :: 5;
                          function :: (arg: number) number -> arg + 5;
                          return function(5);
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          number := 1;
                          function :: () number -> number;
                          number = 0;
                          value :: function();
                          return value;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
                          number :~ 0;
                          function :: () -> number = 1;
                          function();
                          return number;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          number :~ 0;
                          function :: () -> number = 1;
                          function();
                          return $number;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
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
                        main :: () number {
                          function :: () number -> 1;
                          forward :: (function: () number) number -> function();
                          value :: forward(function);
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("11"),
                """
                        main :: () number {
                          add :: (a: number, b: number) number -> a + b;
                          forward :: (a: number, b: number, function: (c: number, d: number) number) number -> function(a, b);
                          value :: forward(5, 6, add);
                          return value;
                        }
                        """);
    }

    @Test
    public void globalLambda() {
        assertProgram(TWO,
                """
                        number := 1;
                        number = 2;
                        main :: () number {
                          return number;
                        }
                        """);
        assertProgram(ONE,
                """
                        number := 1;
                        main :: () number {
                          return number;
                        }
                        number = 2;
                        """);
    }

    @Test
    public void external() {
        assertProgram(TWO,
                Map.of("get", values -> TWO),
                """
                        get :: () number;
                        main :: () number {
                          return get();
                        }
                        """);
    }

    @Test
    public void mathInteger() {
        assertProgram(ONE,
                """
                        main :: () number -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: () number -> 1 + 1;
                        """);

        assertProgram(TWO,
                """
                        main :: () number {
                            value := 1;
                            value = value + 1;
                            return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("14"),
                """
                        main :: () number -> 2 + 3 * 4;
                        """);
        assertProgram(new Value.NumberLiteral("20"),
                """
                        main :: () number -> (2 + 3) * 4;
                        """);
    }

    @Test
    public void mathFloat() {
        assertProgram(new Value.NumberLiteral("0.3"),
                """
                        main :: () number -> 0.2 + 0.1;
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
                        main :: () number {
                          array :: [1]number;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          array :: [5]number {1, 2, 3, 4, 5};
                          return array[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () number {
                          array := [5]number {1, 2, 3, 4, 5};
                          array = [5]number {2, 3, 4, 5, 6};
                          return array[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () number {
                          array := [5]number {1, 2, 3, 4, 5};
                          array = {2, 3, 4, 5, 6};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          array :: [5]number {1, 2, 3, 4, 5,};
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          array :[5]number: [5]number {1, 2, 3, 4, 5,};
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
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                          array :: [1]Point {
                            Point {.x: 1, .y: 2},
                          }
                          return array[0];
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
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
                        main :: () number {
                          array :[10]number'n: 0..10;
                          return array[1];
                        }
                        """);
    }

    @Test
    public void arrayLazy() {
        assertProgram(new Value.Array(new Type.Array(Type.NUMBER, 5), List.of(
                        new Value.NumberLiteral("1"),
                        new Value.NumberLiteral("2"),
                        new Value.NumberLiteral("3"),
                        new Value.NumberLiteral("4"),
                        new Value.NumberLiteral("5"))
                ),
                """
                        main :: () [5]number {
                          array := [5]number @ + 1;
                          return array;
                        }
                        """);
    }

    @Test
    public void arrayFilter() {
        assertProgram(new Value.Array(new Type.Array(Type.NUMBER, -1), List.of(
                        new Value.NumberLiteral("3"),
                        new Value.NumberLiteral("4"),
                        new Value.NumberLiteral("5"))
                ),
                """
                        main :: () []number {
                          array := [5]number {1, 2, 3, 4, 5};
                          filtered :: array where @ > 2;
                          return filtered;
                        }
                        """);
        assertProgram(new Value.Array(new Type.Array(Type.of("Point"), -1), List.of(
                        new Value.Struct("Point", Map.of("x", new Value.NumberLiteral("3"), "y", new Value.NumberLiteral("4"))),
                        new Value.Struct("Point", Map.of("x", new Value.NumberLiteral("5"), "y", new Value.NumberLiteral("6")))
                )),
                """
                        Point :: struct {x: number, y: number}
                        main :: () []Point {
                          array := [3]Point {{1,2}, {3,4}, {5,6}};
                          filtered :: array where @.x > 2;
                          return filtered;
                        }
                        """);
    }

    @Test
    public void arrayMutation() {
        assertProgram(ZERO,
                """
                        main :: () number {
                          array := [5]number {1, 2, 3, 4, 5};
                          array[0] = 0;
                          return array[0];
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          array := [5]number {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array2[0];
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
                          array := [5]number {1, 2, 3, 4, 5};
                          array2 := array;
                          array[0] = 0;
                          return array[0];
                        }
                        """);
    }

    @Test
    public void struct() {
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point -> Point {1, 2};
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point { Point {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point { return Point {1, 2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point { return Point {.x:1, .y:2} }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        main :: () Point {
                            Point :: struct {x: number, y: number}
                            return Point {1, 2};
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () number {
                            Point :: struct {x: number, y: number}
                            point :: Point {1, 2};
                            return point.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () number {
                            Point :: struct {x: number, y: number}
                            point :: Point {1, 2};
                            return point.y;
                        }
                        """);

        assertProgram(TWO,
                """
                        Point :: struct {x: number, y: number}
                        get_point :: () Point -> Point {1, 2};
                        main :: () number {
                            point :: get_point();
                            return point.y;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: number, y: number}
                        get_point :: () Point -> Point {1, 2};
                        main :: () number {
                            point :: get_point();
                            return point.y;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: number, y: number}
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        main :: () number {
                            point :: update_point(Point{1, 2});
                            return point.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: number, y: number}
                        update_point :: (point: Point) Point -> {point.x + 1, point.y};
                        main :: () number {
                            point :: update_point({1, 2});
                            return point.x;
                        }
                        """);

        assertProgram(new Value.StringLiteral("test"),
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: number, y: number}
                        main :: () string {
                            player :: Player {"test", {1, 2}};
                            return player.name;
                        }
                        """);

        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                            player := Player {"test", {3, 4}};
                            player.point = {1, 2};
                            return player.point;
                        }
                        """);

        assertProgram(TWO,
                """
                        Player :: struct {name: string, point: Point}
                        Point :: struct {x: number, y: number}
                        main :: () number {
                            player :: Player {"test", {1, 2}};
                            return player.point.y;
                        }
                        """);
    }

    @Test
    public void structMutation() {
        assertProgram(new Value.Struct("Point", Map.of("x", TWO, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                            value := Point{1, 2};
                            value.x = 2;
                            return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                            value := Point{1, 2};
                            value2 := value;
                            value.x = 2;
                            return value2.x;
                        }
                        """);
        assertProgram(TWO,
                """
                        Point :: struct {x: number, y: number}
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
                        Point :: struct {x: number, y: number}
                        main :: () number {
                            player :: Player {"test", {1, 2}};
                            player.point.x = 2;
                            return player.point.x;
                        }
                        """);

        assertProgram(ONE,
                """
                        Player :: struct {test: [1]number}
                        main :: () Point {
                            value := Player{[1]number{1}};
                            value2 := value;
                            value.test[0] = 2;
                            return value2.test[0];
                        }
                        """);
        assertProgram(TWO,
                """
                        Player :: struct {test: [1]number}
                        main :: () Point {
                            value := Player{[1]number{1}};
                            value2 := value;
                            value.test[0] = 2;
                            return value.test[0];
                        }
                        """);
    }

    //@Test
    public void typeConstraint() {
        assertProgram(TWO,
                """
                        positive_int :: number where @ > 0;
                        main :: () positive_int {
                          return 2;
                        }
                        """);
    }

    @Test
    public void binary() {
        assertProgram(ONE,
                """
                        main :: () number {
                          text :: UTF8."Some text";
                          return 1;
                        }
                        """);
        assertProgram(new Value.Binary("I32", "00000000 00000000 00000001 11110100"),
                """
                        main :: () I32 {
                          return I32.500;
                        }
                        """);
        assertProgram(new Value.Binary("UTF8", "00100010 01001000 01100101 01101100 01101100 01101111 00100010"),
                """
                        main :: () UTF8 {
                          return UTF8."Hello";
                        }
                        """);
    }

    @Test
    public void enumDecl() {
        assertProgram(ZERO,
                """
                        Direction :: enum {North,South}
                        main :: () number -> return Direction.North;
                        """);
        assertProgram(ONE,
                """
                        Direction :: enum {North,South}
                        main :: () number -> Direction.South;
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
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
                        Point :: struct {x: number, y: number}
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
                        Point :: struct {x: number, y: number}
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
                        Point :: struct {x: number, y: number}
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
                        Point :: struct {x: number, y: number};
                        main :: () number {
                          Component :: union {
                            Point,
                            Velocity :: struct {x: number, y: number},
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        Point :: struct {x: number, y: number};
                        main :: () number {
                          Component :: union {
                            Point,
                          }
                          value :: Point {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          Component :: union {
                            Position :: struct {x: number, y: number},
                            Velocity :: struct {x: number, y: number},
                          }
                          value :: Position {.x: 1, .y: 2};
                          return value.x;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: number, y: number},
                            Velocity :: struct {x: number, y: number},
                        }
                        main :: () Component {
                          value :Component: Position {1, 2};
                          return value;
                        }
                        """);

        assertProgram(new Value.Union("Component", new Value.Struct("Position", Map.of("x", ONE, "y", TWO))),
                """
                        Component :: union {
                            Position :: struct {x: number, y: number},
                            Velocity :: struct {x: number, y: number},
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
                        main :: () number {
                          value := 0;
                          if true {
                            value = 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          value := 0;
                          if true -> value = 1;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          value := 0;
                          if true value = 1;
                          else value = 2;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
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
                        main :: () number {
                          value := 0;
                          if false value = 1;
                          else value = 2;
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          value := 0;
                          if !false {
                            value = 1;
                          }
                          return value;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          value :: true;
                          if value {
                            return 1;
                          }
                          return 2;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
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
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value := 0;
                          for 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value := 0;
                          for i: 0..10 -> value = value + 1;
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("45"),
                """
                        main :: () number {
                          value := 0;
                          for i: 0..10 -> value = value + i;
                          return value;
                        }
                        """);

        assertProgram(ONE,
                """
                        main :: () number {
                          value := 0;
                          for {
                            break;
                          }
                          return 1;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            break;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value := 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value :~ 0;
                          for 0..10 {
                            value = value + 1;
                            continue;
                          }
                          return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("9"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x: array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("12"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .y: array -> value = value + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("9"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for .x, .y: array -> value = value + x;
                          return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("9"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x;
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("21"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for (.x, .y): array -> value = value + x + y;
                          return value;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("9"),
                """
                        main :: () number {
                          Point :: struct {x: number, y: number}
                          array :: [3]Point {{1,2}, {3,4}, {5,6}}
                          value := 0;
                          for p: array -> value = value + p.x;
                          return value;
                        }
                        """);
    }

    @Test
    public void selectStmt() {
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value := 5;
                          select {
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("6"),
                """
                        main :: () number {
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
        assertProgram(new Value.NumberLiteral("6"),
                """
                        main :: () number {
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
                        main :: () number {
                          for {
                            select {
                              -> break;
                            }
                          }
                          return 1;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value := 5;
                          select {
                            -> value = 10;
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value :~ 5;
                          select {
                            -> value = 10;
                            -> value = 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
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
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value :: select {
                            -> 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value :number: select {
                            -> 10;
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        main :: () Point {
                          Point :: struct {x: number, y: number}
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
                        main :: () number {
                          join {
                            spawn test :: 10;
                            spawn test2 :: 10;
                          }
                          return 0;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () number {
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
                        main :: () number {
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
                        main :: () number {
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
                        main :: () number {
                          stop :~ false;
                          join {
                            spawn stop = $stop;
                            spawn stop = $stop;
                            spawn stop = true;
                          }
                          return 1;
                        }
                        """);

        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
                          value :~ 0;
                          join {
                            for 0.. 10 {
                              value = value + 1;
                            }
                          }
                          return value;
                        }
                        """);
        assertProgram(new Value.NumberLiteral("10"),
                """
                        main :: () number {
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
                        main :: () number {
                          spawn {
                          }
                          return 0;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
                          shared :~ 0;
                          spawn shared = 1;
                          return shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          shared :~ 0;
                          spawn shared = 1;
                          return $shared;
                        }
                        """);
        assertProgram(ONE,
                """
                        main :: () number {
                          shared :~ 1;
                          spawn shared = 1;
                          return $shared;
                        }
                        """);
        assertProgram(TWO,
                """
                        main :: () number {
                          shared :~ 0;
                          shared = 1;
                          spawn shared = shared + 1;
                          return $shared;
                        }
                        """);
        assertProgram(ZERO,
                """
                        main :: () number {
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
                        Point :: struct {x: number, y: number}
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number,}
                        main :: () Point { return {1, 2} }
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point -> {.x: 1, .y: 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point -> {1, 2}
                        """);
        assertProgram(new Value.Struct("Point", Map.of("x", ONE, "y", TWO)),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                            value :Point: {1, 2};
                            return value;
                        }
                        """);
        assertProgram(new Value.Struct("Point", Map.of(
                        "x", new Value.NumberLiteral("3"), "y", new Value.NumberLiteral("4"))),
                """
                        Point :: struct {x: number, y: number}
                        main :: () Point {
                          point :Point = {.x: 1, .y: 2}
                          point = {.x: 3, .y: 4}
                          return point;
                        }
                        """);
    }

    @Test
    public void multiReturn() {
        assertProgram(new Value.NumberLiteral("3"),
                """
                        Point :: struct {x: number, y: number}
                        get_point :: () Point { return {1, 2} }
                        main :: () number {
                            x, y :: get_point();
                            return x + y;
                        }
                        """);
    }

    private static void assertProgram(Value expected, String input) {
        assertProgram(expected, Map.of(), input);
    }

    private static void assertProgram(Value expected, Map<String, ExternalFunction> externals, String input) {
        var tokens = new Scanner(input).scanTokens();
        var statements = new Parser(tokens).parse();
        var interpreter = new VM(null, statements, externals);
        var actual = interpreter.interpret("main", List.of());
        interpreter.stop();
        if (actual instanceof Value.NumberLiteral numberLiteral1 && expected instanceof Value.NumberLiteral numberLiteral2) {
            // TODO: check number type
            assertEquals(numberLiteral1.value(), numberLiteral2.value());
        } else {
            assertEquals(expected, actual);
        }
    }
}
