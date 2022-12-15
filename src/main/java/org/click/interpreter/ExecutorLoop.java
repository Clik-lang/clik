package org.click.interpreter;

import org.click.Statement;
import org.click.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Phaser;

public record ExecutorLoop(Executor executor, ScopeWalker<Value> walker) {
    void interpret(Statement.Loop loop) {
        final Context context = new Context(loop, new Phaser(), new ArrayList<>());
        final Value iterable = executor.evaluate(loop.iterable(), null);
        switch (iterable) {
            case Value.Range range -> rangeLoop(context, range);
            case Value.Array array -> rangeArray(context, array);
            default -> throw new RuntimeException("Expected iterable, got: " + iterable);
        }
        context.phaser().register();
        context.phaser().arriveAndAwaitAdvance();
        final List<ScopeWalker<Value>> walkers = context.executors().stream().map(Executor::walker).toList();
        ValueMerger.merge(walker, walkers);
    }

    record Context(Statement.Loop loop, Phaser phaser, List<Executor> executors) {
    }

    private void iterate(Context context) {
        final Statement.Loop loop = context.loop();
        final List<Statement> body = loop.body();
        if (!loop.fork()) {
            // Single thread
            iterateBody(executor, body);
        } else {
            // Virtual threads
            final Executor executor = executor().fork(true);
            context.executors().add(executor);
            context.phaser().register();
            Thread.startVirtualThread(() -> {
                iterateBody(executor, body);
                context.phaser().arriveAndDeregister();
            });
        }
    }

    private void iterateBody(Executor executor, List<Statement> body) {
        for (Statement statement : body) {
            final Value value = executor.interpret(statement);
            if (value instanceof Value.Break) {
                break;
            }
        }
    }

    private void rangeLoop(Context context, Value.Range range) {
        final Statement.Loop loop = context.loop();
        final List<Statement.Loop.Declaration> declarations = loop.declarations();

        final int start = (int) ((Value.Constant) range.start()).value();
        final int end = (int) ((Value.Constant) range.end()).value();
        final int step = (int) ((Value.Constant) range.step()).value();
        walker.enterBlock();
        if (!declarations.isEmpty()) {
            assert declarations.size() == 1 && !declarations.get(0).ref() : "Invalid loop declaration: " + declarations;
            // Index declared
            final String variableName = declarations.get(0).name();
            walker.register(variableName, new Value.Constant(Type.I32, start));
            for (int i = start; i < end; i += step) {
                walker.update(variableName, new Value.Constant(Type.I32, i));
                iterate(context);
            }
        } else {
            // No declaration
            for (int i = start; i < end; i += step) {
                iterate(context);
            }
        }
        walker.exitBlock();
    }

    private void rangeArray(Context context, Value.Array array) {
        final Statement.Loop loop = context.loop();
        final List<Statement.Loop.Declaration> declarations = loop.declarations();

        walker.enterBlock();
        final List<Value> values = array.values();
        if (!declarations.isEmpty()) {
            final Type arrayType = array.type();
            final Value tracked = walker.find(arrayType.name());
            if (declarations.size() == 1 && !declarations.get(0).ref()) {
                // for-each loop
                final String variableName = declarations.get(0).name();
                walker.register(variableName, null);
                for (Value value : values) {
                    walker.update(variableName, value);
                    iterate(context);
                }
            } else if (declarations.size() == 2 && !declarations.get(0).ref() && !declarations.get(1).ref()) {
                // for-each counted loop
                final String indexName = declarations.get(0).name();
                final String variableName = declarations.get(1).name();
                walker.register(indexName, null);
                walker.register(variableName, null);
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    walker.update(indexName, new Value.Constant(Type.I32, i));
                    walker.update(variableName, value);
                    iterate(context);
                }
            } else {
                // Ref loop
                assert declarations.stream().allMatch(Statement.Loop.Declaration::ref) : "Invalid loop declaration: " + declarations;
                List<String> refs = declarations.stream().map(Statement.Loop.Declaration::name).toList();
                for (Statement.Loop.Declaration declaration : declarations) {
                    final String name = declaration.name();
                    final Type referenceType = switch (tracked) {
                        case Value.StructDecl structDecl -> structDecl.get(name);
                        default -> throw new RuntimeException("Unknown type: " + tracked);
                    };
                    walker.register(name, new Value.Constant(referenceType, 0));
                }
                for (Value value : values) {
                    for (String refName : refs) {
                        final Value refValue = ((Value.Struct) value).parameters().get(refName);
                        walker.update(refName, refValue);
                    }
                    iterate(context);
                }
            }
        } else {
            // No declaration
            for (Value value : values) {
                iterate(context);
            }
        }
        walker.exitBlock();
    }
}
