package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.Type;
import org.click.value.Value;

import java.util.List;

import static org.click.Ast.Statement;

public record ExecutorLoop(Executor executor, ScopeWalker<Value> walker) {
    Value interpret(Statement.Loop loop) {
        var previousLoop = executor.insideLoop;
        executor.insideLoop = true;
        this.walker.enterBlock();
        if (loop.iterable() == null) {
            // Infinite loop
            //noinspection StatementWithEmptyBody
            while (iterate(loop.body())) ;
        } else {
            final Value iterable = executor.evaluate(loop.iterable(), null);
            if (iterable instanceof Value.Array arrayRef) {
                loop(loop, arrayRef);
            } else {
                throw new RuntimeException("Expected iterable, got: " + iterable);
            }
        }
        executor.insideLoop = previousLoop;
        this.walker.exitBlock();
        return null;
    }

    private boolean iterate(Statement body) {
        final Value value = executor.interpret(body);
        if (value instanceof Value.Continue) return true;
        return !(value instanceof Value.Break) && !(value instanceof Value.Interrupt);
    }

    private void loop(Statement.Loop loop, Value.Array array) {
        final List<Statement.Loop.Declaration> declarations = loop.declarations();
        final Statement body = loop.body();
        final Type.Array arrayType = array.arrayType();
        final List<Value> values = array.elements();
        if (!declarations.isEmpty()) {
            if (declarations.size() == 1 && !declarations.get(0).ref()) {
                // for-each loop
                final String variableName = declarations.get(0).name();
                walker.register(variableName, null);
                for (Value value : values) {
                    walker.update(variableName, value);
                    if (!iterate(body)) break;
                }
            } else if (declarations.size() == 2 && !declarations.get(0).ref() && !declarations.get(1).ref()) {
                // for-each counted loop
                final String indexName = declarations.get(0).name();
                final String variableName = declarations.get(1).name();
                walker.register(indexName, null);
                walker.register(variableName, null);
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    walker.update(indexName, new Value.NumberLiteral(i));
                    walker.update(variableName, value);
                    if (!iterate(body)) break;
                }
            } else {
                // Ref loop
                assert declarations.stream().allMatch(Statement.Loop.Declaration::ref) : "Invalid loop declaration: " + declarations;
                List<String> refs = declarations.stream().map(Statement.Loop.Declaration::name).toList();
                for (Statement.Loop.Declaration declaration : declarations) {
                    final String name = declaration.name();
                    walker.register(name, new Value.NumberLiteral(0));
                }
                for (Value value : values) {
                    for (String refName : refs) {
                        final Value refValue = ((Value.Struct) value).parameters().get(refName);
                        walker.update(refName, refValue);
                    }
                    if (!iterate(body)) break;
                }
            }
        } else {
            // No declaration
            for (Value ignored : values) {
                if (!iterate(body)) break;
            }
        }
    }
}
