package org.click;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        final String input = Files.readAllLines(Path.of("samples", "hello.cl"))
                .stream().reduce("", (a, b) -> a + b + '\n');
        var tokens = new Scanner(input).scanTokens();
        //System.out.println("Tokens:");
        //for (var token : tokens) System.out.println(token);

        var statements = new Parser(tokens).parse();
        //System.out.println("Statements:");
        //for (var statement : statements) System.out.println(statement);

        var program = new Compiler(statements).compile();
        System.out.println("Variables: " + program.variables());
        System.out.println("Structs: " + program.structs());
        System.out.println("Functions: " + program.functions());

        var result = new Interpreter(program).interpret("main", List.of());
        System.out.println("Result: " + result);
    }
}
