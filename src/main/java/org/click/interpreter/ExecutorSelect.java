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
        Thread[] threads = new Thread[blocks.size()];
        CountDownLatch latch = new CountDownLatch(1);
        for (int i = 0; i < threads.length; i++) {
            final Statement.Block block = blocks.get(i);
            final Executor executor = executor().fork(true, executor().insideLoop);
            threads[i] = Thread.ofVirtual().unstarted(() -> {
                if (selectionRef.get() != null) return;
                final Value result = executor.interpret(block);
                if (executor.interrupted) return;
                Selection selection = new Selection(executor, result);
                if (selectionRef.compareAndSet(null, selection)) {
                    latch.countDown();
                }
            });
        }
        for (Thread thread : threads) thread.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            return new Value.Interrupt();
        }
        for (Thread thread : threads) thread.interrupt();
        final Selection selection = selectionRef.get();
        assert selection != null;
        ValueCompute.update(walker, selection.executor().walker());
        return selection.value();
    }

    record Selection(Executor executor, Value value) {
    }
}
