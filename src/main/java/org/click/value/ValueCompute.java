package org.click.value;

import org.click.AccessPoint;
import org.click.Expression;
import org.click.ScopeWalker;
import org.click.Type;
import org.click.interpreter.Executor;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;
import java.lang.foreign.ValueLayout;
import java.util.*;
import java.util.stream.LongStream;

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
                if (!Objects.equals(value, copyValue)) {
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
            for (Value delta : values) {
                value = ValueCompute.mergeDelta(value, delta);
            }
            walker.update(name, value);
        }
    }

    public static Value mergeDelta(Value initial, Value delta) {
        if (initial instanceof Value.IntegerLiteral initialConstant && delta instanceof Value.IntegerLiteral nextConstant) {
            final long value = initialConstant.value() + nextConstant.value();
            return new Value.IntegerLiteral(initialConstant.type(), value);
        } else if (initial instanceof Value.BooleanLiteral initialConstant && delta instanceof Value.BooleanLiteral nextConstant) {
            final boolean value = initialConstant.value() || nextConstant.value();
            return new Value.BooleanLiteral(value);
        } else if (initial instanceof Value.Table initialTable && delta instanceof Value.Table nextTable) {
            List<Value> values = new ArrayList<>(initialTable.values());
            values.addAll(nextTable.values());
            return new Value.Table(initialTable.tableType(), values);
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + delta);
        }
    }

    public static Value delta(Value initial, Value next) {
        if (initial instanceof Value.IntegerLiteral initialConstant && next instanceof Value.IntegerLiteral nextConstant) {
            final long delta = nextConstant.value() - initialConstant.value();
            return new Value.IntegerLiteral(initialConstant.type(), delta);
        } else if (initial instanceof Value.BooleanLiteral initialConstant && next instanceof Value.BooleanLiteral nextConstant) {
            final boolean value = initialConstant.value() || nextConstant.value();
            return new Value.BooleanLiteral(value);
        } else if (initial instanceof Value.Table initialTable && next instanceof Value.Table nextTable) {
            // Value/Occurrence
            Map<Value, Integer> valueInitOccurrences = new HashMap<>();
            Map<Value, Integer> valueNextOccurrences = new HashMap<>();
            for (Value value : initialTable.values()) valueInitOccurrences.merge(value, 1, Integer::sum);
            for (Value value : nextTable.values()) valueNextOccurrences.merge(value, 1, Integer::sum);
            Set<Value> keys = new HashSet<>();
            keys.addAll(valueInitOccurrences.keySet());
            keys.addAll(valueNextOccurrences.keySet());
            List<Value> values = new ArrayList<>();
            for (Value key : keys) {
                final Integer initOccurrences = valueInitOccurrences.getOrDefault(key, 0);
                final Integer nextOccurrences = valueNextOccurrences.getOrDefault(key, 0);
                final int occurrences = nextOccurrences - initOccurrences;
                for (int i = 0; i < occurrences; i++) {
                    values.add(key);
                }
            }
            return new Value.Table(initialTable.tableType(), values);
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + next);
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

    public static Value computeArray(Executor executor, Type.Array arrayType, @Nullable List<Expression> expressions) {
        final Type elementType = arrayType.type();
        final long length = arrayType.length();
        if (elementType instanceof Type.Primitive) {
            // Primitive types are stored in a single segment
            final long sizeof = ValueType.sizeOf(elementType);
            MemorySegment segment = MemorySegment.allocateNative(length * sizeof, SegmentScope.auto());
            if (expressions != null) {
                for (int i = 0; i < expressions.size(); i++) {
                    final Expression expression = expressions.get(i);
                    final Value value = executor.evaluate(expression, elementType);
                    final long index = i * sizeof;
                    assignArrayBuffer(elementType, value, segment, index);
                }
            }
            return new Value.ArrayValue(arrayType, segment);
        } else {
            // Reference type
            final List<Value> evaluated;
            if (expressions != null) {
                // Initialized array
                evaluated = expressions.stream()
                        .map(expression -> executor.evaluate(expression, elementType)).toList();
            } else {
                // Default value
                final Value defaultValue = ValueType.defaultValue(elementType);
                evaluated = LongStream.range(0, length).mapToObj(i -> defaultValue).toList();
            }
            return new Value.ArrayRef(arrayType, evaluated);
        }
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
            case Value.ArrayRef arrayRef -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final List<Value> newParams = new ArrayList<>(arrayRef.elements());
                    final Value index = executor.evaluate(indexExpression, null);
                    final int targetIndex = (int) ((Value.IntegerLiteral) index).value();
                    newParams.set(targetIndex, updated);
                    yield new Value.ArrayRef(arrayRef.arrayType(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            case Value.ArrayValue arrayValue -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final MemorySegment newData = MemorySegment.allocateNative(arrayValue.data().byteSize(), SegmentScope.auto());
                    newData.copyFrom(arrayValue.data());
                    final Value indexExpr = executor.evaluate(indexExpression, null);
                    final long index = ((Value.IntegerLiteral) indexExpr).value() * ValueType.sizeOf(arrayValue.arrayType().type());
                    final Type type = Objects.requireNonNullElse(indexAccess.transmuteType(), arrayValue.arrayType().type());
                    ValueCompute.assignArrayBuffer(type, updated, newData, index);
                    yield new Value.ArrayValue(arrayValue.arrayType(), newData);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            case Value.Map map -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final HashMap<Value, Value> newParams = new HashMap<>(map.entries());
                    final Value index = executor.evaluate(indexExpression, map.mapType().key());
                    newParams.put(index, updated);
                    yield new Value.Map(map.mapType(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            default -> throw new RuntimeException("Cannot update: " + variable + " " + accesses);
        };
    }
}
