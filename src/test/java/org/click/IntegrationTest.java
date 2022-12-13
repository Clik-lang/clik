package org.click;

import org.click.interpreter.Interpreter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class IntegrationTest {

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
    public void math() {
        assertProgram(ONE,
                """
                        main :: () -> 1;
                        """);
        assertProgram(TWO,
                """
                        main :: () -> 1 + 1;
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
