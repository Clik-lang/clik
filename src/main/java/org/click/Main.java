package org.click;

import org.click.interpreter.VM;
import org.click.value.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class Main {
    public static void main(String[] args) throws IOException {
        final String input = Files.readAllLines(Path.of("samples", "hello.cl"))
                .stream().reduce("", (a, b) -> a + b + '\n');
        var tokens = new Scanner(input).scanTokens();
        System.out.println("Tokens:");
        for (var token : tokens) System.out.println(token);

        var statements = new Parser(tokens).parse();
        System.out.println("Statements:");
        for (var statement : statements) System.out.println(statement);

        AtomicReference<Value> ref = new AtomicReference<>(new Value.NumberLiteral(Type.INT, 0));

        Thread.startVirtualThread(() -> {
            // Read integer from console
            var scanner = new java.util.Scanner(System.in);
            while (true) {
                ref.set(new Value.NumberLiteral(Type.INT, scanner.nextInt()));
            }
        });

        var interpreter = new VM(Path.of("samples"), statements,
                Map.of(),
                Map.of("key", ref),
                Map.of("print", System.out::println));
        //var result = interpreter.interpret("main", List.of());
        //System.out.println("Result: " + result);
        interpreter.stop();
    }
}
