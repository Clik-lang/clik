package org.click.interpreter;

import org.click.Statement;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Phaser;

public final class VM {
    private final Context context;
    private final Executor executor;

    public record Context(Path directory, ScopeWalker<Value> walker, Phaser phaser) {
    }

    public VM(Path directory, List<Statement> statements) {
        this.context = new Context(directory, new ScopeWalker<>(), new Phaser(1));
        this.executor = new Executor(context);
        this.context.walker.enterBlock(executor);
        this.executor.interpretGlobal(statements);
    }

    public Value interpret(String function, List<Value> parameters) {
        final Value value = executor.interpret(function, parameters);
        this.context.phaser.arriveAndAwaitAdvance();
        return value;
    }

    public void stop() {
        this.context.walker().exitBlock();
    }
}
