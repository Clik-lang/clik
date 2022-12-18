package org.click.interpreter;

import org.click.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public record ExecutorJoin(Executor executor, ScopeWalker<Value> walker) {
    Value interpret(Statement.Join join) {
        final List<Statement> statements = join.statements();
        // Run every statement in a virtual thread and start the block of the first one that finishes
        List<ScopeWalker<Value>> walkers = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(statements.size());
        for (Statement stmt : statements) {
            final Executor executor = executor().fork(true, false);
            walkers.add(executor.walker());
            Thread.startVirtualThread(() -> {
                executor.interpret(stmt);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        ValueCompute.merge(walker, walkers);
        return null;
    }
}
