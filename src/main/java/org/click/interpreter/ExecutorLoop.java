package org.click.interpreter;

import org.click.Statement;
import org.click.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

public record ExecutorLoop(Executor executor, ScopeWalker<Value> walker) {
    void interpret(Statement.Loop loop) {
        final Context context = new Context(loop, new Phaser(), new ArrayList<>());
        context.phaser().register();
        if (loop.iterable() == null) {
            // Infinite loop
            assert !loop.fork() : "Forking infinite loops is not supported";
            while (true) {
                if (!iterate(context)) break;
            }
            return;
        }
        final Value iterable = executor.evaluate(loop.iterable(), null);
        switch (iterable) {
            case Value.Range range -> rangeLoop(context, range);
            case Value.ArrayRef arrayRef -> rangeArrayRef(context, arrayRef);
            default -> throw new RuntimeException("Expected iterable, got: " + iterable);
        }
        context.phaser().arriveAndAwaitAdvance();
        final List<ScopeWalker<Value>> walkers = context.executors().stream().map(Executor::walker).toList();
        ValueCompute.merge(walker, walkers);
    }

    record Context(Statement.Loop loop, Phaser phaser, List<Executor> executors) {
    }

    private boolean iterate(Context context) {
        final Statement.Loop loop = context.loop();
        final Statement body = loop.body();
        if (!loop.fork()) {
            // Single thread
            var previousLoop = executor.insideLoop;
            executor.insideLoop = true;
            final boolean result = iterateBody(executor, body);
            executor.insideLoop = previousLoop;
            return result;
        } else {
            // Virtual threads
            final Executor executor = executor().fork(true, true);
            context.executors().add(executor);
            context.phaser().register();
            Thread.startVirtualThread(() -> {
                iterateBody(executor, body);
                context.phaser().arriveAndDeregister();
            });
            return true; // Fork cannot be stopped from within
        }
    }

    private boolean iterateBody(Executor executor, Statement body) {
        final Value value = executor.interpret(body);
        if (value instanceof Value.Continue) return true;
        return !(value instanceof Value.Break);
    }

    private void rangeLoop(Context context, Value.Range range) {
        final Statement.Loop loop = context.loop();
        final List<Statement.Loop.Declaration> declarations = loop.declarations();

        final long start = ((Value.IntegerLiteral) range.start()).value();
        final long end = ((Value.IntegerLiteral) range.end()).value();
        final long step = ((Value.IntegerLiteral) range.step()).value();
        walker.enterBlock(executor);
        if (!declarations.isEmpty()) {
            assert declarations.size() == 1 && !declarations.get(0).ref() : "Invalid loop declaration: " + declarations;
            // Index declared
            final String variableName = declarations.get(0).name();
            walker.register(variableName, new Value.IntegerLiteral(Type.INT, start));
            for (long i = start; i < end; i += step) {
                walker.update(variableName, new Value.IntegerLiteral(Type.INT, i));
                if (!iterate(context)) break;
            }
        } else {
            // No declaration
            for (long i = start; i < end; i += step) {
                if (!iterate(context)) break;
            }
        }
        walker.exitBlock();
    }

    private void rangeArrayRef(Context context, Value.ArrayRef arrayRef) {
        final Statement.Loop loop = context.loop();
        final List<Statement.Loop.Declaration> declarations = loop.declarations();

        walker.enterBlock(executor);
        final List<Value> values = arrayRef.elements();
        if (!declarations.isEmpty()) {
            if (declarations.size() == 1 && !declarations.get(0).ref()) {
                // for-each loop
                final String variableName = declarations.get(0).name();
                walker.register(variableName, null);
                for (Value value : values) {
                    walker.update(variableName, value);
                    if (!iterate(context)) break;
                }
            } else if (declarations.size() == 2 && !declarations.get(0).ref() && !declarations.get(1).ref()) {
                // for-each counted loop
                final String indexName = declarations.get(0).name();
                final String variableName = declarations.get(1).name();
                walker.register(indexName, null);
                walker.register(variableName, null);
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    walker.update(indexName, new Value.IntegerLiteral(Type.INT, i));
                    walker.update(variableName, value);
                    if (!iterate(context)) break;
                }
            } else {
                // Ref loop
                final Type arrayType = arrayRef.type();
                if (!(arrayType instanceof Type.Array typeArray))
                    throw new RuntimeException("Expected array of structures, got: " + arrayType);
                final Value tracked = walker.find(typeArray.type().name());
                assert declarations.stream().allMatch(Statement.Loop.Declaration::ref) : "Invalid loop declaration: " + declarations;
                List<String> refs = declarations.stream().map(Statement.Loop.Declaration::name).toList();
                for (Statement.Loop.Declaration declaration : declarations) {
                    final String name = declaration.name();
                    final Type referenceType = switch (tracked) {
                        case Value.StructDecl structDecl -> structDecl.get(name);
                        default -> throw new RuntimeException("Unknown type: " + tracked);
                    };
                    walker.register(name, new Value.IntegerLiteral(referenceType, 0));
                }
                for (Value value : values) {
                    for (String refName : refs) {
                        final Value refValue = ((Value.Struct) value).parameters().get(refName);
                        walker.update(refName, refValue);
                    }
                    if (!iterate(context)) break;
                }
            }
        } else {
            // No declaration
            for (Value value : values) {
                if (!iterate(context)) break;
            }
        }
        walker.exitBlock();
    }
}
