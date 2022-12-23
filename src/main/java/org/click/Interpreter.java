package org.click;

import org.click.value.Value;
import org.click.value.ValueOperator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            final Value value = evaluate(expression);
            this.walker.register(name, value);
        }
        // Run function
        final Program.Function function = program.functions().get(functionName);
        if (function == null) throw new IllegalArgumentException("Function " + functionName + " not found");
        final List<Program.Expression> argValues = arguments.stream()
                .map(value -> (Program.Expression) new Program.Expression.Constant(value)).toList();
        final Program.Expression.Call call = new Program.Expression.Call(function.functionType().returnType(), functionName, argValues);
        final Value value = execute(new Program.Statement.Run(call));
        this.walker.exitBlock();
        return value;
    }

    private Value execute(Program.Statement statement) {
        return switch (statement) {
            case Program.Statement.Declare declare -> {
                final Value value = evaluate(declare.expression());
                this.walker.register(declare.name(), value);
                yield null;
            }
            case Program.Statement.Assign assign -> {
                final Value value = evaluate(assign.expression());
                this.walker.update(assign.name(), value);
                yield null;
            }
            case Program.Statement.Run run -> evaluate(run.expression());
            case Program.Statement.Branch branch -> {
                final Value.BooleanLiteral condition = (Value.BooleanLiteral) evaluate(branch.condition());
                if (condition.value()) {
                    yield execute(branch.thenBranch());
                } else {
                    if (branch.elseBranch() != null) {
                        yield execute(branch.elseBranch());
                    } else {
                        yield null;
                    }
                }
            }
            case Program.Statement.Block block -> {
                this.walker.enterBlock();
                Value result = null;
                for (Program.Statement statement1 : block.statements()) {
                    result = execute(statement1);
                    if (result != null) break;
                }
                this.walker.exitBlock();
                yield result;
            }
            case Program.Statement.Return returnStatement -> evaluate(returnStatement.expression());
            default -> throw new IllegalArgumentException("Unknown statement " + statement);
        };
    }

    private Value evaluate(Program.Expression expression) {
        return switch (expression) {
            case Program.Expression.Constant constant -> constant.value();
            case Program.Expression.Variable variable -> {
                final Value value = this.walker.find(variable.name());
                assert value != null : "Variable " + variable.name() + " not found -> " + walker.currentScope().tracked().keySet();
                yield value;
            }
            case Program.Expression.Call call -> {
                this.walker.enterBlock();
                final String name = call.name();
                Program.Function function = program.functions().get(name);
                if (function == null) {
                    final Value tracked = walker.find(name);
                    assert tracked != null : "Function " + name + " not found: " + program.functions().keySet();
                    if (!(tracked instanceof Value.Function valueFunction))
                        throw new IllegalArgumentException("Function " + name + " not found");
                    function = program.functions().get(valueFunction.name());
                }
                int index = 0;
                for (var param : function.functionType().parameters()) {
                    var paramName = param.name();
                    var argExpression = call.arguments().get(index++);
                    final Value value = evaluate(argExpression);
                    this.walker.register(paramName, value);
                }
                Value result = null;
                for (Program.Statement statement : function.body()) {
                    result = execute(statement);
                    if (result != null) break;
                }
                this.walker.exitBlock();
                yield result;
            }
            case Program.Expression.Struct struct -> {
                final String name = struct.structType().name();
                final Program.Struct structDef = program.structs().get(name);
                final Program.TypedName.Passed passed = struct.passed();
                Map<String, Value> fields = new HashMap<>();
                if (passed instanceof Program.TypedName.Passed.Positional positional) {
                    int index = 0;
                    for (var field : positional.expressions()) {
                        Program.TypedName fieldName = structDef.fields().get(index++);
                        final Value evaluated = evaluate(field);
                        fields.put(fieldName.name(), evaluated);
                    }
                } else if (passed instanceof Program.TypedName.Passed.Named named) {
                    for (var entry : named.entries().entrySet()) {
                        final String name1 = entry.getKey();
                        final Program.Expression expression1 = entry.getValue();
                        final Value evaluated = evaluate(expression1);
                        fields.put(name1, evaluated);
                    }
                } else {
                    assert false;
                }
                yield new Value.Struct(name, fields);
            }
            case Program.Expression.Binary binary -> {
                final Value left = evaluate(binary.left());
                final Value right = evaluate(binary.right());
                yield ValueOperator.operate(binary.operator(), left, right);
            }
            case Program.Expression.Unary unary -> {
                final Value value = evaluate(unary.expression());
                if (unary.operator() == Token.Type.EXCLAMATION) {
                    final boolean bool = ((Value.BooleanLiteral) value).value();
                    yield new Value.BooleanLiteral(!bool);
                } else {
                    throw new RuntimeException("Unsupported unary operator: " + unary.operator());
                }
            }
            default -> throw new IllegalArgumentException("Unknown expression " + expression);
        };
    }
}
