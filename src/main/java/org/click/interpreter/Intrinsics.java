package org.click.interpreter;

import org.click.Type;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static java.util.Map.entry;

public final class Intrinsics {
    private static final Map<String, BiFunction<Executor, List<Value>, Value>> FUNCTIONS = Map.ofEntries(
            entry("print", Intrinsics::print),
            entry("sleep", Intrinsics::sleep),
            entry("open_server", Intrinsics::openServer),
            entry("connect_server", Intrinsics::connectServer),
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
        try {
            final int millis = getInteger(evaluated, 0);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Value openServer(Executor executor, List<Value> evaluated) {
        // Open http server
        try {
            final long port = getInteger(evaluated, 0);
            ServerSocket serverSocket = new ServerSocket((int) port);
            return new Value.JavaObject(serverSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value connectServer(Executor executor, List<Value> evaluated) {
        // Connect to http server
        try {
            final String host = getString(evaluated, 0);
            final int port = getInteger(evaluated, 1);
            Socket socket = new Socket(host, port);
            return new Value.JavaObject(socket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value acceptClient(Executor executor, List<Value> evaluated) {
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
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected socket, got " + value);
        }
        try {
            final byte[] buffer = new byte[25_000];
            final int length = socket.getInputStream().read(buffer);
            final List<Value> values = IntStream.range(0, length).mapToObj(i -> (Value) new Value.IntegerLiteral(Type.I8, buffer[i])).toList();
            return new Value.Array(Type.I8, values);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value send(Executor executor, List<Value> evaluated) {
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        final Value.Array array = (Value.Array) evaluated.get(1);
        byte[] bytes = new byte[array.values().size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ((Value.IntegerLiteral) array.values().get(i)).value();
        }
        try {
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

    private static String getString(List<Value> evaluated, int index) {
        final Value value = evaluated.get(index);
        return ((Value.StringLiteral) value).value();
    }

    private static int getInteger(List<Value> evaluated, int index) {
        final Value value = evaluated.get(index);
        return (int) ((Value.IntegerLiteral) value).value();
    }
}
