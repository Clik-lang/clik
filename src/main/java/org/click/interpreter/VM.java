package org.click.interpreter;

                   import org.click.Directive;
import org.click.Statement;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;

public final class VM {
    private final Context context;
    private final Executor executor;

    public record Context(Path directory, ScopeWalker<Value> walker, Phaser phaser) {
    }

    public VM(Path directory, List<Statement> statements) {
        this.context = new Context(directory, new ScopeWalker<>(), new Phaser(1));
        this.executor = new Executor(context, false, Map.of());
        this.context.walker.enterBlock(executor);
        // Global scope
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = executor.evaluate(declare.initializer(), declare.explicitType());
                this.executor.registerMulti(declare.names(), value);
            } else if (statement instanceof Statement.Directive directive) {
                if (directive.directive() instanceof Directive.Statement.Load) {
                    this.executor.interpret(directive);
                } else {
                    throw new RuntimeException("Directive not supported as global: " + directive);
                }
            } else {
                throw new RuntimeException("Unexpected global declaration: " + statement);
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
