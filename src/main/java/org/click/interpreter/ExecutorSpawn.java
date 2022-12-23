package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.value.Value;

import java.util.concurrent.Phaser;

import static org.click.Ast.Statement;

public record ExecutorSpawn(Executor executor, ScopeWalker<Value> walker) {
    Value interpret(Statement.Spawn spawn) {
        final Executor executor = executor().fork(true, false);
        final Executor.JoinScope joinScope = executor.joinScope;
        final Phaser phaser = joinScope.phaser();
        joinScope.spawns().add(executor);
        phaser.register(); // Prevent the join scope from exiting before the spawned task finishes
        Thread.startVirtualThread(() -> {
            executor.interpret(spawn.statement());
            phaser.arriveAndDeregister();
        });
        return null;
    }
}
