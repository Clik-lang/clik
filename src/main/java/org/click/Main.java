package org.click;

import org.click.external.BuiltinEx;
import org.click.external.ExternalFunction;
import org.click.interpreter.VM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        final String input = Files.readAllLines(Path.of("samples", "web_server.cl"))
                .stream().reduce("", (a, b) -> a + b + '\n');
        var tokens = new Scanner(input).scanTokens();
        //System.out.println("Tokens:");
        for (var token : tokens) System.out.println(token);

        var statements = new Parser(tokens).parse();
        //System.out.println("Statements:");
        for (var statement : statements) System.out.println(statement);

        interpret(statements);
        //compile(statements);
    }

    private static void interpret(List<Ast.Statement> statements) {
        var externals = webExternals();
        var interpreter = new VM(Path.of("samples"), statements, externals);
        var result = interpreter.interpret("main", List.of());
        //System.out.println("Result: " + result);
        interpreter.stop();
    }

    private static Map<String, ExternalFunction> proxyExternals() {
        var openServer = new BuiltinEx.OpenServer();
        var acceptClient = new BuiltinEx.AcceptClient(openServer.servers());
        var connectServer = new BuiltinEx.ConnectServer();

        return Map.of(
                "print", new BuiltinEx.Printer(),
                "open_server", openServer,
                "accept_client", acceptClient,
                "connect_server", connectServer
        );
    }

    private static Map<String, ExternalFunction> webExternals() {
        var openServer = new BuiltinEx.OpenServer();
        var acceptClient = new BuiltinEx.AcceptClient(openServer.servers());
        var send = new BuiltinEx.Send(acceptClient.sockets());
        var close = new BuiltinEx.Close(acceptClient.sockets());

        return Map.of(
                "print", new BuiltinEx.Printer(),
                "open_server", openServer,
                "accept_client", acceptClient,
                "send", send,
                "close", close
        );
    }

    private static Map<String, ExternalFunction> helloExternals() {
        return Map.of(
                "Key", new BuiltinEx.IntReader(),
                "Print", new BuiltinEx.Printer()
        );
    }
}
