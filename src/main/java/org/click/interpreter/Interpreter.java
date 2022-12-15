package org.click.interpreter;

import org.click.Statement;

import java.util.List;

public final class Interpreter {
    private final ScopeWalker<Value> walker = new ScopeWalker<>();
    private final ExecutorStatement executor = new ExecutorStatement(walker);

    public Interpreter(List<Statement> statements) {
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
