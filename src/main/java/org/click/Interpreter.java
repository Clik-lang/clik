package org.click;

import org.click.value.Value;
import org.click.value.ValueCompute;
import org.click.value.ValueOperator;
import org.click.value.ValueType;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Interpreter {
    private final Program program;

    public Interpreter(Program program) {
        this.program = program;
    }

    private final ScopeWalker<Value> walker = new ScopeWalker<>();

    public Value interpret(String functionName, List<Value> arguments) {
        this.walker.enterBlock();
        // Global scope
        for (Map.Entry<String, Program.Expression> entry : program.variables().entrySet()) {
            final String name = entry.getKey();
            final Program.Expression expression = entry.getValue();
            final Value value = evaluate(walker, expression);
            this.walker.register(name, value);
        }
        // Run function
        final Program.Function function = program.functions().get(functionName);
        if (function == null) throw new IllegalArgumentException("Function " + functionName + " not found");
        final List<Program.Expression> argValues = arguments.stream()
                .map(value -> (Program.Expression) new Program.Expression.Constant(value)).toList();
        final Program.Expression.Call call = new Program.Expression.Call(function.functionType().returnType(), functionName, argValues);
        final Value value = execute(walker, new Program.Statement.Run(call));
        this.walker.exitBlock();
        return value;
    }

    private Value execute(ScopeWalker<Value> walker, Program.Statement statement) {
        return switch (statement) {
            case Program.Statement.Declare declare -> {
                final Value value = evaluate(walker, declare.expression());
                walker.register(declare.name(), value);
                yield null;
            }
            case Program.Statement.Assign assign -> {
                final Value value = evaluate(walker, assign.expression());
                walker.update(assign.name(), value);
                yield null;
            }
            case Program.Statement.Capture capture -> {
                Map<String, Value> values = new HashMap<>();
                for (var captureString : capture.names()) {
                    values.put(captureString, walker.find(captureString));
                }
                final Value value = new Value.Capture(values);
                walker.register(capture.name(), value);
                yield null;
            }
            case Program.Statement.Run run -> evaluate(walker, run.expression());
            case Program.Statement.Branch branch -> {
                final Value.BooleanLiteral condition = (Value.BooleanLiteral) evaluate(walker, branch.condition());
                if (condition.value()) {
                    yield execute(walker, branch.thenBranch());
                } else {
                    if (branch.elseBranch() != null) {
                        yield execute(walker, branch.elseBranch());
                    } else {
                        yield null;
                    }
                }
            }
            case Program.Statement.Block block -> {
                walker.enterBlock();
                Value result = null;
                for (Program.Statement statement1 : block.statements()) {
                    result = execute(walker, statement1);
                    if (result != null) break;
                }
                walker.exitBlock();
                yield result;
            }
            case Program.Statement.Return returnStatement -> evaluate(walker, returnStatement.expression());
            default -> throw new IllegalArgumentException("Unknown statement " + statement);
        };
    }

    private Value fork(Value.@Nullable Capture capture, Program.Function function, List<Value> arguments) {
        ScopeWalker<Value> walker = new ScopeWalker<>();
        walker.enterBlock();
        // Register captures
        if (capture != null) {
            for (Map.Entry<String, Value> entry : capture.values().entrySet()) {
                walker.register(entry.getKey(), entry.getValue());
            }
        }
        // Register parameters
        int index = 0;
        for (var param : function.functionType().parameters()) {
            final String paramName = param.name();
            final Value value = arguments.get(index++);
            walker.register(paramName, value);
        }
        // Run function
        Value result = null;
        for (Program.Statement statement : function.body()) {
            result = execute(walker, statement);
            if (result != null) break;
        }
        walker.exitBlock();
        return result;
    }

    private Value evaluate(ScopeWalker<Value> walker, Program.Expression evaluatedExpression) {
        return switch (evaluatedExpression) {
            case Program.Expression.Constant constant -> constant.value();
            case Program.Expression.Variable variable -> {
                final Value value = walker.find(variable.name());
                assert value != null : "Variable " + variable.name() + " not found -> " + walker.currentScope().tracked().keySet();
                yield value;
            }
            case Program.Expression.Call call -> {
                final String name = call.name();
                final Value tracked = walker.find(name);
                final Value.Capture capture = tracked instanceof Value.Capture c ? c : null;
                Program.Function function = program.functions().get(name);
                if (function == null) {
                    assert tracked != null : "Function " + name + " not found: " + program.functions().keySet();
                    if (!(tracked instanceof Value.Function valueFunction))
                        throw new IllegalArgumentException("Function " + name + " not found");
                    function = program.functions().get(valueFunction.name());
                }
                final List<Value> arguments = call.arguments().stream().map(arg -> evaluate(walker, arg)).toList();
                yield fork(capture, function, arguments);
            }
            case Program.Expression.Struct struct -> {
                final String name = struct.structType().name();
                final Program.Struct structDef = program.structs().get(name);
                final Program.Passed passed = struct.passed();
                Map<String, Value> fields = new HashMap<>();
                if (passed instanceof Program.Passed.Positional positional) {
                    int index = 0;
                    for (var field : positional.expressions()) {
                        TypedName fieldName = structDef.fields().get(index++);
                        final Value evaluated = evaluate(walker, field);
                        fields.put(fieldName.name(), evaluated);
                    }
                } else if (passed instanceof Program.Passed.Named named) {
                    for (var entry : named.entries().entrySet()) {
                        final String name1 = entry.getKey();
                        final Program.Expression expression1 = entry.getValue();
                        final Value evaluated = evaluate(walker, expression1);
                        fields.put(name1, evaluated);
                    }
                } else {
                    assert false;
                }
                yield new Value.Struct(name, fields);
            }
            case Program.Expression.Binary binary -> {
                final Value left = evaluate(walker, binary.left());
                final Value right = evaluate(walker, binary.right());
                yield ValueOperator.operate(binary.operator(), left, right);
            }
            case Program.Expression.Unary unary -> {
                final Value value = evaluate(walker, unary.expression());
                if (unary.operator() == Token.Type.EXCLAMATION) {
                    final boolean bool = ((Value.BooleanLiteral) value).value();
                    yield new Value.BooleanLiteral(!bool);
                } else {
                    throw new RuntimeException("Unsupported unary operator: " + unary.operator());
                }
            }
            case Program.Expression.Access access -> {
                final Value expression = evaluate(walker, access.target());
                Value result = expression;
                for (Program.AccessPoint accessPoint : access.accessPoints()) {
                    result = switch (result) {
                        case Value.Struct struct -> {
                            if (!(accessPoint instanceof Program.AccessPoint.Field fieldAccess))
                                throw new RuntimeException("Invalid struct access: " + access);
                            final String name = fieldAccess.component();
                            yield struct.parameters().get(name);
                        }
                        case Value.EnumDecl enumDecl -> {
                            if (!(accessPoint instanceof Program.AccessPoint.Field fieldAccess))
                                throw new RuntimeException("Invalid enum access: " + access);
                            final String component = fieldAccess.component();
                            final Value value = enumDecl.entries().get(component);
                            if (value == null) throw new RuntimeException("Enum entry not found: " + component);
                            yield value;
                        }
                        case Value.ArrayRef arrayRef -> {
                            if (!(accessPoint instanceof Program.AccessPoint.Index indexAccess))
                                throw new RuntimeException("Invalid array access: " + access);
                            final Value index = evaluate(walker, indexAccess.expression());
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
                            if (!(accessPoint instanceof Program.AccessPoint.Index indexAccess))
                                throw new RuntimeException("Invalid array access: " + access);
                            final Value indexValue = evaluate(walker, indexAccess.expression());
                            if (!(indexValue instanceof Value.IntegerLiteral integerLiteral))
                                throw new RuntimeException("Expected constant, got: " + indexValue);
                            final long index = integerLiteral.value() * ValueType.sizeOf(arrayValue.arrayType().type());
                            final Type type = Objects.requireNonNullElse(indexAccess.transmuteType(), arrayValue.arrayType().type());
                            final MemorySegment data = arrayValue.data();
                            if (index < 0 || index >= data.byteSize())
                                throw new RuntimeException("Index out of bounds: " + index + " in " + data.byteSize());
                            yield ValueCompute.lookupArrayBuffer(type, data, index);
                        }
                        case Value.Map map -> {
                            if (!(accessPoint instanceof Program.AccessPoint.Index indexAccess))
                                throw new RuntimeException("Invalid map access: " + access);
                            final Value key = evaluate(walker, indexAccess.expression());
                            yield map.entries().get(key);
                        }
                        default -> throw new RuntimeException("Expected struct, got: " + expression);
                    };
                }
                yield result;
            }
            default -> throw new IllegalArgumentException("Unknown expression " + evaluatedExpression);
        };
    }
}
