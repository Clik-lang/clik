package org.click.interpreter;

import org.click.Statement;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public record InterpreterSelect(Interpreter interpreter, ScopeWalker<Value> walker) {
    void interpret(Statement.Select select) {
        final Map<Statement, Statement.Block> cases = select.cases();
        // Run every statement in a virtual thread and start the block of the first one that finishes
        AtomicReference<FinishedCase> finishedRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        for (Statement stmt : cases.keySet()) {
            final ScopeWalker<Value> copy = walker.flattenedCopy();
            final ExecutorStatement executor = new ExecutorStatement(interpreter, copy);
            Thread.startVirtualThread(() -> {
                executor.interpret(stmt);
                final FinishedCase finishedCase = new FinishedCase(stmt, copy);
                if (finishedRef.compareAndSet(null, finishedCase)) {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        final FinishedCase finished = finishedRef.get();
        final Statement statement = finished.statement();
        final Statement.Block block = cases.get(statement);
        ValueMerger.update(walker, finished.walker());
        interpreter.execute(block);
    }

    record FinishedCase(Statement statement, ScopeWalker<Value> walker) {
    }
}
