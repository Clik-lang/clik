package org.click.interpreter;

import org.click.Statement;

import java.util.concurrent.Phaser;

public record ExecutorSpawn(Executor executor, ScopeWalker<Value> walker) {
    void interpret(Statement.Spawn spawn) {
        final Executor executor = executor().fork(true, false);
        final Phaser phaser = executor.context().phaser();
        phaser.register(); // Prevent the main thread from exiting before the spawned task finishes
        Thread.startVirtualThread(() -> {
            executor.interpret(spawn.statement());
            phaser.arriveAndDeregister();
        });
    }
}
