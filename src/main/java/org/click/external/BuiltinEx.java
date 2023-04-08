package org.click.external;

import org.click.value.Value;
import org.click.value.ValueSerializer;

import java.util.Arrays;
import java.util.Scanner;

public final class BuiltinEx {
    public static final class Printer implements ExternalFunction {
        @Override
        public Value run(Value... args) {
            final String value = Arrays.stream(args).map(ValueSerializer::serialize)
                    .reduce((a, b) -> a + " " + b).orElse("");
            System.out.println(value);
            return null;
        }
    }

    public static final class IntReader implements ExternalFunction {
        private final Scanner scanner = new Scanner(System.in);

        @Override
        public Value run(Value... args) {
            final int value = scanner.nextInt();
            return new Value.NumberLiteral(value);
        }
    }
}
