package org.click;

import org.click.interpreter.Interpreter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        final String input = Files.readString(Path.of("samples", "hello.cl"));
        var tokens = new Scanner(input).scanTokens();
        System.out.println("Tokens:");
        for (var token : tokens) System.out.println(token);

        var statements = new Parser(tokens).parse();
        System.out.println("Statements:");
        for (var statement : statements) System.out.println(statement);

        var interpreter = new Interpreter(statements);
        interpreter.interpret("main", List.of());
        interpreter.stop();
    }
}