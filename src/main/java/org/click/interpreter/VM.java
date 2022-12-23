package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.value.Value;

import java.nio.file.Path;
import java.util.List;

import static org.click.Ast.Statement;

public final class VM {
    private final Context context;
    private final Executor executor;

    public record Context(Path directory, ScopeWalker<Value> walker) {
    }

    public VM(Path directory, List<Statement> statements) {
        this.context = new Context(directory, new ScopeWalker<>());
        this.executor = new Executor(context);
        this.context.walker.enterBlock(executor);
        this.executor.interpretGlobal(statements);
    }

    public Value interpret(String function, List<Value> parameters) {
        return executor.interpret(function, parameters);
    }

    public void stop() {
        this.context.walker().exitBlock();
    }
}
