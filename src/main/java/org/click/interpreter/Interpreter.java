package org.click.interpreter;

import org.click.Expression;
import org.click.Parameter;
import org.click.Statement;
import org.click.Token;

import java.util.List;

public final class Interpreter {
    private final List<Statement> statements;
    private final ScopeWalker<Expression> walker = new ScopeWalker<>();

    public Interpreter(List<Statement> statements) {
        this.statements = statements;

        this.walker.enterBlock();
        // Global scope
        for (Statement statement : statements) {
            if (statement instanceof Statement.Declare declare) {
                walker.register(declare.name(), declare.initializer());
            }
        }
    }

    public Expression interpret(String function) {
        return interpret(function, List.of());
    }

    public Expression interpret(String function, List<Expression> parameters) {
        final Expression call = walker.find(function);
        if (!(call instanceof Expression.Function functionDeclaration)) {
            throw new RuntimeException("Function not found: " + call);
        }

        walker.enterBlock();
        for (int i = 0; i < parameters.size(); i++) {
            final Parameter parameter = functionDeclaration.parameters().get(i);
            final Expression expression = parameters.get(i);
            assert expression != null;
            walker.register(parameter.name(), expression);
        }

        Expression result = null;
        for (Statement statement : functionDeclaration.body()) {
            result = execute(statement);
        }

        walker.exitBlock();
        return result;
    }

    private Expression execute(Statement statement) {
        if (statement instanceof Statement.Declare declare) {
            final String name = declare.name();
            final Expression initializer = declare.initializer();
            final Expression evaluated = evaluate(initializer);
            assert evaluated != null;
            walker.register(name, evaluated);
        } else if (statement instanceof Statement.Assign assign) {
            walker.update(assign.name(), assign.expression());
        } else if (statement instanceof Statement.Call call) {
            return evaluate(new Expression.Call(call.name(), call.arguments()));
        } else if (statement instanceof Statement.Branch branch) {
            final Expression condition = evaluate(branch.condition());
            assert condition != null;
            if (condition instanceof Expression.Constant constant) {
                if ((boolean) constant.value()) {
                    for (Statement thenBranch : branch.thenBranch()) {
                        execute(thenBranch);
                    }
                } else if (branch.elseBranch() != null) {
                    for (Statement elseBranch : branch.elseBranch()) {
                        execute(elseBranch);
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
                        execute(body);
                    }
                }
            } else {
                final Expression iterable = evaluate(loop.iterable());
                if (iterable instanceof Expression.Range range) {
                    final int start = (int) ((Expression.Constant) range.start()).value();
                    final int end = (int) ((Expression.Constant) range.end()).value();
                    final int step = (int) ((Expression.Constant) range.step()).value();
                    walker.enterBlock();
                    for (int i = 0; i < loop.declarations().size(); i++) {
                        final String declaration = loop.declarations().get(i);
                        walker.register(declaration, new Expression.Constant(0));
                    }
                    for (int i = start; i < end; i += step) {
                        walker.update(loop.declarations().get(0), new Expression.Constant(i));
                        for (Statement body : loop.body()) {
                            execute(body);
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
                execute(inner);
            }
            this.walker.exitBlock();
        } else if (statement instanceof Statement.Return returnStatement) {
            final Expression expression = returnStatement.expression();
            if (expression != null) {
                return evaluate(expression);
            }
        }
        return null;
    }

    private Expression evaluate(Expression argument) {
        if (argument instanceof Expression.Function functionDeclaration) {
            return functionDeclaration;
        } else if (argument instanceof Expression.Struct structDeclaration) {
            return structDeclaration;
        } else if (argument instanceof Expression.Enum enumDeclaration) {
            return enumDeclaration;
        } else if (argument instanceof Expression.Constant constant) {
            return constant;
        } else if (argument instanceof Expression.Variable variable) {
            final String name = variable.name();
            final Expression variableExpression = walker.find(name);
            if (variableExpression == null) {
                throw new RuntimeException("Variable not found: " + name + " -> " + walker.currentScope().tracked.keySet());
            }
            return variableExpression;
        } else if (argument instanceof Expression.Field field) {
            final Expression expression = evaluate(field.object());
            if (expression instanceof Expression.StructInit structInit) {
                if (!(walker.find(structInit.name()) instanceof Expression.Struct structDeclaration)) {
                    throw new RuntimeException("Struct not found: " + structInit.name());
                }
                final var params = structDeclaration.parameters().stream().map(Parameter::name).toList();
                int index = params.indexOf(field.name());
                if (index == -1) {
                    throw new RuntimeException("Field not found: " + field.name());
                }
                return structInit.fields().get(index);
            } else if (expression instanceof Expression.Enum enumDecl) {
                final Expression value = enumDecl.entries().get(field.name());
                if (value == null) {
                    throw new RuntimeException("Enum entry not found: " + field.name());
                }
                return value;
            } else {
                throw new RuntimeException("Expected struct, got: " + expression);
            }
        } else if (argument instanceof Expression.Call call) {
            final String name = call.name();
            final List<Expression> params = call.arguments().stream().map(this::evaluate).toList();
            if (name.equals("print")) {
                for (Expression param : params) {
                    final String serialize = serialize(param);
                    System.out.print(serialize);
                }
                System.out.println();
            } else {
                return interpret(name, call.arguments());
            }
            return null;
        } else if (argument instanceof Expression.StructInit init) {
            return new Expression.StructInit(init.name(), init.fields().stream().map(this::evaluate).toList());
        } else if (argument instanceof Expression.Range init) {
            return new Expression.Range(evaluate(init.start()), evaluate(init.end()), evaluate(init.step()));
        } else if (argument instanceof Expression.Binary binary) {
            final Expression left = evaluate(binary.left());
            final Expression right = evaluate(binary.right());
            return operate(binary.operator(), left, right);
        } else {
            throw new RuntimeException("Unknown expression: " + argument);
        }
    }

    private Expression operate(Token operator, Expression left, Expression right) {
        if (left instanceof Expression.Constant leftConstant && right instanceof Expression.Constant rightConstant) {
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
                return new Expression.Constant(isComparison ? result == 1 : result);
            } else if (leftValue instanceof Boolean leftBool && rightValue instanceof Boolean rightBool) {
                final boolean result = switch (operator.type()) {
                    case OR -> leftBool || rightBool;
                    case AND -> leftBool && rightBool;
                    case EQUAL_EQUAL -> leftBool.equals(rightBool);
                    default -> throw new RuntimeException("Unknown operator: " + operator);
                };
                return new Expression.Constant(result);
            } else {
                throw new RuntimeException("Unknown types: " + leftValue.getClass() + " and " + rightValue.getClass());
            }
        } else {
            throw new RuntimeException("Unknown types: " + left + " and " + right);
        }
    }

    private String serialize(Expression expression) {
        if (expression instanceof Expression.Constant constant) {
            return constant.value().toString();
        } else if (expression instanceof Expression.StructInit init) {
            final StringBuilder builder = new StringBuilder();
            builder.append(init.name()).append("{");
            for (int i = 0; i < init.fields().size(); i++) {
                final Expression field = init.fields().get(i);
                builder.append(serialize(field));
                if (i < init.fields().size() - 1) {
                    builder.append(", ");
                }
            }
            builder.append("}");
            return builder.toString();
        } else {
            throw new RuntimeException("Unknown expression: " + expression);
        }
    }

    public void stop() {
        walker.exitBlock();
    }
}
