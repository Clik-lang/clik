package org.click.interpreter;

import org.click.Expression;

import java.util.List;

public record EvaluatorFunction(Executor executor) {
    public Value evaluate(Expression.Call call) {
        final String name = call.name();
        final List<Value> evaluated = call.arguments().expressions().stream()
                .map(expression -> executor.evaluate(expression, null)).toList();
        if (name.equals("print")) {
            StringBuilder builder = new StringBuilder();
            for (Value value : evaluated) {
                final String serialized = ValueSerializer.serialize(executor().walker(), value);
                builder.append(serialized);
            }
            System.out.println(builder);
            return null;
        } else {
            return executor.interpret(name, evaluated);
        }
    }
}
