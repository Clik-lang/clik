package org.click.interpreter;

import org.click.*;
import org.click.value.Value;
import org.click.value.ValueCompute;
import org.click.value.ValueOperator;
import org.click.value.ValueType;

import java.lang.foreign.MemorySegment;
import java.util.*;

public final class Evaluator {
    private final Executor executor;
    private final ScopeWalker<Value> walker;

    private final EvaluatorSelect evaluatorSelect;

    public Evaluator(Executor executor, ScopeWalker<Value> walker) {
        this.executor = executor;
        this.walker = walker;

        this.evaluatorSelect = new EvaluatorSelect(executor, walker);
    }

    public Value evaluate(Expression argument, Type explicitType) {
        final Value rawValue = switch (argument) {
            case Expression.Constant constant -> {
                final Value value = constant.value();
                if (value instanceof Value.FunctionDecl functionDecl) {
                    // Handle local function
                    if (this.executor.currentFunction() != null) {
                        // Local function
                        final Executor lambdaExecutor = this.executor.fork(executor.async, executor.insideLoop);
                        yield new Value.FunctionDecl(functionDecl.parameters(),
                                functionDecl.returnType(), functionDecl.body(), lambdaExecutor);
                    }
                } else if (value instanceof Value.UnionDecl unionDecl) {
                    // Register inline structs
                    for (Map.Entry<String, Value.StructDecl> entry : unionDecl.entries().entrySet()) {
                        final String name = entry.getKey();
                        final Value.StructDecl structDecl = entry.getValue();
                        if (structDecl != null) walker.register(name, structDecl);
                    }
                }
                yield value;
            }
            case Expression.Enum enumDeclaration -> {
                final Type type = enumDeclaration.type();
                Map<String, Value> evaluated = new HashMap<>();
                for (Map.Entry<String, Expression> entry : enumDeclaration.entries().entrySet()) {
                    evaluated.put(entry.getKey(), evaluate(entry.getValue(), type));
                }
                yield new Value.EnumDecl(type, evaluated);
            }
            case Expression.Variable variable -> {
                final String name = variable.name();
                final Value value = walker.find(name);
                if (value == null) {
                    throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked().keySet());
                }
                yield value;
            }
            case Expression.Access field -> {
                final Value expression = evaluate(field.object(), null);
                final AccessPoint accessPoint = field.accessPoint();
                Value result = expression;
                for (AccessPoint.Access access : accessPoint.accesses()) {
                    result = switch (result) {
                        case Value.Struct struct -> {
                            if (!(access instanceof AccessPoint.Field fieldAccess)) {
                                throw new RuntimeException("Invalid struct access: " + access);
                            }
                            final String name = fieldAccess.component();
                            yield struct.parameters().get(name);
                        }
                        case Value.EnumDecl enumDecl -> {
                            if (!(access instanceof AccessPoint.Field fieldAccess)) {
                                throw new RuntimeException("Invalid enum access: " + access);
                            }
                            final String component = fieldAccess.component();
                            final Value value = enumDecl.entries().get(component);
                            if (value == null) throw new RuntimeException("Enum entry not found: " + component);
                            yield value;
                        }
                        case Value.ArrayRef arrayRef -> {
                            if (!(access instanceof AccessPoint.Index indexAccess)) {
                                throw new RuntimeException("Invalid array access: " + access);
                            }
                            final Value index = evaluate(indexAccess.expression(), null);
                            if (!(index instanceof Value.IntegerLiteral integerLiteral)) {
                                throw new RuntimeException("Expected constant, got: " + index);
                            }
                            final int integer = (int) integerLiteral.value();
                            final List<Value> content = arrayRef.elements();
                            if (integer < 0 || integer >= content.size())
                                throw new RuntimeException("Index out of bounds: " + integer + " in " + content);
                            yield content.get(integer);
                        }
                        case Value.ArrayValue arrayValue -> {
                            if (!(access instanceof AccessPoint.Index indexAccess)) {
                                throw new RuntimeException("Invalid array access: " + access);
                            }
                            final Value indexValue = evaluate(indexAccess.expression(), null);
                            if (!(indexValue instanceof Value.IntegerLiteral integerLiteral)) {
                                throw new RuntimeException("Expected constant, got: " + indexValue);
                            }
                            final long index = integerLiteral.value() * ValueType.sizeOf(arrayValue.arrayType().type());
                            final Type type = Objects.requireNonNullElse(indexAccess.transmuteType(), arrayValue.arrayType().type());
                            final MemorySegment data = arrayValue.data();
                            if (index < 0 || index >= data.byteSize())
                                throw new RuntimeException("Index out of bounds: " + index + " in " + data.byteSize());
                            yield ValueCompute.lookupArrayBuffer(type, data, index);
                        }
                        case Value.Map map -> {
                            if (!(access instanceof AccessPoint.Index indexAccess)) {
                                throw new RuntimeException("Invalid map access: " + access);
                            }
                            final Value key = evaluate(indexAccess.expression(), map.mapType().key());
                            yield map.entries().get(key);
                        }
                        default -> throw new RuntimeException("Expected struct, got: " + expression);
                    };
                }
                yield result;
            }
            case Expression.VariableAwait variableAwait -> {
                final String name = variableAwait.name();
                final Value value = walker.find(name);
                if (value == null) {
                    throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked().keySet());
                }
                final Executor.SharedMutation sharedMutation = executor.sharedMutations.get(name);
                if (sharedMutation == null) throw new RuntimeException("Variable not shared: " + name);
                yield sharedMutation.await(value);
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
                final Executor callExecutor = Objects.requireNonNullElse(functionDecl.lambdaExecutor(), executor);
                final Executor fork = callExecutor.fork(executor.async, executor.insideLoop);
                yield fork.interpret(name, functionDecl, evaluated);
            }
            case Expression.Select select -> this.evaluatorSelect.evaluate(select, explicitType);
            case Expression.Initialization initialization -> {
                final Type type = Objects.requireNonNullElse(initialization.type(), explicitType);
                final Parameter.Passed passed = initialization.parameters();
                yield switch (type) {
                    case Type.Identifier identifier -> {
                        final String name = identifier.name();
                        final Value.StructDecl struct = (Value.StructDecl) walker.find(name);
                        final List<Parameter> parameters = struct.parameters();
                        Map<String, Value> evaluated = new HashMap<>();
                        for (Parameter param : struct.parameters()) {
                            final Expression value = passed.find(parameters, param);
                            if (value == null) throw new RuntimeException("Missing field: " + param.name());
                            evaluated.put(param.name(), evaluate(value, param.type()));
                        }
                        yield new Value.Struct(name, evaluated);
                    }
                    case Type.Array arrayType -> ValueCompute.computeArray(executor, arrayType, passed.expressions());
                    case Type.Map mapType -> {
                        if (!(passed instanceof Parameter.Passed.Mapped mapped))
                            throw new RuntimeException("Expected mapped parameters, got: " + passed);
                        Map<Value, Value> evaluatedEntries = new HashMap<>();
                        for (var entry : mapped.entries().entrySet()) {
                            final Value key = evaluate(entry.getKey(), mapType.key());
                            final Value value = evaluate(entry.getValue(), mapType.value());
                            evaluatedEntries.put(key, value);
                        }
                        yield new Value.Map(mapType, evaluatedEntries);
                    }
                    default ->
                            throw new RuntimeException("Invalid initialization: " + initialization + " " + explicitType);
                };
            }
            case Expression.Range init -> new Value.Range(evaluate(init.start(), null),
                    evaluate(init.end(), null),
                    evaluate(init.step(), null));
            case Expression.Binary binary -> {
                final Value left = evaluate(binary.left(), explicitType);
                final Value right = evaluate(binary.right(), explicitType);
                yield ValueOperator.operate(binary.operator(), left, right);
            }
            case Expression.Unary unary -> {
                final Value value = evaluate(unary.expression(), explicitType);
                if (unary.operator() == Token.Type.EXCLAMATION) {
                    if (value instanceof Value.BooleanLiteral booleanLiteral) {
                        yield new Value.BooleanLiteral(!booleanLiteral.value());
                    } else {
                        throw new RuntimeException("Expected boolean, got: " + value);
                    }
                } else {
                    throw new RuntimeException("Unsupported unary operator: " + unary.operator());
                }
            }
        };

        if (explicitType == null) {
            // No type defined, use inferred type
            return rawValue;
        }
        if (explicitType instanceof Type.Primitive) {
            // Primitive type, no conversion needed
            // TODO: downcast
            return rawValue;
        }

        if (explicitType instanceof Type.Identifier identifier) {
            final String name = identifier.name();
            final Value trackedType = walker.find(name);
            // Union cast
            if (trackedType instanceof Value.UnionDecl unionDecl && rawValue instanceof Value.Struct struct) {
                // Put struct in union wrapper
                assert unionDecl.entries().containsKey(struct.name()) : "Struct not found in union: " + struct.name();
                return new Value.Union(name, rawValue);
            }
            // Enum cast
            if (trackedType instanceof Value.EnumDecl enumDecl && !(rawValue instanceof Value.Enum)) {
                final Map<String, Value> entries = enumDecl.entries();
                final String enumName = entries.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(rawValue))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
                // Put struct in enum wrapper
                return new Value.Enum(name, enumName);
            }
        }
        // Valid type, no conversion needed
        return rawValue;
    }
}
