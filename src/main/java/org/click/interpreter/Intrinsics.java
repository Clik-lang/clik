package org.click.interpreter;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.util.Map.entry;

public final class Intrinsics {
    private static final Map<String, BiFunction<Executor, List<Value>, Value>> FUNCTIONS = Map.ofEntries(
            entry("print", Intrinsics::print),
            entry("sleep", Intrinsics::sleep),
            entry("open_server", Intrinsics::openServer),
            entry("accept_client", Intrinsics::acceptClient),
            entry("recv", Intrinsics::recv),
            entry("send", Intrinsics::send),
            entry("close", Intrinsics::close)
    );

    public static Value evaluate(Executor executor, String name, List<Value> evaluated) {
        final BiFunction<Executor, List<Value>, Value> builtin = FUNCTIONS.get(name);
        if (builtin == null) throw new RuntimeException("Function not found: " + name);
        return builtin.apply(executor, evaluated);
    }

    public static Value print(Executor executor, List<Value> evaluated) {
        StringBuilder builder = new StringBuilder();
        for (Value value : evaluated) {
            final String serialized = ValueSerializer.serialize(executor.walker(), value);
            builder.append(serialized);
        }
        System.out.println(builder);
        return null;
    }

    public static Value sleep(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.IntegerLiteral integerLiteral)) {
            throw new RuntimeException("Expected integer, got " + value);
        }
        try {
            Thread.sleep(integerLiteral.value());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Value openServer(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.IntegerLiteral integerLiteral)) {
            throw new RuntimeException("Expected integer, got " + value);
        }
        // Open http server
        try {
            final long port = integerLiteral.value();
            ServerSocket serverSocket = new ServerSocket((int) port);
            return new Value.JavaObject(serverSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value acceptClient(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof ServerSocket serverSocket)) {
            throw new RuntimeException("Expected server, got " + value);
        }
        try {
            var clientSocket = serverSocket.accept();
            return new Value.JavaObject(clientSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value recv(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 2) throw new RuntimeException("Expected 2 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected socket, got " + value);
        }
        final Value sizeValue = evaluated.get(1);
        if (!(sizeValue instanceof Value.IntegerLiteral integerLiteral)) {
            throw new RuntimeException("Expected integer, got " + sizeValue);
        }
        try {
            final byte[] buffer = new byte[(int) integerLiteral.value()];
            final int length = socket.getInputStream().read(buffer);
            final String string = new String(buffer, 0, length, StandardCharsets.UTF_8);
            return new Value.StringLiteral(string);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value send(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 2) throw new RuntimeException("Expected 2 arguments, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        final Value message = evaluated.get(1);
        if (!(message instanceof Value.StringLiteral stringLiteral)) {
            throw new RuntimeException("Expected string, got " + message);
        }
        try {
            final byte[] bytes = stringLiteral.value().getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Value close(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        try {
            socket.getOutputStream().close();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }
}
