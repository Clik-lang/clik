package org.click.interpreter;

import org.click.*;

import java.util.List;

public final class Executor {
    private final ScopeWalker<Value> walker;

    private final Evaluator interpreter;
    private final ExecutorLoop interpreterLoop;
    private final ExecutorSelect interpreterSelect;

    private Value.FunctionDecl currentFunction = null;

    public Executor(ScopeWalker<Value> walker) {
        this.walker = walker;

        this.interpreter = new Evaluator(this, walker);

        this.interpreterLoop = new ExecutorLoop(this, walker);
        this.interpreterSelect = new ExecutorSelect(this, walker);
    }

    public ScopeWalker<Value> walker() {
        return walker;
    }

    public Executor fork() {
        final ScopeWalker<Value> copy = walker.flattenedCopy();
        return new Executor(copy);
    }

    public Value interpret(String function, List<Value> parameters) {
        final Value call = walker.find(function);
        if (!(call instanceof Value.FunctionDecl functionDeclaration)) {
            throw new RuntimeException("Function not found: " + call + " " + function);
        }

        walker.enterBlock();
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = functionDeclaration.parameters().get(i);
            final Value value = parameters.get(i);
            assert value != null;
            walker.register(parameter.name(), value);
        }

        var previousFunction = currentFunction;
        currentFunction = functionDeclaration;
        Value result = null;
        for (Statement statement : functionDeclaration.body()) {
            result = interpret(statement);
            if (result != null) break;
        }
        currentFunction = previousFunction;
        walker.exitBlock();
        return result;
    }

    public Value evaluate(Expression expression, Type explicitType) {
        return interpreter.evaluate(expression, explicitType);
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
                final Type variableType = ValueTypeExtractor.extractType(walker.find(assign.name()));
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
                            interpret(thenBranch);
                        }
                    } else if (branch.elseBranch() != null) {
                        for (Statement elseBranch : branch.elseBranch()) {
                            interpret(elseBranch);
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
                        for (Statement body : loop.body()) interpret(body);
                    }
                } else {
                    this.interpreterLoop.interpret(loop);
                }
            }
            case Statement.Select select -> interpreterSelect.interpret(select);
            case Statement.Block block -> {
                this.walker.enterBlock();
                for (Statement inner : block.statements()) interpret(inner);
                this.walker.exitBlock();
            }
            case Statement.Return returnStatement -> {
                final Expression expression = returnStatement.expression();
                if (expression != null) {
                    assert currentFunction != null : "Return outside of function";
                    final Type returnType = currentFunction.returnType();
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
