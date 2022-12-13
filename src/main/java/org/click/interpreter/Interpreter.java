package org.click.interpreter;

import org.click.Expression;
import org.click.Statement;

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

    public void interpret(String function) {
        final Expression call = walker.find(function);
        if (!(call instanceof Expression.Function functionDeclaration)) {
            throw new RuntimeException("Function not found: " + call);
        }
        for (Statement statement : functionDeclaration.body()) {
            execute(statement);
        }
    }

    private void execute(Statement statement) {
        if (statement instanceof Statement.Declare declare) {
            walker.register(declare.name(), declare.initializer());
        } else if (statement instanceof Statement.Assign assign) {
            walker.register(assign.name(), assign.expression());
        } else if (statement instanceof Statement.Call call) {
            final String name = call.name();
            if (name.equals("print")) {
                for (Expression argument : call.arguments()) {
                    final String value = evaluate(argument).toString();
                    System.out.print(value);
                }
                System.out.println();
            } else {
                interpret(name);
            }
        }
    }

    private Object evaluate(Expression argument) {
        if (argument instanceof Expression.Constant constant) {
            return constant.value();
        } else if (argument instanceof Expression.Variable variable) {
            final Expression variableExpression = walker.find(variable.name());
            if (variableExpression == null) {
                throw new RuntimeException("Variable not found: " + variable.name());
            }
            return evaluate(variableExpression);
        } else {
            throw new RuntimeException("Unknown expression: " + argument);
        }
    }

    public void stop() {
        walker.exitBlock();
    }
}
