package org.click.interpreter;

import org.click.Directive;
import org.click.Expression;
import org.click.Statement;
import org.click.Type;

public final class ExecutorStatement {
    private final Interpreter interpreter;
    private final ScopeWalker<Value> walker;
    private final InterpreterLoop interpreterLoop;
    private final InterpreterSelect interpreterSelect;

    public ExecutorStatement(Interpreter interpreter, ScopeWalker<Value> walker) {
        this.interpreter = interpreter;
        this.walker = walker;

        this.interpreterLoop = new InterpreterLoop(interpreter, walker);
        this.interpreterSelect = new InterpreterSelect(interpreter, walker);
    }

    Value interpret(Statement statement) {
        switch (statement) {
            case Statement.Declare declare -> {
                final String name = declare.name();
                final Expression initializer = declare.initializer();
                final Value evaluated = interpreter.evaluate(initializer, declare.explicitType());
                assert evaluated != null;
                walker.register(name, evaluated);
            }
            case Statement.Assign assign -> {
                final Type variableType = interpreter.extractType(walker.find(assign.name()));
                final Value evaluated = interpreter.evaluate(assign.expression(), variableType);
                walker.update(assign.name(), evaluated);
            }
            case Statement.Call call -> {
                return interpreter.evaluate(new Expression.Call(call.name(), call.arguments()), null);
            }
            case Statement.Branch branch -> {
                final Value condition = interpreter.evaluate(branch.condition(), null);
                assert condition != null;
                if (condition instanceof Value.Constant constant) {
                    if ((boolean) constant.value()) {
                        for (Statement thenBranch : branch.thenBranch()) {
                            interpreter.execute(thenBranch);
                        }
                    } else if (branch.elseBranch() != null) {
                        for (Statement elseBranch : branch.elseBranch()) {
                            interpreter.execute(elseBranch);
                        }
                    }
                } else {
                    throw new RuntimeException("Condition must be a boolean");
                }
            }
            case Statement.Loop loop -> {
                if (loop.iterable() == null) {
                    // Infinite loop
                    while (true) {
                        for (Statement body : loop.body()) {
                            interpreter.execute(body);
                        }
                    }
                } else {
                    this.interpreterLoop.interpret(loop);
                }
            }
            case Statement.Select select -> interpreterSelect.interpret(select);
            case Statement.Block block -> {
                this.walker.enterBlock();
                for (Statement inner : block.statements()) {
                    interpreter.execute(inner);
                }
                this.walker.exitBlock();
            }
            case Statement.Return returnStatement -> {
                final Expression expression = returnStatement.expression();
                if (expression != null) {
                    assert interpreter.currentFunction() != null : "Return outside of function";
                    final Type returnType = interpreter.currentFunction().returnType();
                    return interpreter.evaluate(expression, returnType);
                }
            }
            case Statement.Directive directive -> {
                switch (directive.directive()) {
                    case Directive.Statement.Sleep sleep -> {
                        final Value time = interpreter.evaluate(sleep.expression(), null);
                        assert time != null;
                        final int millis = (int) ((Value.Constant) time).value();
                        try {
                            Thread.sleep(millis);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        return null;
    }
}
