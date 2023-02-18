package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.io.IO;
import org.click.value.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.click.Ast.Statement;

public final class VM {
    private final Context context;
    private final Executor executor;

    public record Context(Path directory, ScopeWalker<Value> walker,
                          Map<String, Function<List<Value>, Value>> externals,
                          Map<String, IO.In> inputs,
                          Map<String, IO.Out> outputs) {
        public Context {
            externals = Map.copyOf(externals);
            inputs = Map.copyOf(inputs);
        }
    }

    public VM(Path directory, List<Statement> statements,
              Map<String, Function<List<Value>, Value>> externals,
              Map<String, IO.In> inputs,
              Map<String, IO.Out> outputs) {
        this.context = new Context(directory, new ScopeWalker<>(), externals, inputs, outputs);
        this.executor = new Executor(context);
        this.context.walker.enterBlock();
        this.executor.interpret(statements);
    }

    public Value interpret(String function, List<Value> parameters) {
        return executor.interpret(function, parameters);
    }

    public void stop() {
        this.context.walker().exitBlock();
    }
}
