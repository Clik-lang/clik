package org.click.interpreter;

import org.click.ScopeWalker;
import org.click.Token;
import org.click.Type;
import org.click.value.Value;
import org.click.value.ValueOperator;
import org.click.value.ValueType;

import java.util.*;
import java.util.stream.LongStream;

import static org.click.Ast.*;

public final class Evaluator {
    private final Executor executor;
    private final ScopeWalker<Value> walker;
    private Value contextual;

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
            case Expression.Contextual ignored -> this.contextual;
            case Expression.Access access -> {
                final Value expression = evaluate(access.object(), null);
                Value result = expression;
                for (AccessPoint accessPoint : access.accessPoints()) {
                    result = switch (result) {
                        case Value.Struct struct -> {
                            if (!(accessPoint instanceof AccessPoint.Field fieldAccess))
                                throw new RuntimeException("Invalid struct access: " + access);
                            final String name = fieldAccess.component();
                            yield struct.parameters().get(name);
                        }
                        case Value.EnumDecl enumDecl -> {
                            if (!(accessPoint instanceof AccessPoint.Field fieldAccess))
                                throw new RuntimeException("Invalid enum access: " + access);
                            final String component = fieldAccess.component();
                            final Value value = enumDecl.entries().get(component);
                            if (value == null) throw new RuntimeException("Enum entry not found: " + component);
                            yield value;
                        }
                        case Value.Array array -> {
                            if (accessPoint instanceof AccessPoint.Index indexAccess) {
                                final Value index = evaluate(indexAccess.expression(), null);
                                if (!(index instanceof Value.IntegerLiteral integerLiteral)) {
                                    throw new RuntimeException("Expected constant, got: " + index);
                                }
                                final int integer = (int) integerLiteral.value();
                                final List<Value> content = array.elements();
                                if (integer < 0 || integer >= content.size())
                                    throw new RuntimeException("Index out of bounds: " + integer + " in " + content + " -> " + indexAccess.expression());
                                yield content.get(integer);
                            } else if (accessPoint instanceof AccessPoint.Constraint constraintAccess) {
                                List<Value> filtered = new ArrayList<>();
                                for (Value element : array.elements()) {
                                    this.contextual = element;
                                    final Value condition = evaluate(constraintAccess.expression(), null);
                                    if (!(condition instanceof Value.BooleanLiteral booleanLiteral)) {
                                        throw new RuntimeException("Expected constant, got: " + condition);
                                    }
                                    if (booleanLiteral.value()) filtered.add(element);
                                }
                                this.contextual = null;
                                // Lose length information
                                final Type.Array arrayType = new Type.Array(array.arrayType().type(), -1);
                                yield new Value.Array(arrayType, filtered);
                            } else {
                                throw new RuntimeException("Invalid array access: " + access);
                            }
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
                final List<Expression> expressions = ((Parameter.Passed.Positional) call.arguments()).expressions();
                assert params.size() == expressions.size() : name + ": Expected " + params.size() + " arguments, got " + expressions.size();
                List<Value> evaluated = new ArrayList<>();
                for (int i = 0; i < expressions.size(); i++) {
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
                        if (passed instanceof Parameter.Passed.Named named) {
                            for (Map.Entry<String, Expression> entry : named.entries().entrySet()) {
                                final String key = entry.getKey();
                                final Expression value = entry.getValue();
                                final Value evaluatedValue = evaluate(value, parameters.stream().filter(p -> p.name().equals(key)).findFirst().get().type());
                                evaluated.put(key, evaluatedValue);
                            }
                        } else if (passed instanceof Parameter.Passed.Positional positional) {
                            final List<Expression> expressions = positional.expressions();
                            for (int i = 0; i < expressions.size(); i++) {
                                final Expression expression = expressions.get(i);
                                final Parameter parameter = parameters.get(i);
                                final Value evaluatedValue = evaluate(expression, parameter.type());
                                evaluated.put(parameter.name(), evaluatedValue);
                            }
                        }
                        yield new Value.Struct(name, evaluated);
                    }
                    case Type.Array arrayType -> {
                        final long length = arrayType.length();
                        final List<Value> values = switch (passed) {
                            case Parameter.Passed.Positional positional -> {
                                final List<Expression> expressions = positional.expressions();
                                final Type elementType = arrayType.type();
                                final List<Value> evaluated;
                                if (!expressions.isEmpty()) {
                                    // Initialized array
                                    evaluated = expressions.stream()
                                            .map(expression -> executor.evaluate(expression, elementType)).toList();
                                } else {
                                    // Default value
                                    final Value defaultValue = ValueType.defaultValue(elementType);
                                    evaluated = LongStream.range(0, length).mapToObj(i -> defaultValue).toList();
                                }
                                yield evaluated;
                            }
                            case Parameter.Passed.Supplied supplied -> {
                                final List<Value> evaluated = new ArrayList<>();
                                for (int i = 0; i < length; i++) {
                                    this.contextual = new Value.IntegerLiteral(Type.INT, i);
                                    final Value value = evaluate(supplied.expression(), null);
                                    evaluated.add(value);
                                    this.contextual = null;
                                }
                                yield evaluated;
                            }
                            default -> throw new RuntimeException("Invalid array initialization: " + initialization);
                        };
                        yield new Value.Array(arrayType, values);
                    }
                    default ->
                            throw new RuntimeException("Invalid initialization: " + initialization + " " + explicitType);
                };
            }
            case Expression.Range init -> {
                final Value start = evaluate(init.start(), null);
                final Value end = evaluate(init.end(), null);
                final Value step = evaluate(init.step(), null);
                if (!(start instanceof Value.IntegerLiteral startLiteral))
                    throw new RuntimeException("Expected constant, got: " + start);
                if (!(end instanceof Value.IntegerLiteral endLiteral))
                    throw new RuntimeException("Expected constant, got: " + end);
                if (!(step instanceof Value.IntegerLiteral stepLiteral))
                    throw new RuntimeException("Expected constant, got: " + step);
                final long startValue = startLiteral.value();
                final long endValue = endLiteral.value();
                final long stepValue = stepLiteral.value();
                final List<Value> values = new ArrayList<>();
                for (long i = startValue; i < endValue; i += stepValue) {
                    values.add(new Value.IntegerLiteral(Type.INT, i));
                }
                yield new Value.Array(new Type.Array(Type.INT, values.size()), values);
            }
            case Expression.Binary binary -> {
                final Value left = evaluate(binary.left(), null);
                final Value right = evaluate(binary.right(), null);
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
        return cast(rawValue, explicitType);
    }

    Value cast(Value value, Type target) {
        if (target == null) {
            // No type defined, use inferred type
            return value;
        }
        if (target instanceof Type.Array targetArray && value instanceof Value.Array arrayValue) {
            final Type.Array valueArray = arrayValue.arrayType();
            if (valueArray.length() != -1 && targetArray.length() == -1) {
                // Lose length information
                value = new Value.Array(new Type.Array(valueArray.type(), -1), arrayValue.elements());
            }
        }
        if (target instanceof Type.Identifier identifier) {
            final String name = identifier.name();
            final Value trackedType = walker.find(name);
            // Union cast
            if (trackedType instanceof Value.UnionDecl unionDecl && value instanceof Value.Struct struct) {
                // Put struct in union wrapper
                assert unionDecl.entries().containsKey(struct.name()) : "Struct not found in union: " + struct.name();
                return new Value.Union(name, value);
            }
            // Enum cast
            if (trackedType instanceof Value.EnumDecl enumDecl && !(value instanceof Value.Enum)) {
                final Map<String, Value> entries = enumDecl.entries();
                Value finalValue = value;
                final String enumName = entries.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(finalValue))
                        .findFirst()
                        .orElseThrow()
                        .getKey();
                // Put struct in enum wrapper
                return new Value.Enum(name, enumName);
            }
            // Constrained type check
            if (trackedType instanceof Value.DistinctDecl distinctDecl) {
                final Value casted = cast(value, distinctDecl.type());
                this.contextual = casted;
                final Value.BooleanLiteral constraintResult = (Value.BooleanLiteral) evaluate(distinctDecl.constraint(), null);
                this.contextual = null;
                if (!constraintResult.value())
                    throw new RuntimeException("Value does not satisfy constraint: " + value);
                return casted;
            }
        }
        // TODO: Check if value is assignable to target
        //final Type extractedType = ValueType.extractAssignmentType(value);
        //if (!extractedType.equals(target)) throw new RuntimeException("Expected type " + target + ", got " + extractedType);
        // Valid type, no conversion needed
        return value;
    }
}
