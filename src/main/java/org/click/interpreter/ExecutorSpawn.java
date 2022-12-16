package org.click.interpreter;

import org.click.Statement;

import java.util.List;
import java.util.concurrent.Phaser;

public record ExecutorSpawn(Executor executor, ScopeWalker<Value> walker) {
    void interpret(Statement.Spawn spawn) {
        final List<Statement> statements = spawn.statements();
        final Executor executor = executor().fork(false);
        final Phaser phaser = executor.context().phaser();
        phaser.register(); // Prevent the main thread from exiting before the spawned task finishes
        Thread.startVirtualThread(() -> {
            for (Statement stmt : statements) {
                executor.interpret(stmt);
            }
            phaser.arriveAndDeregister();
        });
    }
}
