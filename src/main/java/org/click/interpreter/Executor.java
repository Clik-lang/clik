package org.click.interpreter;

import org.click.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class Executor {
    private final VM.Context context;
    private final ScopeWalker<Value> walker;
    boolean insideLoop;
    private final Map<String, AtomicReference<Value>> sharedMutations;

    private final Evaluator interpreter;
    private final ExecutorLoop interpreterLoop;
    private final ExecutorSelect interpreterSelect;
    private final ExecutorJoin interpreterJoin;
    private final ExecutorSpawn interpreterSpawn;

    private CurrentFunction currentFunction = null;

    record CurrentFunction(String name, List<Parameter> parameters, Type returnType,
                           List<Value> evaluatedParameters) {
    }

    public Executor(VM.Context context, boolean insideLoop, Map<String, AtomicReference<Value>> sharedMutations) {
        this.context = context;
        this.walker = context.walker();
        this.insideLoop = insideLoop;
        this.sharedMutations = new HashMap<>(sharedMutations);

        this.interpreter = new Evaluator(this, walker);

        this.interpreterLoop = new ExecutorLoop(this, walker);
        this.interpreterSelect = new ExecutorSelect(this, walker);
        this.interpreterJoin = new ExecutorJoin(this, walker);
        this.interpreterSpawn = new ExecutorSpawn(this, walker);
    }

    public VM.Context context() {
        return context;
    }

    public ScopeWalker<Value> walker() {
        return walker;
    }

    public Map<String, AtomicReference<Value>> sharedMutations() {
        return sharedMutations;
    }

    public CurrentFunction currentFunction() {
        return currentFunction;
    }

    public Executor fork(boolean insideLoop) {
        final ScopeWalker<Value> copy = new ScopeWalker<>();
        final VM.Context context = new VM.Context(this.context.directory(), copy, this.context.phaser());
        final Executor executor = new Executor(context, insideLoop, sharedMutations);
        copy.enterBlock(executor);
        copy.currentScope().tracked.putAll(walker.currentScope().tracked);
        return executor;
    }

    public Value interpret(String name, Value.FunctionDecl declaration, List<Value> parameters) {
        walker.enterBlock(this);
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = declaration.parameters().get(i);
            final Value value = parameters.get(i);
            assert value != null;
            walker.register(parameter.name(), value);
        }

        var previousFunction = currentFunction;
        currentFunction = new CurrentFunction(name, declaration.parameters(),
                declaration.returnType(), parameters);
        Value result = null;
        for (Statement statement : declaration.body()) {
            result = interpret(statement);
            if (result != null) break;
        }
        currentFunction = previousFunction;
        walker.exitBlock();
        return result;
    }

    public Value interpret(String name, List<Value> parameters) {
        final Value call = walker.find(name);
        if (!(call instanceof Value.FunctionDecl declaration)) {
            throw new RuntimeException("Function not found: " + call + " " + name + " -> " + walker.currentScope().tracked.keySet());
        }
        return interpret(name, declaration, parameters);
    }

    public Value evaluate(Expression expression, Type explicitType) {
        return interpreter.evaluate(expression, explicitType);
    }

    public void registerMulti(List<String> names, Statement.DeclarationType declarationType, Value value) {
        final boolean isShared = declarationType == Statement.DeclarationType.SHARED;
        if (names.size() == 1) {
            // Single return
            final String name = names.get(0);
            walker.register(name, value);
            if (isShared) sharedMutations.put(name, new AtomicReference<>(value));
        } else {
            // Multiple return
            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);
                final Value deconstructed = ValueExtractor.deconstruct(walker, value, i);
                walker.register(name, deconstructed);
                if (isShared) sharedMutations.put(name, new AtomicReference<>(deconstructed));
            }
        }
    }

    void interpretGlobal(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = evaluate(declare.initializer(), declare.explicitType());
                registerMulti(declare.names(), declare.type(), value);
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
                registerMulti(names, declare.type(), evaluated);
                yield null;
            }
            case Statement.Assign assign -> {
                final List<Statement.AssignTarget> assignTargets = assign.assignTargets();
                final int count = assignTargets.size();
                if (count == 1) {
                    final Statement.AssignTarget assignTarget = assignTargets.get(0);
                    final String name = assignTarget.name();
                    final Value tracked = walker.find(name);
                    final Type variableType = ValueExtractor.extractAssignmentType(tracked);
                    final Value evaluated = interpreter.evaluate(assign.expression(), variableType);
                    final Value updatedVariable = ValueExtractor.updateVariable(this, tracked, assignTarget.accessPoint(), evaluated);
                    walker.update(name, updatedVariable);
                    var ref = sharedMutations.get(name);
                    if (ref != null) ref.set(updatedVariable);
                } else {
                    final Value evaluated = interpreter.evaluate(assign.expression(), null);
                    for (int i = 0; i < count; i++) {
                        final Statement.AssignTarget assignTarget = assignTargets.get(i);
                        final String name = assignTarget.name();
                        final Value tracked = walker.find(name);
                        final Value deconstructed = ValueExtractor.deconstruct(walker, evaluated, i);
                        final Value updatedVariable = ValueExtractor.updateVariable(this, tracked, assignTarget.accessPoint(), deconstructed);
                        walker.update(name, updatedVariable);
                        var ref = sharedMutations.get(name);
                        if (ref != null) ref.set(updatedVariable);
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
                this.interpreterLoop.interpret(loop);
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
            case Statement.Select select -> interpreterSelect.interpret(select);
            case Statement.Join join -> interpreterJoin.interpret(join);
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
                Value result = null;
                for (Statement inner : block.statements()) {
                    result = interpret(inner);
                    if (result != null) break;
                }
                this.walker.exitBlock();
                yield result;
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
