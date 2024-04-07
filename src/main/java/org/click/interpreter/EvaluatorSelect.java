package org.click.interpreter;

import org.click.Type;
import org.click.value.Value;
import org.click.value.ValueCompute;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.click.Ast.Expression;
import static org.click.Ast.Statement;

public record EvaluatorSelect(Executor executor, ScopeWalker<Value> walker) {
    Value evaluate(Expression.Select select, @Nullable Type explicitType) {
        final List<Statement.Block> blocks = select.blocks();
        // Run every statement in a virtual thread and start the block of the first one that finishes
        AtomicReference<Selection> selectionRef = new AtomicReference<>();
        Thread[] threads = new Thread[blocks.size()];
        CountDownLatch latch = new CountDownLatch(1);
        for (int i = 0; i < threads.length; i++) {
            final Statement.Block block = blocks.get(i);
            final Executor executor = executor().fork(true, executor().insideLoop);
            final Value.FunctionDecl decl = new Value.FunctionDecl(List.of(), explicitType, List.of(block), null);
            threads[i] = Thread.ofVirtual().unstarted(() -> {
                if (selectionRef.get() != null) return;
                final Value result = executor.interpret("select", decl, List.of());
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
