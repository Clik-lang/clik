package org.click.interpreter;

import org.click.AccessPoint;
import org.click.Expression;
import org.click.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class ValueExtractor {
    public static Type extractAssignmentType(Value expression) {
        return switch (expression) {
            case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
            case Value.FloatLiteral floatLiteral -> floatLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.Struct struct -> Type.of(struct.name());
            case Value.Array array -> array.type().type();
            case Value.Map map -> map.type().value();
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

    public static Value updateVariable(Executor executor, Value variable, AccessPoint accessPoint, Value updated) {
        final List<AccessPoint.Access> accesses = accessPoint.accesses();
        if (accesses.isEmpty()) return updated;
        final AccessPoint.Access access = accesses.get(0);
        return switch (variable) {
            case Value.Struct struct -> {
                if (access instanceof AccessPoint.Field field) {
                    final String component = field.component();
                    final HashMap<String, Value> newParams = new HashMap<>(struct.parameters());
                    if (accesses.size() == 1) {
                        newParams.put(component, updated);
                    } else {
                        final Value prevValue = newParams.get(component);
                        final AccessPoint recursiveAccess = new AccessPoint(accesses.subList(1, accesses.size()));
                        final Value recursiveValue = updateVariable(executor, prevValue, recursiveAccess, updated);
                        newParams.put(component, recursiveValue);
                    }
                    yield new Value.Struct(struct.name(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            case Value.Array array -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final List<Value> newParams = new ArrayList<>(array.elements());
                    final Value index = executor.evaluate(indexExpression, null);
                    final int targetIndex = (int) ((Value.IntegerLiteral) index).value();
                    newParams.set(targetIndex, updated);
                    yield new Value.Array(array.type(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            case Value.Map map -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final HashMap<Value, Value> newParams = new HashMap<>(map.entries());
                    final Value index = executor.evaluate(indexExpression, map.type().key());
                    newParams.put(index, updated);
                    yield new Value.Map(map.type(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            default -> throw new RuntimeException("Cannot update: " + variable + " " + accessPoint);
        };
    }
}
