package org.click.value;

import org.click.ScopeWalker;
import org.click.Type;
import org.click.interpreter.Executor;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.click.Ast.AccessPoint;
import static org.click.Ast.Expression;

public final class ValueCompute {
    public static void update(ScopeWalker<Value> walker, ScopeWalker<Value> updated) {
        for (Map.Entry<String, Value> entry : updated.currentScope().tracked().entrySet()) {
            final String name = entry.getKey();
            final Value value = entry.getValue();
            if (walker.find(name) == null) {
                walker.register(name, value);
            } else {
                walker.update(name, value);
            }
        }
    }

    public static void merge(ScopeWalker<Value> walker, List<ScopeWalker<Value>> copies) {
        Map<String, List<Value>> deltas = new HashMap<>();
        // Compute deltas
        for (Map.Entry<String, Value> entry : walker.currentScope().tracked().entrySet()) {
            final String name = entry.getKey();
            final Value value = entry.getValue();
            for (ScopeWalker<Value> copy : copies) {
                final Value copyValue = copy.find(name);
                if (copyValue != null && !value.equals(copyValue)) {
                    final Value delta = ValueCompute.delta(value, copyValue);
                    deltas.computeIfAbsent(name, k -> new ArrayList<>()).add(delta);
                }
            }
        }
        // Apply deltas
        for (Map.Entry<String, List<Value>> entry : deltas.entrySet()) {
            final String name = entry.getKey();
            final List<Value> values = entry.getValue();
            Value value = walker.find(name);
            for (Value delta : values) value = ValueCompute.mergeDelta(value, delta);
            walker.update(name, value);
        }
    }

    public static Value mergeDelta(Value initial, Value delta) {
        switch (initial) {
            case Value.IntegerLiteral initialConstant when delta instanceof Value.IntegerLiteral nextConstant -> {
                final long value = initialConstant.value() + nextConstant.value();
                return new Value.IntegerLiteral(initialConstant.type(), value);
            }
            case Value.BooleanLiteral initialConstant when delta instanceof Value.BooleanLiteral nextConstant -> {
                final boolean value = initialConstant.value() || nextConstant.value();
                return new Value.BooleanLiteral(value);
            }
            default -> throw new RuntimeException("Unknown types: " + initial + " and " + delta);
        }
    }

    public static Value delta(Value initial, Value next) {
        switch (initial) {
            case Value.IntegerLiteral initialConstant when next instanceof Value.IntegerLiteral nextConstant -> {
                final long delta = nextConstant.value() - initialConstant.value();
                return new Value.IntegerLiteral(initialConstant.type(), delta);
            }
            case Value.BooleanLiteral initialConstant when next instanceof Value.BooleanLiteral nextConstant -> {
                final boolean value = initialConstant.value() || nextConstant.value();
                return new Value.BooleanLiteral(value);
            }
            default -> throw new RuntimeException("Unknown types: " + initial + " and " + next);
        }
    }

    public static void assignArrayBuffer(Type type, Value value, MemorySegment segment, long index) {
        if (value instanceof Value.IntegerLiteral literal) {
            if (type == Type.I8) {
                segment.set(ValueLayout.JAVA_BYTE, index, (byte) literal.value());
            } else if (type == Type.I16) {
                segment.set(ValueLayout.JAVA_SHORT_UNALIGNED, index, (short) literal.value());
            } else if (type == Type.I32) {
                segment.set(ValueLayout.JAVA_INT_UNALIGNED, index, (int) literal.value());
            } else if (type == Type.I64) {
                segment.set(ValueLayout.JAVA_LONG_UNALIGNED, index, literal.value());
            } else if (type == Type.INT) {
                segment.set(ValueLayout.JAVA_LONG_UNALIGNED, index, literal.value());
            } else {
                throw new RuntimeException("Unknown integer type: " + type);
            }
        } else if (value instanceof Value.FloatLiteral literal) {
            if (type == Type.F32) {
                segment.set(ValueLayout.JAVA_FLOAT_UNALIGNED, index, (float) literal.value());
            } else if (type == Type.F64) {
                segment.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, index, literal.value());
            } else {
                throw new RuntimeException("Unknown float type: " + type);
            }
        } else {
            throw new RuntimeException("Unknown type: " + value);
        }
    }

    public static Value lookupArrayBuffer(Type type, MemorySegment data, long index) {
        if (type == Type.I8) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_BYTE, index));
        } else if (type == Type.I16) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_SHORT_UNALIGNED, index));
        } else if (type == Type.I32) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_INT_UNALIGNED, index));
        } else if (type == Type.I64) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_LONG_UNALIGNED, index));
        } else if (type == Type.F32) {
            return new Value.FloatLiteral(type, data.get(ValueLayout.JAVA_FLOAT_UNALIGNED, index));
        } else if (type == Type.F64) {
            return new Value.FloatLiteral(type, data.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, index));
        } else if (type == Type.INT) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_LONG_UNALIGNED, index));
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }
    }

    public static Value computeArray(Executor executor, Type.Array arrayType, List<Expression> expressions) {
        final Type elementType = arrayType.type();
        final long length = arrayType.length();
        final List<Value> evaluated;
        if (!expressions.isEmpty()) {
            // Initialized array
            evaluated = expressions.stream()
                    .map(expression -> executor.evaluate(expression, elementType)).toList();
        } else {
            // Default value
            final Value defaultValue = ValueType.defaultValue(elementType);
            evaluated = LongStream.range(0, length).mapToObj(i -> defaultValue).toList();
        }
        return new Value.Array(arrayType, evaluated);
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

    public static Value updateVariable(Executor executor, Value variable, List<AccessPoint> accesses, Value updated) {
        if (accesses.isEmpty()) return updated;
        final AccessPoint access = accesses.get(0);
        return switch (variable) {
            case Value.Struct struct -> {
                if (access instanceof AccessPoint.Field field) {
                    final String component = field.component();
                    final HashMap<String, Value> newParams = new HashMap<>(struct.parameters());
                    if (accesses.size() == 1) {
                        newParams.put(component, updated);
                    } else {
                        final Value prevValue = newParams.get(component);
                        final List<AccessPoint> recursiveAccess = accesses.subList(1, accesses.size());
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
                    yield new Value.Array(array.arrayType(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            default -> throw new RuntimeException("Cannot update: " + variable + " " + accesses);
        };
    }
}
