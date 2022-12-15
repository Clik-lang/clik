package org.click.interpreter;

import org.click.Expression;
import org.click.Parameter;
import org.click.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Evaluator {
    private final Executor executor;
    private final ScopeWalker<Value> walker;

    private final Operator operator;
    private final ValueSerializer valueSerializer;

    public Evaluator(Executor executor, ScopeWalker<Value> walker) {
        this.executor = executor;
        this.walker = walker;

        this.operator = new Operator();
        this.valueSerializer = new ValueSerializer(walker);
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
            case Expression.Constant constant -> new Value.Constant(constant.type(), constant.value());
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
                    case null, default -> throw new RuntimeException("Expected struct, got: " + expression);
                };
            }
            case Expression.ArrayAccess arrayAccess -> {
                final Value array = evaluate(arrayAccess.array(), null);
                yield switch (array) {
                    case Value.Array arrayValue -> {
                        final Value index = evaluate(arrayAccess.index(), null);
                        if (!(index instanceof Value.Constant constant) || !(constant.value() instanceof Integer integer)) {
                            throw new RuntimeException("Expected constant, got: " + index);
                        }
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
                    case null, default -> throw new RuntimeException("Expected array/map, got: " + array);
                };
            }
            case Expression.Call call -> {
                final String name = call.name();
                final List<Value> evaluated = call.arguments().expressions().stream()
                        .map(expression -> evaluate(expression, null)).toList();
                if (name.equals("print")) {
                    StringBuilder builder = new StringBuilder();
                    for (Value value : evaluated) {
                        final String serialized = valueSerializer.serialize(value);
                        builder.append(serialized);
                    }
                    System.out.println(builder);
                } else {
                    yield executor.interpret(name, evaluated);
                }
                yield null;
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
                if (!explicitType.primitive()) {
                    // Struct
                    yield evaluate(new Expression.StructValue(explicitType.name(), initializationBlock.parameters()), null);
                }
                throw new RuntimeException("Expected struct, got: " + explicitType);
            }
            case Expression.Range init -> new Value.Range(evaluate(init.start(), null),
                    evaluate(init.end(), null),
                    evaluate(init.step(), null));
            case Expression.Binary binary -> {
                final Value left = evaluate(binary.left(), explicitType);
                final Value right = evaluate(binary.right(), explicitType);
                yield operator.operate(binary.operator(), left, right);
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
        final Value trackedType = walker.find(explicitType.name());
        if (trackedType instanceof Value.UnionDecl unionDecl && rawValue instanceof Value.Struct struct) {
            // Put struct in union wrapper
            final String unionName = explicitType.name();
            assert unionDecl.entries().containsKey(struct.name()) : "Struct not found in union: " + struct.name();
            return new Value.Union(unionName, rawValue);
        }
        // Valid type, no conversion needed
        return rawValue;
    }
}
