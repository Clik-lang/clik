package org.click;

import java.util.List;
import java.util.Map;

public record Parameter(String name, Type type) {
    public sealed interface Passed {
        Expression find(List<Parameter> parameters, Parameter parameter);

        List<Expression> expressions();

        record Positional(List<Expression> expressions) implements Passed {
            @Override
            public Expression find(List<Parameter> parameters, Parameter parameter) {
                final int index = parameters.indexOf(parameter);
                final Expression expression = expressions.get(index);
                if (expression == null)
                    throw new RuntimeException("Field not found: " + parameter);
                return expression;
            }
        }

        record Named(Map<String, Expression> entries) implements Passed {
            @Override
            public Expression find(List<Parameter> parameters, Parameter parameter) {
                final Expression expression = entries.get(parameter.name());
                if (expression == null)
                    throw new RuntimeException("Field not found: " + parameter);
                return expression;
            }

            @Override
            public List<Expression> expressions() {
                return List.copyOf(entries.values());
            }
        }

        record Mapped(Map<Expression, Expression> entries) implements Passed {
            @Override
            public Expression find(List<Parameter> parameters, Parameter parameter) {
                throw new RuntimeException("Mapped parameters are not supported");
            }

            @Override
            public List<Expression> expressions() {
                throw new RuntimeException("Mapped parameters are not supported");
            }
        }
    }
}
