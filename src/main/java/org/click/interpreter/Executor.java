package org.click.interpreter;

import org.click.*;
import org.click.value.Value;
import org.click.value.ValueCompute;
import org.click.value.ValueType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static org.click.Ast.*;

public final class Executor {
    private final VM.Context context;
    private final ScopeWalker<Value> walker;
    final boolean async;
    boolean insideLoop;
    boolean interrupted;
    JoinScope joinScope;
    final Map<String, SharedMutation> sharedMutations;

    private final Evaluator interpreter;
    private final ExecutorLoop interpreterLoop;
    private final ExecutorSpawn interpreterSpawn;

    private CurrentFunction currentFunction = null;

    record SharedMutation(AtomicReference<Value> ref, ReentrantLock writeLock,
                          ReentrantLock readLock, Condition condition) {
        public SharedMutation(Value initial) {
            this(new AtomicReference<>(initial), new ReentrantLock(), new ReentrantLock(), null);
        }

        public SharedMutation {
            condition = readLock.newCondition();
        }

        void append(Executor executor, Value previous, Value next) {
            writeLock.lock();
            if (executor.async) {
                final Value current = ref.get();
                final Value delta = ValueCompute.delta(previous, next);
                final Value merged = ValueCompute.mergeDelta(current, delta);
                ref.set(merged);
            } else {
                ref.set(next);
            }
            writeLock.unlock();
            readLock.lock();
            condition.signalAll();
            readLock.unlock();
        }

        Value await(Value value) {
            Value current = ref.get();
            if (!value.equals(current)) return current;
            // Wait for update
            this.readLock.lock();
            try {
                current = ref.get();
                if (!value.equals(current)) return current;
                condition.await();
            } catch (InterruptedException e) {
                return new Value.Interrupt();
            } finally {
                this.readLock.unlock();
            }
            return ref.get();
        }
    }

    record JoinScope(Phaser phaser, List<Executor> spawns) {
    }

    record CurrentFunction(String name, List<Parameter> parameters, Type returnType,
                           List<Value> evaluatedParameters) {
    }

    public Executor(VM.Context context, boolean async, boolean insideLoop,
                    JoinScope joinScope, Map<String, SharedMutation> sharedMutations) {
        this.context = context;
        this.walker = context.walker();
        this.async = async;
        this.insideLoop = insideLoop;
        this.joinScope = joinScope;
        this.sharedMutations = new HashMap<>(sharedMutations);

        this.interpreter = new Evaluator(this, walker);

        this.interpreterLoop = new ExecutorLoop(this, walker);
        this.interpreterSpawn = new ExecutorSpawn(this, walker);
    }

    public Executor(VM.Context context) {
        this(context, false, false, new JoinScope(new Phaser(1), new ArrayList<>()), Map.of());
    }

    public VM.Context context() {
        return context;
    }

    public ScopeWalker<Value> walker() {
        return walker;
    }

    public CurrentFunction currentFunction() {
        return currentFunction;
    }

    public Executor fork(boolean async, boolean insideLoop) {
        final ScopeWalker<Value> copy = new ScopeWalker<>();
        final VM.Context context = new VM.Context(this.context.directory(), copy);
        final Executor executor = new Executor(context, async, insideLoop, joinScope, sharedMutations);
        copy.enterBlock();
        this.walker.currentScope().tracked().forEach(copy::register);
        return executor;
    }

    public Value interpret(String name, Value.FunctionDecl declaration, List<Value> parameters) {
        walker.enterBlock();
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = declaration.parameters().get(i);
            final Value value = parameters.get(i);
            assert value != null;
            walker.register(parameter.name(), value);
        }

        enterScope();
        var previousFunction = currentFunction;
        currentFunction = new CurrentFunction(name, declaration.parameters(),
                declaration.returnType(), parameters);
        Value result = null;
        for (Statement statement : declaration.body()) {
            result = interpret(statement);
            if (result != null) break;
        }
        currentFunction = previousFunction;
        exitScope();
        walker.exitBlock();
        return result;
    }

    JoinScope previousJoinScope = null;

    void enterScope() {
        this.previousJoinScope = joinScope;
        this.joinScope = new JoinScope(new Phaser(1), new ArrayList<>());
    }

    void exitScope() {
        this.joinScope.phaser.arriveAndAwaitAdvance();
        // Merge
        final List<ScopeWalker<Value>> walkers = joinScope.spawns().stream().map(Executor::walker).toList();
        ValueCompute.merge(walker, walkers);
        // Restore
        joinScope = previousJoinScope;
    }

    public Value interpret(String name, List<Value> parameters) {
        final Value call = walker.find(name);
        if (!(call instanceof Value.FunctionDecl declaration)) {
            throw new RuntimeException("Function not found: " + call + " " + name + " -> " + walker.currentScope().tracked().keySet());
        }
        return interpret(name, declaration, parameters);
    }

    public Value evaluate(Expression expression, Type explicitType) {
        return interpreter.evaluate(expression, explicitType);
    }

    public void registerMulti(List<String> names, DeclarationType declarationType, Value value) {
        final boolean isShared = declarationType == DeclarationType.SHARED;
        if (names.size() == 1) {
            // Single return
            final String name = names.get(0);
            walker.register(name, value);
            if (isShared) this.sharedMutations.put(name, new SharedMutation(value));
        } else {
            // Multiple return
            for (int i = 0; i < names.size(); i++) {
                final String name = names.get(i);
                final Value deconstructed = ValueCompute.deconstruct(walker, value, i);
                walker.register(name, deconstructed);
                if (isShared) this.sharedMutations.put(name, new SharedMutation(value));
            }
        }
    }

    void interpretGlobal(List<Statement> statements) {
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = evaluate(declare.initializer(), declare.explicitType());
                registerMulti(declare.names(), declare.declarationType(), value);
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
        assert joinScope != null : "Join scope not initialized";
        assert !interrupted : "Cannot interpret after interrupt";
        try {
            return interpret0(statement);
        } catch (InterruptedException e) {
            interrupted = true;
            return new Value.Interrupt();
        }
    }

    Value interpret0(Statement statement) throws InterruptedException {
        return switch (statement) {
            case Statement.Declare declare -> {
                final List<String> names = declare.names();
                final Expression initializer = declare.initializer();
                final Value evaluated = interpreter.evaluate(initializer, declare.explicitType());
                assert evaluated != null;
                registerMulti(names, declare.declarationType(), evaluated);
                yield null;
            }
            case Statement.Assign assign -> {
                final List<Statement.Assign.Target> assignTargets = assign.targets();
                final int count = assignTargets.size();
                if (count == 1) {
                    final Statement.Assign.Target target = assignTargets.get(0);
                    final String name = target.name();
                    final Value tracked = walker.find(name);
                    assert tracked != null : "Variable not found: " + name;
                    final Type variableType = ValueType.extractAssignmentType(tracked);
                    final Value evaluated = interpreter.evaluate(assign.expression(), variableType);
                    if (evaluated instanceof Value.Interrupt) yield evaluated;
                    final Value updatedVariable = ValueCompute.updateVariable(this, tracked, target.accessPoints(), evaluated);
                    walker.update(name, updatedVariable);
                    var sharedMutation = sharedMutations.get(name);
                    if (sharedMutation != null) sharedMutation.append(this, tracked, updatedVariable);
                } else {
                    final Value evaluated = interpreter.evaluate(assign.expression(), null);
                    if (evaluated instanceof Value.Interrupt) yield evaluated;
                    for (int i = 0; i < count; i++) {
                        final Statement.Assign.Target target = assignTargets.get(i);
                        final String name = target.name();
                        final Value tracked = walker.find(name);
                        final Value deconstructed = ValueCompute.deconstruct(walker, evaluated, i);
                        final Value updatedVariable = ValueCompute.updateVariable(this, tracked, target.accessPoints(), deconstructed);
                        walker.update(name, updatedVariable);
                        var sharedMutation = sharedMutations.get(name);
                        if (sharedMutation != null) sharedMutation.append(this, tracked, updatedVariable);
                    }
                }
                yield null;
            }
            case Statement.Run run -> interpreter.evaluate(run.expression(), null);
            case Statement.Branch branch -> {
                final Value condition = interpreter.evaluate(branch.condition(), null);
                assert condition != null;
                if (condition instanceof Value.BooleanLiteral booleanLiteral) {
                    if (booleanLiteral.value()) {
                        yield interpret(branch.thenBranch());
                    } else if (branch.elseBranch() != null) {
                        yield interpret(branch.elseBranch());
                    }
                } else {
                    throw new RuntimeException("Condition must be a boolean");
                }
                yield null;
            }
            case Statement.Loop loop -> this.interpreterLoop.interpret(loop);
            case Statement.Break ignored -> {
                if (!insideLoop) throw new RuntimeException("Break statement outside of loop");
                yield new Value.Break();
            }
            case Statement.Continue ignored -> {
                if (!insideLoop) throw new RuntimeException("Continue statement outside of loop");
                yield new Value.Continue();
            }
            case Statement.Join join -> {
                enterScope();
                final Value value = interpret(join.block());
                exitScope();
                yield value;
            }
            case Statement.Spawn spawn -> this.interpreterSpawn.interpret(spawn);
            case Statement.Block block -> {
                this.walker.enterBlock();
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
                    assert currentFunction != null : "Return statement outside of function";
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
