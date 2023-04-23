package org.click.external;

import org.click.BinStandard;
import org.click.value.Value;
import org.click.value.ValueSerializer;

import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static int getInteger(Value[] args, int index) {
        final Value value = args[index];
        if (!(value instanceof Value.Binary binary))
            throw new IllegalArgumentException("Expected binary value, got " + value.getClass().getSimpleName());
        final BinStandard standard = binary.standard();
        if (!standard.equals(BinStandard.I32))
            throw new IllegalArgumentException("Expected binary value of type I32, got " + standard);
        return binary.segment().get(ValueLayout.JAVA_INT, 0);
    }

    private static String getString(Value[] args, int index) {
        final Value value = args[index];
        if (!(value instanceof Value.Binary binary))
            throw new IllegalArgumentException("Expected binary value, got " + value.getClass().getSimpleName());
        final BinStandard standard = binary.standard();
        if (!standard.equals(BinStandard.UTF8))
            throw new IllegalArgumentException("Expected binary value of type I32, got " + standard);
        var array = binary.segment().toArray(ValueLayout.JAVA_BYTE);
        return new String(array, StandardCharsets.UTF_8);
    }

    public static final class OpenServer implements ExternalFunction {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final Map<Integer, ServerSocketChannel> servers = new ConcurrentHashMap<>();

        @Override
        public Value run(Value... args) {
            final int fd = counter.incrementAndGet();
            try {
                final int port = getInteger(args, 0);
                ServerSocketChannel serverSocket = ServerSocketChannel.open();
                serverSocket.configureBlocking(true);
                serverSocket.bind(new InetSocketAddress("0.0.0.0", port));
                this.servers.put(fd, serverSocket);
                return Value.Binary.I32(fd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Map<Integer, ServerSocketChannel> servers() {
            return servers;
        }
    }

    public static final class ConnectServer implements ExternalFunction {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final Map<Integer, SocketChannel> sockets = new ConcurrentHashMap<>();

        @Override
        public Value run(Value... args) {
            final int fd = counter.incrementAndGet();
            try {
                final String host = getString(args, 0);
                final int port = getInteger(args, 1);
                SocketChannel channel = SocketChannel.open(new InetSocketAddress(host, port));
                channel.configureBlocking(true);
                this.sockets.put(fd, channel);
                return Value.Binary.I32(fd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Map<Integer, SocketChannel> sockets() {
            return sockets;
        }
    }

    public static final class AcceptClient implements ExternalFunction {
        private final AtomicInteger counter = new AtomicInteger(0);
        private final Map<Integer, ServerSocketChannel> servers;
        private final Map<Integer, SocketChannel> sockets = new ConcurrentHashMap<>();

        public AcceptClient(Map<Integer, ServerSocketChannel> servers) {
            this.servers = servers;
        }

        @Override
        public Value run(Value... args) {
            final int fd = counter.incrementAndGet();
            final int serverFd = getInteger(args, 0);
            final ServerSocketChannel serverSocket = servers.get(serverFd);
            if (serverSocket == null) throw new RuntimeException("Server not found: " + serverFd);
            try {
                final SocketChannel clientSocket = serverSocket.accept();
                this.sockets.put(fd, clientSocket);
                return Value.Binary.I32(fd);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public Map<Integer, SocketChannel> sockets() {
            return sockets;
        }
    }

    public static final class Send implements ExternalFunction {
        private final Map<Integer, SocketChannel> sockets;

        public Send(Map<Integer, SocketChannel> sockets) {
            this.sockets = sockets;
        }

        @Override
        public Value run(Value... args) {
            final int serverFd = getInteger(args, 0);
            final String data = getString(args, 1);
            final SocketChannel socket = sockets.get(serverFd);
            if (socket == null) throw new RuntimeException("Socket not found: " + serverFd);

            final byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            try {
                socket.write(buffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }

    public static final class Close implements ExternalFunction {
        private final Map<Integer, SocketChannel> sockets;

        public Close(Map<Integer, SocketChannel> sockets) {
            this.sockets = sockets;
        }

        @Override
        public Value run(Value... args) {
            final int serverFd = getInteger(args, 0);
            final SocketChannel socket = sockets.remove(serverFd);
            if (socket == null) throw new RuntimeException("Socket not found: " + serverFd);
            try {
                socket.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return null;
        }
    }
}
