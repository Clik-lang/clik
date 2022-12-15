package org.click.interpreter;

import org.click.Statement;

import java.util.List;
import java.util.Map;

public final class VM {
    private final ScopeWalker<Value> walker = new ScopeWalker<>();
    private final Executor executor = new Executor(walker, false, Map.of());

    public VM(List<Statement> statements) {
        this.walker.enterBlock();
        // Global scope
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = executor.evaluate(declare.initializer(), declare.explicitType());
                walker.register(declare.name(), value);
            }
        }
    }

    public Value interpret(String function, List<Value> parameters) {
        return executor.interpret(function, parameters);
    }

    public void stop() {
        walker.exitBlock();
    }
}
