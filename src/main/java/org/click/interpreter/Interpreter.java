package org.click.interpreter;

import org.click.Expression;
import org.click.Parameter;
import org.click.Statement;
import org.click.Type;

import java.util.List;

public final class Interpreter {
    private final List<Statement> statements;
    private final ScopeWalker<Value> walker = new ScopeWalker<>();

    private final ValueSerializer valueSerializer = new ValueSerializer(walker);
    private final ExecutorStatement interpreterStatement = new ExecutorStatement(this, walker);
    private final ExecutorExpression interpreterExpression = new ExecutorExpression(this, walker);

    private Value.FunctionDecl currentFunction = null;

    public Value.FunctionDecl currentFunction() {
        return currentFunction;
    }

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

        var previousFunction = currentFunction;
        currentFunction = functionDeclaration;
        Value result = null;
        for (Statement statement : functionDeclaration.body()) {
            result = execute(statement);
            if (result != null) break;
        }
        currentFunction = previousFunction;
        walker.exitBlock();
        return result;
    }

    public Type extractType(Value expression) {
        return switch (expression) {
            case Value.Constant constant -> {
                final Object value = constant.value();
                if (value instanceof String) yield Type.STRING;
                if (value instanceof Integer) yield Type.I32;
                throw new RuntimeException("Unknown constant type: " + value);
            }
            case Value.Struct struct -> Type.of(struct.name());
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }

    public Value execute(Statement statement) {
        return interpreterStatement.interpret(statement);
    }

    public Value evaluate(Expression argument, Type explicitType) {
        return interpreterExpression.evaluate(argument, explicitType);
    }

    public void stop() {
        walker.exitBlock();
    }
}
