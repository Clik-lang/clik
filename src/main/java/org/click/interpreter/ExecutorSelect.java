package org.click.interpreter;

import org.click.Statement;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public record ExecutorSelect(Executor executor, ScopeWalker<Value> walker) {
    Value interpret(Statement.Select select) {
        final List<Statement.Block> blocks = select.blocks();
        // Run every statement in a virtual thread and start the block of the first one that finishes
        AtomicReference<Selection> selectionRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        for (Statement.Block block : blocks) {
            final Executor executor = executor().fork(true, executor().insideLoop);
            Thread.startVirtualThread(() -> {
                if (selectionRef.get() != null) return;
                final Value result = executor.interpret(block);
                Selection selection = new Selection(executor, result);
                if (selectionRef.compareAndSet(null, selection)) {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        final Selection selection = selectionRef.get();
        assert selection != null;
        ValueCompute.update(walker, selection.executor().walker());
        return selection.value();
    }

    record Selection(Executor executor, Value value) {
    }
}
