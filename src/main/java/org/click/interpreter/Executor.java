package org.click.interpreter;

import org.click.Scanner;
import org.click.*;
import org.click.external.ExternalFunction;
import org.click.value.Value;
import org.click.value.ValueCompute;
import org.click.value.ValueType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
        final VM.Context context = new VM.Context(this.context.directory(), copy, this.context.externals());
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
        final Value function = walker.find(name);
        return switch (function) {
            case Value.FunctionDecl functionDecl -> {
                final Executor callExecutor = Objects.requireNonNullElse(functionDecl.lambdaExecutor(), this);
                final Executor fork = callExecutor.fork(this.async, this.insideLoop);
                yield fork.interpret(name, functionDecl, parameters);
            }
            case Value.ExternFunctionDecl ignored -> {
                final Map<String, ExternalFunction> functions = this.context().externals();
                final ExternalFunction builtin = functions.get(name);
                if (builtin == null) throw new RuntimeException("External function impl not found: " + name);
                yield builtin.run(parameters.toArray(Value[]::new));
            }
            default -> throw new IllegalStateException("Unexpected value: " + function);
        };
    }

    public Value evaluate(Expression expression, Type explicitType) {
        return interpreter.evaluate(expression, explicitType);
    }

    public void registerMulti(List<String> names, DeclarationType declarationType, Value value) {
        if (value instanceof Value.FunctionDecl || value instanceof Value.StructDecl ||
                value instanceof Value.EnumDecl || value instanceof Value.UnionDecl) {
            if (declarationType != DeclarationType.CONSTANT) {
                throw new RuntimeException("Type declaration must be constant");
            }
        }
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            if (walker.find(name) != null)
                throw new RuntimeException("Variable already declared: " + name);
            final Value deconstructed = names.size() > 1 ? ValueCompute.deconstruct(walker, value, i) : value;
            walker.register(name, deconstructed);
            if (declarationType == DeclarationType.SHARED) this.sharedMutations.put(name, new SharedMutation(value));
        }
    }

    void interpret(List<Statement> statements) {
        for (Statement statement : statements) {
            interpret(statement);
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
                Type explicitType = null;
                if (count == 1) {
                    final Statement.Assign.Target target = assignTargets.get(0);
                    final String name = target.name();
                    final Value targetValue = evaluate(new Expression.Access(new Expression.Variable(name), target.accessPoints()), null);
                    explicitType = ValueType.extractAssignmentType(targetValue);
                }
                final Value evaluated = interpreter.evaluate(assign.expression(), explicitType);
                if (evaluated instanceof Value.Interrupt) yield evaluated;
                for (int i = 0; i < count; i++) {
                    final Statement.Assign.Target target = assignTargets.get(i);
                    final String name = target.name();
                    final Value tracked = walker.find(name);
                    assert tracked != null : "Variable not found: " + name;
                    final Value deconstructed = count > 1 ? ValueCompute.deconstruct(walker, evaluated, i) : evaluated;
                    final Value updatedVariable = ValueCompute.updateVariable(this, tracked, target.accessPoints(), deconstructed);
                    walker.update(name, updatedVariable);
                    var sharedMutation = sharedMutations.get(name);
                    if (sharedMutation != null) sharedMutation.append(this, tracked, updatedVariable);
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
            case Statement.LoadLibrary loadLibrary -> {
                final String filePath = loadLibrary.path();
                final String source;
                try {
                    final Path path = this.context.directory().resolve(filePath);
                    source = Files.readString(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                final List<Token> tokens = new Scanner(source).scanTokens();
                final List<Statement> statements = new Parser(tokens).parse();
                interpret(statements);
                yield null;
            }
        };
    }
}
