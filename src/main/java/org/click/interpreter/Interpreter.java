package org.click.interpreter;

import org.click.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Interpreter {
    private final List<Statement> statements;
    private final ScopeWalker<Value> walker = new ScopeWalker<>();

    public Interpreter(List<Statement> statements) {
        this.statements = statements;

        this.walker.enterBlock();
        // Global scope
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                final Value value = evaluate(declare.initializer(), declare.explicitType());
                walker.register(declare.name(), value);
            }
        }
    }

    public Value interpret(String function, List<Value> parameters) {
        final Value call = walker.find(function);
        if (!(call instanceof Value.FunctionDecl functionDeclaration)) {
            throw new RuntimeException("Function not found: " + call + " " + function);
        }

        walker.enterBlock();
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = functionDeclaration.parameters().get(i);
            final Value value = parameters.get(i);
            assert value != null;
            walker.register(parameter.name(), value);
        }

        Value result = null;
        for (Statement statement : functionDeclaration.body()) {
            result = execute(functionDeclaration, statement);
            if (result != null) break;
        }
        walker.exitBlock();
        return result;
    }

    private Type extractType(Value expression) {
        return switch (expression) {
            case Value.Constant constant -> {
                final Object value = constant.value();
                if (value instanceof String) yield Type.STRING;
                if (value instanceof Integer) yield Type.I32;
                throw new RuntimeException("Unknown constant type: " + value);
            }
            case Value.Struct struct -> Type.of(struct.name());
            case null, default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }

    private Value execute(Value.FunctionDecl function, Statement statement) {
        switch (statement) {
            case Statement.Declare declare -> {
                final String name = declare.name();
                final Expression initializer = declare.initializer();
                final Value evaluated = evaluate(initializer, declare.explicitType());
                assert evaluated != null;
                walker.register(name, evaluated);
            }
            case Statement.Assign assign -> {
                final Type variableType = extractType(walker.find(assign.name()));
                final Value evaluated = evaluate(assign.expression(), variableType);
                walker.update(assign.name(), evaluated);
            }
            case Statement.Call call -> {
                return evaluate(new Expression.Call(call.name(), call.arguments()), null);
            }
            case Statement.Branch branch -> {
                final Value condition = evaluate(branch.condition(), null);
                assert condition != null;
                if (condition instanceof Value.Constant constant) {
                    if ((boolean) constant.value()) {
                        for (Statement thenBranch : branch.thenBranch()) {
                            execute(function, thenBranch);
                        }
                    } else if (branch.elseBranch() != null) {
                        for (Statement elseBranch : branch.elseBranch()) {
                            execute(function, elseBranch);
                        }
                    }
                } else {
                    throw new RuntimeException("Condition must be a boolean");
                }
            }
            case Statement.Loop loop -> {
                if (loop.iterable() == null) {
                    // Infinite loop
                    while (true) {
                        for (Statement body : loop.body()) {
                            execute(function, body);
                        }
                    }
                } else {
                    final List<Statement.Loop.Declaration> declarations = loop.declarations();
                    final Value iterable = evaluate(loop.iterable(), null);
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
                                    for (Statement body : loop.body()) execute(function, body);
                                }
                            } else {
                                // No declaration
                                for (int i = start; i < end; i += step) {
                                    for (Statement body : loop.body()) execute(function, body);
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
                                        for (Statement body : loop.body()) execute(function, body);
                                    }
                                } else if (declarations.size() == 2 && !declarations.get(0).ref() && !declarations.get(1).ref()) {
                                    // for-each counted loop
                                    final String variableName = declarations.get(0).name();
                                    final String indexName = declarations.get(1).name();
                                    walker.register(variableName, null);
                                    walker.register(indexName, null);
                                    for (int i = 0; i < values.size(); i++) {
                                        final Value value = values.get(i);
                                        walker.update(variableName, value);
                                        walker.update(indexName, new Value.Constant(Type.I32, i));
                                        for (Statement body : loop.body()) execute(function, body);
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
                                        for (Statement body : loop.body()) execute(function, body);
                                    }
                                }
                            } else {
                                assert declarations.isEmpty() : "Unknown declaration type: " + declarations;
                                // No declaration
                                for (Value value : values) {
                                    for (Statement body : loop.body()) execute(function, body);
                                }
                            }
                            walker.exitBlock();
                        }
                        case null, default -> throw new RuntimeException("Expected iterable, got: " + iterable);
                    }
                }
            }
            case Statement.Block block -> {
                this.walker.enterBlock();
                for (Statement inner : block.statements()) {
                    execute(function, inner);
                }
                this.walker.exitBlock();
            }
            case Statement.Return returnStatement -> {
                final Expression expression = returnStatement.expression();
                if (expression != null) {
                    final Type returnType = function.returnType();
                    return evaluate(expression, returnType);
                }
            }
            case null, default -> {
            }
        }
        return null;
    }

    private Value evaluate(Expression argument, Type explicitType) {
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
                    for (Value value : evaluated) builder.append(serialize(value));
                    System.out.println(builder);
                } else {
                    yield interpret(name, evaluated);
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
                yield operate(binary.operator(), left, right);
            }
            case null -> throw new RuntimeException("Unknown expression: " + argument);
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

    private Value operate(Token operator, Value left, Value right) {
        if (left instanceof Value.Constant leftConstant && right instanceof Value.Constant rightConstant) {
            final Object leftValue = leftConstant.value();
            final Object rightValue = rightConstant.value();
            if (leftValue instanceof Integer leftInt && rightValue instanceof Integer rightInt) {
                boolean isComparison = false;
                final int result = switch (operator.type()) {
                    case PLUS -> leftInt + rightInt;
                    case MINUS -> leftInt - rightInt;
                    case STAR -> leftInt * rightInt;
                    case SLASH -> leftInt / rightInt;
                    case EQUAL_EQUAL -> {
                        isComparison = true;
                        yield leftInt.equals(rightInt) ? 1 : 0;
                    }
                    case GREATER -> {
                        isComparison = true;
                        yield leftInt > rightInt ? 1 : 0;
                    }
                    case GREATER_EQUAL -> {
                        isComparison = true;
                        yield leftInt >= rightInt ? 1 : 0;
                    }
                    case LESS -> {
                        isComparison = true;
                        yield leftInt < rightInt ? 1 : 0;
                    }
                    case LESS_EQUAL -> {
                        isComparison = true;
                        yield leftInt <= rightInt ? 1 : 0;
                    }
                    default -> throw new RuntimeException("Unknown operator: " + operator);
                };
                return isComparison ? new Value.Constant(Type.BOOL, result == 1) : new Value.Constant(Type.I32, result);
            } else if (leftValue instanceof Boolean leftBool && rightValue instanceof Boolean rightBool) {
                final boolean result = switch (operator.type()) {
                    case OR -> leftBool || rightBool;
                    case AND -> leftBool && rightBool;
                    case EQUAL_EQUAL -> leftBool.equals(rightBool);
                    default -> throw new RuntimeException("Unknown operator: " + operator);
                };
                return new Value.Constant(Type.BOOL, result);
            } else {
                throw new RuntimeException("Unknown types: " + leftValue.getClass() + " and " + rightValue.getClass());
            }
        } else {
            throw new RuntimeException("Unknown types: " + left + " and " + right);
        }
    }

    private String serialize(Value expression) {
        return switch (expression) {
            case Value.Constant constant -> constant.value().toString();
            case Value.Struct struct -> {
                if (!(walker.find(struct.name()) instanceof Value.StructDecl structDeclaration)) {
                    throw new RuntimeException("Struct not found: " + struct.name());
                }
                var parameters = structDeclaration.parameters();
                final StringBuilder builder = new StringBuilder();
                builder.append(struct.name()).append("{");
                for (int i = 0; i < parameters.size(); i++) {
                    final Parameter param = parameters.get(i);
                    final Value field = struct.parameters().get(param.name());
                    builder.append(serialize(field));
                    if (i < parameters.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append("}");
                yield builder.toString();
            }
            case Value.Union union -> {
                if (!(walker.find(union.name()) instanceof Value.UnionDecl)) {
                    throw new RuntimeException("Union not found: " + union.name());
                }
                final Value field = union.value();
                yield union.name() + "." + serialize(field);
            }
            case Value.Array array -> {
                final List<Value> values = array.values();
                final StringBuilder builder = new StringBuilder();
                builder.append("[");
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    builder.append(serialize(value));
                    if (i < values.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append("]");
                yield builder.toString();
            }
            case null, default -> throw new RuntimeException("Unknown expression: " + expression);
        };
    }

    public void stop() {
        walker.exitBlock();
    }
}
