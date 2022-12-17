package org.click.interpreter;

import org.click.AccessPoint;
import org.click.Type;

import java.util.HashMap;
import java.util.List;

public final class ValueExtractor {
    public static Type extractType(Value expression) {
        return switch (expression) {
            case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
            case Value.FloatLiteral floatLiteral -> floatLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.Struct struct -> Type.of(struct.name());
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }

    public static Value deconstruct(ScopeWalker<Value> walker, Value expression, int index) {
        return switch (expression) {
            case Value.Struct struct -> {
                final Value.StructDecl decl = (Value.StructDecl) walker.find(struct.name());
                final String fieldName = decl.parameters().get(index).name();
                yield struct.parameters().get(fieldName);
            }
            default -> throw new RuntimeException("Cannot deconstruct: " + expression);
        };
    }

    public static Value updateVariable(Value variable, AccessPoint accessPoint, Value updated) {
        if (accessPoint == null) return updated;
        return switch (variable) {
            case Value.Struct struct -> {
                if (accessPoint instanceof AccessPoint.Field field) {
                    final List<String> components = field.components();
                    final HashMap<String, Value> newParams = new HashMap<>(struct.parameters());
                    final String targetName = components.get(0);
                    if (components.size() == 1) {
                        newParams.put(targetName, updated);
                    } else {
                        final Value prevValue = newParams.get(targetName);
                        final AccessPoint.Field recursiveAccess = new AccessPoint.Field(components.subList(1, components.size()));
                        final Value recursiveValue = updateVariable(prevValue, recursiveAccess, updated);
                        newParams.put(targetName, recursiveValue);
                    }
                    yield new Value.Struct(struct.name(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            default -> throw new RuntimeException("Cannot update: " + variable + " " + accessPoint);
        };
    }
}
