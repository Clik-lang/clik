package org.click.interpreter;

import org.click.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Executor {
    private final VM.Context context;
    private final ScopeWalker<Value> walker;
    boolean insideLoop;
    private final Map<String, Value> sharedMutations;

    private final Evaluator interpreter;
    private final ExecutorLoop interpreterLoop;
    private final ExecutorSelect interpreterSelect;
    private final ExecutorSpawn interpreterSpawn;

    private CurrentFunction currentFunction = null;

    record CurrentFunction(String name, List<Parameter> parameters, Type returnType,
                           List<Value> evaluatedParameters) {
    }

    public Executor(VM.Context context, boolean insideLoop, Map<String, Value> sharedMutations) {
        this.context = context;
        this.walker = context.walker();
        this.insideLoop = insideLoop;
        this.sharedMutations = sharedMutations;

        this.interpreter = new Evaluator(this, walker);

        this.interpreterLoop = new ExecutorLoop(this, walker);
        this.interpreterSelect = new ExecutorSelect(this, walker);
        this.interpreterSpawn = new ExecutorSpawn(this, walker);
    }

    public VM.Context context() {
        return context;
    }

    public ScopeWalker<Value> walker() {
        return walker;
    }

    public Map<String, Value> sharedMutations() {
        return sharedMutations;
    }

    public Executor forkLoop(boolean insideLoop, Map<String, Value> sharedMutations) {
        final ScopeWalker<Value> copy = new ScopeWalker<>();
        final VM.Context context = new VM.Context(this.context.directory(), copy, this.context.phaser());
        final Executor executor = new Executor(context, insideLoop, sharedMutations);
        copy.enterBlock(executor);
        for (Map.Entry<String, Value> entry : this.walker.currentScope().tracked.entrySet()) {
            copy.register(entry.getKey(), entry.getValue());
        }
        return executor;
    }

    public Executor fork() {
        return forkLoop(false, sharedMutations);
    }

    public Value interpret(String function, List<Value> parameters) {
        final Value call = walker.find(function);
        if (!(call instanceof Value.FunctionDecl functionDeclaration)) {
            throw new RuntimeException("Function not found: " + call + " " + function);
        }

        walker.enterBlock(this);
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = functionDeclaration.parameters().get(i);
            final Value value = parameters.get(i);
            assert value != null;
            walker.register(parameter.name(), value);
        }

        var previousFunction = currentFunction;
        currentFunction = new CurrentFunction(function, functionDeclaration.parameters(),
                functionDeclaration.returnType(), parameters);
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

    public void registerMulti(List<String> names, Value value) {
        if (names.size() == 1) {
            // Single return
            final String name = names.get(0);
            walker.register(name, value);
        } else {
            // Multiple return
            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);
                final Value deconstructed = ValueExtractor.deconstruct(walker, value, i);
                walker.register(name, deconstructed);
            }
        }
    }

    void interpretGlobal(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = evaluate(declare.initializer(), declare.explicitType());
                registerMulti(declare.names(), value);
            } else if (statement instanceof Statement.Directive directive) {
                if (directive.directive() instanceof Directive.Statement.Load) {
                    interpret(directive);
                } else {
                    throw new RuntimeException("Directive not supported as global: " + directive);
                }
            } else {
                throw new RuntimeException("Unexpected global declaration: " + statement);
            }
        }
    }

    Value interpret(Statement statement) {
        return switch (statement) {
            case Statement.Declare declare -> {
                final List<String> names = declare.names();
                final Expression initializer = declare.initializer();
                final Value evaluated = interpreter.evaluate(initializer, declare.explicitType());
                assert evaluated != null;
                registerMulti(names, evaluated);
                yield null;
            }
            case Statement.Assign assign -> {
                final List<String> names = assign.names();
                if (names.size() == 1) {
                    final String name = names.get(0);
                    final Type variableType = ValueExtractor.extractType(walker.find(name));
                    final Value evaluated = interpreter.evaluate(assign.expression(), variableType);
                    walker.update(name, evaluated);
                    if (sharedMutations.containsKey(name)) {
                        sharedMutations.put(name, evaluated);
                    }
                } else {
                    final Value evaluated = interpreter.evaluate(assign.expression(), null);
                    for (int i = 0; i < names.size(); i++) {
                        final String name = names.get(i);
                        final Value deconstructed = ValueExtractor.deconstruct(walker, evaluated, i);
                        walker.update(name, deconstructed);
                        if (sharedMutations.containsKey(name)) {
                            sharedMutations.put(name, deconstructed);
                        }
                    }
                }
                yield null;
            }
            case Statement.Call call -> interpreter.evaluate(new Expression.Call(call.name(), call.arguments()), null);
            case Statement.Branch branch -> {
                final Value condition = interpreter.evaluate(branch.condition(), null);
                assert condition != null;
                if (condition instanceof Value.BooleanLiteral booleanLiteral) {
                    if (booleanLiteral.value()) {
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
            case Statement.Spawn spawn -> {
                this.interpreterSpawn.interpret(spawn);
                yield null;
            }
            case Statement.Defer defer -> {
                ScopeWalker<Value>.Scope currentScope = walker.currentScope();
                currentScope.deferred.add(defer.statement());
                yield null;
            }
            case Statement.Block block -> {
                this.walker.enterBlock(this);
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
                    case Directive.Statement.Load load -> {
                        final String filePath = load.path();
                        final String source;
                        try {
                            final Path path = this.context.directory().resolve(filePath);
                            source = Files.readString(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        final List<Token> tokens = new Scanner(source).scanTokens();
                        final List<Statement> statements = new Parser(tokens).parse();
                        interpretGlobal(statements);
                    }
                    case Directive.Statement.Intrinsic ignored -> {
                        final CurrentFunction currentFunction = this.currentFunction;
                        assert currentFunction != null : "Intrinsic outside of function";
                        yield Intrinsics.evaluate(this, currentFunction.name(), currentFunction.evaluatedParameters());
                    }
                }
                yield null;
            }
        };
    }
}
