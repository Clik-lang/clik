package org.click.interpreter;

import org.click.*;

import java.util.List;
import java.util.Map;

public final class Executor {
    private final ScopeWalker<Value> walker;
    private final boolean insideLoop;
    private final Map<String, Value> sharedMutations;

    private final Evaluator interpreter;
    private final ExecutorLoop interpreterLoop;
    private final ExecutorSelect interpreterSelect;

    private Value.FunctionDecl currentFunction = null;

    public Executor(ScopeWalker<Value> walker, boolean insideLoop, Map<String, Value> sharedMutations) {
        this.walker = walker;
        this.insideLoop = insideLoop;
        this.sharedMutations = sharedMutations;

        this.interpreter = new Evaluator(this, walker);

        this.interpreterLoop = new ExecutorLoop(this, walker);
        this.interpreterSelect = new ExecutorSelect(this, walker);
    }

    public ScopeWalker<Value> walker() {
        return walker;
    }

    public Map<String, Value> sharedMutations() {
        return sharedMutations;
    }

    public Executor forkLoop(boolean insideLoop, Map<String, Value> sharedMutations) {
        final ScopeWalker<Value> copy = walker.flattenedCopy();
        return new Executor(copy, insideLoop, sharedMutations);
    }

    public Executor fork() {
        return forkLoop(false, sharedMutations);
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
        return switch (statement) {
            case Statement.Declare declare -> {
                final String name = declare.name();
                final Expression initializer = declare.initializer();
                final Value evaluated = interpreter.evaluate(initializer, declare.explicitType());
                assert evaluated != null;
                walker.register(name, evaluated);
                yield null;
            }
            case Statement.Assign assign -> {
                final String name = assign.name();
                final Type variableType = ValueTypeExtractor.extractType(walker.find(name));
                final Value evaluated = interpreter.evaluate(assign.expression(), variableType);
                walker.update(name, evaluated);
                if (sharedMutations.containsKey(name)) {
                    sharedMutations.put(name, evaluated);
                }
                yield null;
            }
            case Statement.Call call -> interpreter.evaluate(new Expression.Call(call.name(), call.arguments()), null);
            case Statement.Branch branch -> {
                final Value condition = interpreter.evaluate(branch.condition(), null);
                assert condition != null;
                if (condition instanceof Value.Constant constant) {
                    if ((boolean) constant.value()) {
                        for (Statement thenBranch : branch.thenBranch()) {
                            final Value value = interpret(thenBranch);
                            if (value != null) yield value;
                        }
                    } else if (branch.elseBranch() != null) {
                        for (Statement elseBranch : branch.elseBranch()) {
                            final Value value = interpret(elseBranch);
                            if (value != null) yield value;
                        }
                    }
                } else {
                    throw new RuntimeException("Condition must be a boolean");
                }
                yield null;
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
                yield null;
            }
            case Statement.Break ignored -> {
                if (!insideLoop) throw new RuntimeException("Break statement outside of loop");
                yield new Value.Break();
            }
            case Statement.Continue ignored -> {
                if (!insideLoop) throw new RuntimeException("Continue statement outside of loop");
                yield new Value.Continue();
            }
            case Statement.Select select -> {
                interpreterSelect.interpret(select);
                yield null;
            }
            case Statement.Block block -> {
                this.walker.enterBlock();
                for (Statement inner : block.statements()) interpret(inner);
                this.walker.exitBlock();
                yield null;
            }
            case Statement.Return returnStatement -> {
                final Expression expression = returnStatement.expression();
                if (expression != null) {
                    assert currentFunction != null : "Return outside of function";
                    final Type returnType = currentFunction.returnType();
                    yield interpreter.evaluate(expression, returnType);
                }
                yield null;
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
                yield null;
            }
        };
    }
}
