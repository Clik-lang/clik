package org.click.interpreter;

import org.click.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public record ExecutorJoin(Executor executor, ScopeWalker<Value> walker) {
    Value interpret(Statement.Join join) {
        final List<Statement.Block> blocks = join.blocks();
        // Run every statement in a virtual thread and start the block of the first one that finishes
        List<ScopeWalker<Value>> walkers = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(blocks.size());
        for (Statement.Block block : blocks) {
            final Executor executor = executor().fork(true, false);
            walkers.add(executor.walker());
            Thread.startVirtualThread(() -> {
                executor.interpret(block);
                latch.countDown();
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            return new Value.Interrupt();
        }
        ValueCompute.merge(walker, walkers);
        return null;
    }
}
