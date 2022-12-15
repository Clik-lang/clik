package org.click.interpreter;

import org.click.Expression;
import org.click.Parameter;
import org.click.Type;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;

public final class EvaluatorFunction {
    private final Executor executor;

    private final Map<String, Function<List<Value>, Value>> functions = Map.ofEntries(
            entry("print", this::print),
            entry("open_server", this::openServer),
            entry("accept_client", this::acceptClient),
            entry("recv", this::recv),
            entry("send", this::send),
            entry("close", this::close)
    );

    public EvaluatorFunction(Executor executor) {
        this.executor = executor;
    }

    public Value print(List<Value> evaluated) {
        StringBuilder builder = new StringBuilder();
        for (Value value : evaluated) {
            final String serialized = ValueSerializer.serialize(executor.walker(), value);
            builder.append(serialized);
        }
        System.out.println(builder);
        return null;
    }

    public Value openServer(List<Value> evaluated) {
        if (evaluated.size() != 1) throw new RuntimeException("Expected 1 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.Constant constant)) {
            throw new RuntimeException("Expected constant, got " + value);
        }
        if (!(constant.value() instanceof Integer port)) {
            throw new RuntimeException("Expected integer, got " + constant.type());
        }
        // Open http server
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            return new Value.JavaObject(serverSocket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Value acceptClient(List<Value> evaluated) {
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

    public Value recv(List<Value> evaluated) {
        if (evaluated.size() != 2) throw new RuntimeException("Expected 2 argument, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected socket, got " + value);
        }
        final Value sizeValue = evaluated.get(1);
        if (!(sizeValue instanceof Value.Constant constant && constant.value() instanceof Integer size)) {
            throw new RuntimeException("Expected constant, got " + sizeValue);
        }
        try {
            final byte[] buffer = new byte[size];
            final int length = socket.getInputStream().read(buffer);
            final String string = new String(buffer, 0, length, StandardCharsets.UTF_8);
            return new Value.Constant(Type.STRING, string);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Value send(List<Value> evaluated) {
        if (evaluated.size() != 2) throw new RuntimeException("Expected 2 arguments, got " + evaluated.size());
        final Value value = evaluated.get(0);
        if (!(value instanceof Value.JavaObject javaObject && javaObject.object() instanceof Socket socket)) {
            throw new RuntimeException("Expected client, got " + value);
        }
        final Value message = evaluated.get(1);
        if (!(message instanceof Value.Constant c && c.value() instanceof String messageString)) {
            throw new RuntimeException("Expected string, got " + message);
        }
        try {
            final byte[] bytes = messageString.getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public Value close(List<Value> evaluated) {
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

    public Value evaluate(Expression.Call call) {
        final String name = call.name();
        final Function<List<Value>, Value> builtin = functions.get(name);
        if (builtin != null) {
            // TODO: explicit type for builtin functions
            final List<Value> evaluated = call.arguments().expressions().stream()
                    .map(expression -> executor.evaluate(expression, null)).toList();
            return builtin.apply(evaluated);
        } else {
            final Value.FunctionDecl functionDecl = (Value.FunctionDecl) executor.walker().find(name);
            assert functionDecl != null : "Function " + name + " not found";
            final List<Parameter> params = functionDecl.parameters();
            final List<Expression> expressions = call.arguments().expressions();
            List<Value> evaluated = new ArrayList<>();
            for (int i = 0; i < call.arguments().expressions().size(); i++) {
                final Parameter param = params.get(i);
                final Expression expression = expressions.get(i);
                final Value value = executor.evaluate(expression, param.type());
                evaluated.add(value);
            }
            return executor.interpret(name, evaluated);
        }
    }
}
