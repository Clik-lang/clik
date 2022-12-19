package org.click.interpreter;

import org.click.Type;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

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
        try {
            final int port = getInteger(evaluated, 0);
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.configureBlocking(true);
            serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
            return new Value.JavaObject(serverSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value connectServer(Executor executor, List<Value> evaluated) {
        try {
            final String host = getString(evaluated, 0);
            final int port = getInteger(evaluated, 1);
            SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
            channel.configureBlocking(true);
            return new Value.JavaObject(channel);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value acceptClient(Executor executor, List<Value> evaluated) {
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof ServerSocketChannel serverSocket)) {
            throw new RuntimeException("Expected server, got " + value);
        }
        try {
            final SocketChannel clientSocket = serverSocket.accept();
            return new Value.JavaObject(clientSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value recv(Executor executor, List<Value> evaluated) {
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject &&
                javaObject.object() instanceof SocketChannel socketChannel)) {
            throw new RuntimeException("Expected socket, got " + value);
        }
        try {
            final ByteBuffer buffer = MemorySegment.allocateNative(25_000, MemorySession.openImplicit()).asByteBuffer();
            final int length = socketChannel.read(buffer);
            buffer.flip();
            final MemorySegment data = MemorySegment.allocateNative(length, MemorySession.openImplicit());
            data.asByteBuffer().put(buffer);
            return new Value.ArrayValue(new Type.Array(Type.I8, length), data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Value send(Executor executor, List<Value> evaluated) {
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject &&
                javaObject.object() instanceof SocketChannel socketChannel)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        final Value.ArrayValue arrayValue = (Value.ArrayValue) evaluated.get(1);
        final MemorySegment data = arrayValue.data();
        try {
            socketChannel.write(data.asByteBuffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public static Value close(Executor executor, List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject &&
                javaObject.object() instanceof SocketChannel socketChannel)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        try {
            socketChannel.close();
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
