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
            throw new RuntimeException("Function not found: " + call+ " " + function);
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
        if (expression instanceof Value.Constant constant) {
            final Object value = constant.value();
            if (value instanceof String) return Type.STRING;
            if (value instanceof Integer) return Type.I32;
        } else if (expression instanceof Value.Struct struct) {
            return Type.of(struct.name());
        }
        throw new RuntimeException("Unknown type: " + expression);
    }

    private Value execute(Value.FunctionDecl function, Statement statement) {
        if (statement instanceof Statement.Declare declare) {
            final String name = declare.name();
            final Expression initializer = declare.initializer();
            final Value evaluated = evaluate(initializer, declare.explicitType());
            assert evaluated != null;
            walker.register(name, evaluated);
        } else if (statement instanceof Statement.Assign assign) {
            final Type variableType = extractType(walker.find(assign.name()));
            final Value evaluated = evaluate(assign.expression(), variableType);
            walker.update(assign.name(), evaluated);
        } else if (statement instanceof Statement.Call call) {
            return evaluate(new Expression.Call(call.name(), call.arguments()), null);
        } else if (statement instanceof Statement.Branch branch) {
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
        } else if (statement instanceof Statement.Loop loop) {
            if (loop.iterable() == null) {
                // Infinite loop
                while (true) {
                    for (Statement body : loop.body()) {
                        execute(function, body);
                    }
                }
            } else {
                final List<String> declarations = loop.declarations();
                final Value iterable = evaluate(loop.iterable(), null);
                if (iterable instanceof Value.Range range) {
                    final int start = (int) ((Value.Constant) range.start()).value();
                    final int end = (int) ((Value.Constant) range.end()).value();
                    final int step = (int) ((Value.Constant) range.step()).value();
                    walker.enterBlock();
                    for (int i = 0; i < declarations.size(); i++) {
                        final String declaration = loop.declarations().get(i);
                        walker.register(declaration, new Value.Constant(0));
                    }
                    for (int i = start; i < end; i += step) {
                        if (!declarations.isEmpty()) walker.update(declarations.get(0), new Value.Constant(i));
                        for (Statement body : loop.body()) {
                            execute(function, body);
                        }
                    }
                    walker.exitBlock();
                } else if (iterable instanceof Value.Array array) {
                    walker.enterBlock();
                    final List<Value> values = array.values();
                    if (declarations.size() == 1) {
                        final String name = declarations.get(0);
                        walker.register(name, new Value.Constant(0));
                    } else if (declarations.size() > 1) {
                        throw new RuntimeException("Too many declarations for array iteration");
                    }
                    for (Value value : values) {
                        if (!declarations.isEmpty()) walker.update(declarations.get(0), value);
                        for (Statement body : loop.body()) {
                            execute(function, body);
                        }
                    }
                    walker.exitBlock();
                } else {
                    throw new RuntimeException("Expected iterable, got: " + iterable);
                }
            }
        } else if (statement instanceof Statement.Block block) {
            this.walker.enterBlock();
            for (Statement inner : block.statements()) {
                execute(function, inner);
            }
            this.walker.exitBlock();
        } else if (statement instanceof Statement.Return returnStatement) {
            final Expression expression = returnStatement.expression();
            if (expression != null) {
                final Type returnType = function.returnType();
                return evaluate(expression, returnType);
            }
        }
        return null;
    }

    private Value evaluate(Expression argument, Type explicitType) {
        if (argument instanceof Expression.Function functionDeclaration) {
            return new Value.FunctionDecl(functionDeclaration.parameters(), functionDeclaration.returnType(), functionDeclaration.body());
        } else if (argument instanceof Expression.Struct structDeclaration) {
            return new Value.StructDecl(structDeclaration.parameters());
        } else if (argument instanceof Expression.Enum enumDeclaration) {
            final Type type = enumDeclaration.type();
            Map<String, Value> evaluated = new HashMap<>();
            for (Map.Entry<String, Expression> entry : enumDeclaration.entries().entrySet()) {
                evaluated.put(entry.getKey(), evaluate(entry.getValue(), type));
            }
            return new Value.EnumDecl(type, evaluated);
        } else if (argument instanceof Expression.Constant constant) {
            return new Value.Constant(constant.value());
        } else if (argument instanceof Expression.Variable variable) {
            final String name = variable.name();
            final Value value = walker.find(name);
            if (value == null) {
                throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked.keySet());
            }
            return value;
        } else if (argument instanceof Expression.Field field) {
            final Value expression = evaluate(field.object(), null);
            if (expression instanceof Value.Struct struct) {
                return struct.parameters().get(field.name());
            } else if (expression instanceof Value.EnumDecl enumDecl) {
                final Value value = enumDecl.entries().get(field.name());
                if (value == null) throw new RuntimeException("Enum entry not found: " + field.name());
                return value;
            } else {
                throw new RuntimeException("Expected struct, got: " + expression);
            }
        } else if (argument instanceof Expression.ArrayAccess arrayAccess) {
            final Value array = evaluate(arrayAccess.array(), null);
            if (array instanceof Value.Array arrayValue) {
                final Value index = evaluate(arrayAccess.index(), null);
                if (!(index instanceof Value.Constant constant) || !(constant.value() instanceof Integer integer)) {
                    throw new RuntimeException("Expected constant, got: " + index);
                }
                final List<Value> content = arrayValue.values();
                if (integer < 0 || integer >= content.size())
                    throw new RuntimeException("Index out of bounds: " + integer + " in " + content);
                return content.get(integer);
            } else if (array instanceof Value.Map mapValue) {
                final Value index = evaluate(arrayAccess.index(), mapValue.keyType());
                final Value result = mapValue.entries().get(index);
                if (result == null) throw new RuntimeException("Key not found: " + index);
                return result;
            } else {
                throw new RuntimeException("Expected array/map, got: " + array);
            }
        } else if (argument instanceof Expression.Call call) {
            final String name = call.name();
            final List<Value> evaluated = call.arguments().expressions().stream()
                    .map(expression -> evaluate(expression, null)).toList();
            if (name.equals("print")) {
                StringBuilder builder = new StringBuilder();
                for (Value value : evaluated) builder.append(serialize(value));
                System.out.println(builder);
            } else {
                return interpret(name, evaluated);
            }
            return null;
        } else if (argument instanceof Expression.StructValue structValue) {
            final Value.StructDecl struct = (Value.StructDecl) walker.find(structValue.name());
            final List<Parameter> parameters = struct.parameters();
            Map<String, Value> evaluated = new HashMap<>();
            for (Parameter param : struct.parameters()) {
                final Expression value = structValue.fields().find(parameters, param);
                if (value == null) throw new RuntimeException("Missing field: " + param.name());
                evaluated.put(param.name(), evaluate(value, param.type()));
            }
            return new Value.Struct(structValue.name(), evaluated);
        } else if (argument instanceof Expression.ArrayValue arrayValue) {
            if (!(arrayValue.parameters() instanceof Parameter.Passed.Positional positional))
                throw new RuntimeException("Expected positional parameters");
            final List<Value> evaluated = positional.expressions().stream()
                    .map(expression -> evaluate(expression, arrayValue.type())).toList();
            return new Value.Array(arrayValue.type(), evaluated);
        } else if (argument instanceof Expression.MapValue mapValue) {
            Map<Value, Value> evaluatedEntries = new HashMap<>();
            for (var entry : mapValue.entries().entrySet()) {
                final Value key = evaluate(entry.getKey(), mapValue.keyType());
                final Value value = evaluate(entry.getValue(), mapValue.valueType());
                evaluatedEntries.put(key, value);
            }
            return new Value.Map(mapValue.keyType(), mapValue.valueType(), evaluatedEntries);
        } else if (argument instanceof Expression.InitializationBlock initializationBlock) {
            // Retrieve explicit type from context
            if (explicitType == null) throw new RuntimeException("Expected explicit type for initialization block");
            if (!explicitType.primitive()) {
                // Struct
                return evaluate(new Expression.StructValue(explicitType.name(), initializationBlock.parameters()), null);
            } else {
                throw new RuntimeException("Expected struct, got: " + explicitType);
            }
        } else if (argument instanceof Expression.Range init) {
            return new Value.Range(evaluate(init.start(), null),
                    evaluate(init.end(), null),
                    evaluate(init.step(), null));
        } else if (argument instanceof Expression.Binary binary) {
            final Value left = evaluate(binary.left(), explicitType);
            final Value right = evaluate(binary.right(), explicitType);
            return operate(binary.operator(), left, right);
        } else {
            throw new RuntimeException("Unknown expression: " + argument);
        }
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
                return new Value.Constant(isComparison ? result == 1 : result);
            } else if (leftValue instanceof Boolean leftBool && rightValue instanceof Boolean rightBool) {
                final boolean result = switch (operator.type()) {
                    case OR -> leftBool || rightBool;
                    case AND -> leftBool && rightBool;
                    case EQUAL_EQUAL -> leftBool.equals(rightBool);
                    default -> throw new RuntimeException("Unknown operator: " + operator);
                };
                return new Value.Constant(result);
            } else {
                throw new RuntimeException("Unknown types: " + leftValue.getClass() + " and " + rightValue.getClass());
            }
        } else {
            throw new RuntimeException("Unknown types: " + left + " and " + right);
        }
    }

    private String serialize(Value expression) {
        if (expression instanceof Value.Constant constant) {
            return constant.value().toString();
        } else if (expression instanceof Value.Struct struct) {
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
            return builder.toString();
        } else if (expression instanceof Value.Array array) {
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
            return builder.toString();
        } else {
            throw new RuntimeException("Unknown expression: " + expression);
        }
    }

    public void stop() {
        walker.exitBlock();
    }
}
