package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.external.ExternalFunction;
import org.click.value.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.click.Ast.Statement;

public final class VM {
    private final Context context;
    private final Executor executor;

    public record Context(Path directory, ScopeWalker<Value> walker,
                          Map<String, ExternalFunction> externals) {
        public Context {
            externals = Map.copyOf(externals);
        }
    }

    public VM(Path directory, List<Statement> statements,
              Map<String, ExternalFunction> externals) {
        this.context = new Context(directory, new ScopeWalker<>(), externals);
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
