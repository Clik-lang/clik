package org.click;

import org.click.external.BuiltinEx;
import org.click.interpreter.VM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

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

        interpret(statements);
        //compile(statements);
    }

    private static void interpret(List<Ast.Statement> statements) {
        var interpreter = new VM(Path.of("samples"), statements,
                Map.of("Key", new BuiltinEx.IntReader(),
                        "Print", new BuiltinEx.Printer()));
        //var result = interpreter.interpret("main", List.of());
        //System.out.println("Result: " + result);
        interpreter.stop();
    }

    //private static void compile(List<Ast.Statement> statements) {
    //    var compiler = new CCompiler(statements);
    //    final String output = compiler.compile();
    //    // Write to file
    //    try {
    //        Files.createDirectories(Path.of("compiled"));
    //        Files.writeString(Path.of("compiled", "compiled.c"), output);
    //    } catch (IOException e) {
    //        e.printStackTrace();
    //    }
    //}
}
