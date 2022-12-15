package org.click.interpreter;

import org.click.Statement;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

public final class VM {
    private final Context context = new Context(new ScopeWalker<>(), new Phaser(1));
    private final Executor executor = new Executor(context, false, Map.of());

    public record Context(ScopeWalker<Value> walker, Phaser phaser) {
    }

    public VM(List<Statement> statements) {
        this.context.walker.enterBlock(executor);
        // Global scope
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = executor.evaluate(declare.initializer(), declare.explicitType());
                this.context.walker().register(declare.name(), value);
            }
        }
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
