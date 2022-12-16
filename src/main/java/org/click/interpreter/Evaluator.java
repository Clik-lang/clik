package org.click.interpreter;

import org.click.Expression;
import org.click.Parameter;
import org.click.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class Evaluator {
    private final Executor executor;
    private final ScopeWalker<Value> walker;

    public Evaluator(Executor executor, ScopeWalker<Value> walker) {
        this.executor = executor;
        this.walker = walker;
    }

    public Value evaluate(Expression argument, Type explicitType) {
        final Value rawValue = switch (argument) {
            case Expression.Function functionDeclaration ->
                    new Value.FunctionDecl(functionDeclaration.parameters(), functionDeclaration.returnType(), functionDeclaration.body());
            case Expression.Struct structDeclaration -> new Value.StructDecl(structDeclaration.parameters());
            case Expression.Enum enumDeclaration -> {
                final Type type = enumDeclaration.type();
                Map<String, Value> evaluated = new HashMap<>();
                for (Map.Entry<String, Expression> entry : enumDeclaration.entries().entrySet()) {
                    evaluated.put(entry.getKey(), evaluate(entry.getValue(), type));
                }
                yield new Value.EnumDecl(type, evaluated);
            }
            case Expression.Union unionDeclaration -> {
                Map<String, Value.StructDecl> entries = new HashMap<>();
                for (Map.Entry<String, Expression.Struct> entry : unionDeclaration.entries().entrySet()) {
                    final String name = entry.getKey();
                    final Expression.Struct struct = entry.getValue();
                    final Value.StructDecl structDecl;
                    if (struct == null) {
                        if (!(walker.find(name) instanceof Value.StructDecl structDeclaration)) {
                            throw new RuntimeException("Struct not found: " + name);
                        }
                        structDecl = structDeclaration;
                    } else {
                        structDecl = (Value.StructDecl) evaluate(struct, null);
                        walker.register(name, structDecl);
                    }
                    entries.put(name, structDecl);
                }
                yield new Value.UnionDecl(entries);
            }
            case Expression.IntegerLiteral integerLiteral ->
                    new Value.IntegerLiteral(integerLiteral.type(), integerLiteral.value());
            case Expression.FloatLiteral floatLiteral ->
                    new Value.FloatLiteral(floatLiteral.type(), floatLiteral.value());
            case Expression.BooleanLiteral booleanLiteral -> new Value.BooleanLiteral(booleanLiteral.value());
            case Expression.StringLiteral stringLiteral -> new Value.StringLiteral(stringLiteral.value());
            case Expression.Variable variable -> {
                final String name = variable.name();
                final Value value = walker.find(name);
                if (value == null) {
                    throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked.keySet());
                }
                yield value;
            }
            case Expression.Group group -> evaluate(group.expression(), explicitType);
            case Expression.Field field -> {
                final Value expression = evaluate(field.object(), null);
                yield switch (expression) {
                    case Value.Struct struct -> struct.parameters().get(field.name());
                    case Value.EnumDecl enumDecl -> {
                        final Value value = enumDecl.entries().get(field.name());
                        if (value == null) throw new RuntimeException("Enum entry not found: " + field.name());
                        yield value;
                    }
                    default -> throw new RuntimeException("Expected struct, got: " + expression);
                };
            }
            case Expression.VariableAwait variableAwait -> {
                final String name = variableAwait.name();
                final Value value = walker.find(name);
                if (value == null) {
                    throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked.keySet());
                }
                final AtomicReference<Value> sharedRef = executor.sharedMutations().get(name);
                if (sharedRef == null) {
                    throw new RuntimeException("Variable not shared: " + name);
                }
                Value result;
                while ((result = sharedRef.get()).equals(value)) {
                    Thread.yield();
                }
                yield result;
            }
            case Expression.ArrayAccess arrayAccess -> {
                final Value array = evaluate(arrayAccess.array(), null);
                yield switch (array) {
                    case Value.Array arrayValue -> {
                        final Value index = evaluate(arrayAccess.index(), null);
                        if (!(index instanceof Value.IntegerLiteral integerLiteral)) {
                            throw new RuntimeException("Expected constant, got: " + index);
                        }
                        final int integer = (int) integerLiteral.value();
                        final List<Value> content = arrayValue.values();
                        if (integer < 0 || integer >= content.size())
                            throw new RuntimeException("Index out of bounds: " + integer + " in " + content);
                        yield content.get(integer);
                    }
                    case Value.Map mapValue -> {
                        final Value index = evaluate(arrayAccess.index(), mapValue.keyType());
                        final Value result = mapValue.entries().get(index);
                        if (result == null) throw new RuntimeException("Key not found: " + index);
                        yield result;
                    }
                    default -> throw new RuntimeException("Expected array/map, got: " + array);
                };
            }
            case Expression.Call call -> {
                final String name = call.name();
                final Value.FunctionDecl functionDecl = (Value.FunctionDecl) executor.walker().find(name);
                assert functionDecl != null : "Function " + name + " not found";
                final List<Parameter> params = functionDecl.parameters();
                final List<Expression> expressions = call.arguments().expressions();
                assert params.size() == expressions.size() : name + ": Expected " + params.size() + " arguments, got " + expressions.size();
                List<Value> evaluated = new ArrayList<>();
                for (int i = 0; i < call.arguments().expressions().size(); i++) {
                    final Parameter param = params.get(i);
                    final Expression expression = expressions.get(i);
                    final Value value = executor.evaluate(expression, param.type());
                    evaluated.add(value);
                }
                yield executor.interpret(name, evaluated);
            }
            case Expression.StructValue structValue -> {
                final Value.StructDecl struct = (Value.StructDecl) walker.find(structValue.name());
                final List<Parameter> parameters = struct.parameters();
                Map<String, Value> evaluated = new HashMap<>();
                for (Parameter param : struct.parameters()) {
                    final Expression value = structValue.fields().find(parameters, param);
                    if (value == null) throw new RuntimeException("Missing field: " + param.name());
                    evaluated.put(param.name(), evaluate(value, param.type()));
                }
                yield new Value.Struct(structValue.name(), evaluated);
            }
            case Expression.ArrayValue arrayValue -> {
                if (!(arrayValue.parameters() instanceof Parameter.Passed.Positional positional))
                    throw new RuntimeException("Expected positional parameters");
                final List<Value> evaluated = positional.expressions().stream()
                        .map(expression -> evaluate(expression, arrayValue.type())).toList();
                yield new Value.Array(arrayValue.type(), evaluated);
            }
            case Expression.MapValue mapValue -> {
                Map<Value, Value> evaluatedEntries = new HashMap<>();
                for (var entry : mapValue.entries().entrySet()) {
                    final Value key = evaluate(entry.getKey(), mapValue.keyType());
                    final Value value = evaluate(entry.getValue(), mapValue.valueType());
                    evaluatedEntries.put(key, value);
                }
                yield new Value.Map(mapValue.keyType(), mapValue.valueType(), evaluatedEntries);
            }
            case Expression.InitializationBlock initializationBlock -> {
                // Retrieve explicit type from context
                if (explicitType == null) throw new RuntimeException("Expected explicit type for initialization block");
                if (explicitType instanceof Type.Identifier identifier) {
                    // Struct
                    yield evaluate(new Expression.StructValue(identifier.name(), initializationBlock.parameters()), null);
                }
                throw new RuntimeException("Expected struct, got: " + explicitType);
            }
            case Expression.Range init -> new Value.Range(evaluate(init.start(), null),
                    evaluate(init.end(), null),
                    evaluate(init.step(), null));
            case Expression.Binary binary -> {
                final Value left = evaluate(binary.left(), explicitType);
                final Value right = evaluate(binary.right(), explicitType);
                yield ValueOperator.operate(binary.operator(), left, right);
            }
        };

        if (explicitType == null) {
            // No type defined, use inferred type
            return rawValue;
        }
        if (explicitType.primitive()) {
            // Primitive type, no conversion needed
            // TODO: downcast
            return rawValue;
        }

        if (explicitType instanceof Type.Identifier identifier) {
            final String unionName = identifier.name();
            final Value trackedType = walker.find(unionName);
            if (trackedType instanceof Value.UnionDecl unionDecl && rawValue instanceof Value.Struct struct) {
                // Put struct in union wrapper
                assert unionDecl.entries().containsKey(struct.name()) : "Struct not found in union: " + struct.name();
                return new Value.Union(unionName, rawValue);
            }
        }
        // Valid type, no conversion needed
        return rawValue;
    }
}
