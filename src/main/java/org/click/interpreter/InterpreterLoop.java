package org.click.interpreter;

import org.click.Statement;
import org.click.Type;

import java.util.List;

public record InterpreterLoop(ScopeWalker<Value> walker) {
    void interpret(Interpreter interpreter, Statement.Loop loop) {
        final List<Statement.Loop.Declaration> declarations = loop.declarations();
        final Value iterable = interpreter.evaluate(loop.iterable(), null);
        switch (iterable) {
            case Value.Range range -> {
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
                        for (Statement body : loop.body()) interpreter.execute(body);
                    }
                } else {
                    // No declaration
                    for (int i = start; i < end; i += step) {
                        for (Statement body : loop.body()) interpreter.execute(body);
                    }
                }
                walker.exitBlock();
            }
            case Value.Array array -> {
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
                            for (Statement body : loop.body()) interpreter.execute(body);
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
                            for (Statement body : loop.body()) interpreter.execute(body);
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
                            for (Statement body : loop.body()) interpreter.execute(body);
                        }
                    }
                } else {
                    // No declaration
                    for (Value value : values) {
                        for (Statement body : loop.body()) interpreter.execute(body);
                    }
                }
                walker.exitBlock();
            }
            case null, default -> throw new RuntimeException("Expected iterable, got: " + iterable);
        }
    }
}
