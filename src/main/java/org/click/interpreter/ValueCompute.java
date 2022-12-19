package org.click.interpreter;

import org.click.AccessPoint;
import org.click.Expression;
import org.click.Type;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

public final class ValueCompute {
    public static void update(ScopeWalker<Value> walker, ScopeWalker<Value> updated) {
        for (Map.Entry<String, Value> entry : updated.currentScope().tracked.entrySet()) {
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
        Map<String, Value> initials = walker.currentScope().tracked;
        Map<String, Value> changes = new HashMap<>();
        for (ScopeWalker<Value> copy : copies) {
            for (Map.Entry<String, Value> entry : copy.currentScope().tracked.entrySet()) {
                final String name = entry.getKey();
                final Value value = entry.getValue();
                final Value initial = initials.get(name);
                if (initial != null && !initial.equals(value)) {
                    final Value current = changes.computeIfAbsent(name, s -> initial);
                    final Value merged = ValueCompute.merge(current, value);
                    changes.put(name, merged);
                }
            }
        }
        for (Map.Entry<String, Value> entry : changes.entrySet()) {
            final String name = entry.getKey();
            final Value value = entry.getValue();
            walker.update(name, value);
        }
    }

    public static Value merge(Value initial, Value next) {
        if (initial instanceof Value.IntegerLiteral initialConstant && next instanceof Value.IntegerLiteral nextConstant) {
            final long value = initialConstant.value() + nextConstant.value();
            return new Value.IntegerLiteral(initialConstant.type(), value);
        } else if (initial instanceof Value.BooleanLiteral initialConstant && next instanceof Value.BooleanLiteral nextConstant) {
            final boolean value = initialConstant.value() || nextConstant.value();
            return new Value.BooleanLiteral(value);
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + next);
        }
    }

    public static Value delta(Value initial, Value next) {
        if (initial instanceof Value.IntegerLiteral initialConstant && next instanceof Value.IntegerLiteral nextConstant) {
            final long delta = nextConstant.value() - initialConstant.value();
            return new Value.IntegerLiteral(initialConstant.type(), delta);
        } else if (initial instanceof Value.BooleanLiteral initialConstant && next instanceof Value.BooleanLiteral nextConstant) {
            final boolean value = initialConstant.value() || nextConstant.value();
            return new Value.BooleanLiteral(value);
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + next);
        }
    }

    public static void setSegment(Type type, Value value, MemorySegment segment, long index) {
        if (value instanceof Value.IntegerLiteral literal) {
            if (type == Type.I8) {
                segment.set(ValueLayout.JAVA_BYTE, index, (byte) literal.value());
            } else if (type == Type.I16) {
                segment.setAtIndex(ValueLayout.JAVA_SHORT, index, (short) literal.value());
            } else if (type == Type.I32) {
                segment.setAtIndex(ValueLayout.JAVA_INT, index, (int) literal.value());
            } else if (type == Type.I64) {
                segment.setAtIndex(ValueLayout.JAVA_LONG, index, literal.value());
            } else if (type == Type.INT) {
                segment.setAtIndex(ValueLayout.JAVA_LONG, index, literal.value());
            } else {
                throw new RuntimeException("Unknown integer type: " + type);
            }
        } else if (value instanceof Value.FloatLiteral literal) {
            if (type == Type.F32) {
                segment.setAtIndex(ValueLayout.JAVA_FLOAT, index, (float) literal.value());
            } else if (type == Type.F64) {
                segment.setAtIndex(ValueLayout.JAVA_DOUBLE, index, literal.value());
            } else {
                throw new RuntimeException("Unknown float type: " + type);
            }
        } else {
            throw new RuntimeException("Unknown type: " + value);
        }
    }

    public static Value lookupArray(Type type, MemorySegment data, long index) {
        if (type == Type.I8) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_BYTE, index));
        } else if (type == Type.I16) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_SHORT, index));
        } else if (type == Type.I32) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_INT, index));
        } else if (type == Type.I64) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_LONG, index));
        } else if (type == Type.F32) {
            return new Value.FloatLiteral(type, data.get(ValueLayout.JAVA_FLOAT, index));
        } else if (type == Type.F64) {
            return new Value.FloatLiteral(type, data.get(ValueLayout.JAVA_DOUBLE, index));
        } else if (type == Type.INT) {
            return new Value.IntegerLiteral(type, data.get(ValueLayout.JAVA_LONG, index));
        } else {
            throw new RuntimeException("Unknown type: " + type);
        }
    }

    public static Value computeArray(Executor executor, Type.Array arrayType, @Nullable List<Expression> expressions) {
        final Type elementType = arrayType.type();
        final long length = arrayType.length();
        if (elementType.primitive() && elementType != Type.STRING) {
            // Primitive types are stored in a single segment
            final long sizeof = sizeOf(elementType);
            MemorySegment segment = MemorySegment.allocateNative(length * sizeof, MemorySession.openImplicit());
            if (expressions != null) {
                for (int i = 0; i < expressions.size(); i++) {
                    final Expression expression = expressions.get(i);
                    final Value value = executor.evaluate(expression, elementType);
                    setSegment(elementType, value, segment, i);
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
                final Value defaultValue = ValueCompute.defaultValue(elementType);
                evaluated = LongStream.range(0, length).mapToObj(i -> defaultValue).toList();
            }
            return new Value.ArrayRef(arrayType, evaluated);
        }
    }

    public static Value defaultValue(Type type) {
        if (type == Type.U8 || type == Type.U16 || type == Type.U32 || type == Type.U64 ||
                type == Type.I8 || type == Type.I16 || type == Type.I32 || type == Type.I64 || type == Type.INT) {
            return new Value.IntegerLiteral(type, 0);
        }
        if (type == Type.F32 || type == Type.F64) {
            return new Value.FloatLiteral(type, 0);
        }
        if (type == Type.BOOL) {
            return new Value.BooleanLiteral(false);
        }
        throw new RuntimeException("Unknown type: " + type);
    }

    public static long sizeOf(Type type) {
        if (type == Type.BOOL) return 1;
        if (type == Type.I8) return 1;
        if (type == Type.U8) return 1;
        if (type == Type.I16) return 2;
        if (type == Type.U16) return 2;
        if (type == Type.I32) return 4;
        if (type == Type.U32) return 4;
        if (type == Type.I64) return 8;
        if (type == Type.U64) return 8;
        if (type == Type.F32) return 4;
        if (type == Type.F64) return 8;

        if (type == Type.INT) return 8;
        if (type == Type.UINT) return 8;
        throw new RuntimeException("Unknown type: " + type);
    }

    public static Type extractAssignmentType(Value expression) {
        return switch (expression) {
            case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
            case Value.FloatLiteral floatLiteral -> floatLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.Struct struct -> Type.of(struct.name());
            case Value.ArrayRef arrayRef -> arrayRef.type();
            case Value.ArrayValue arrayValue -> arrayValue.type();
            case Value.Map map -> map.type();
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
            case Value.ArrayRef arrayRef -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final List<Value> newParams = new ArrayList<>(arrayRef.elements());
                    final Value index = executor.evaluate(indexExpression, null);
                    final int targetIndex = (int) ((Value.IntegerLiteral) index).value();
                    newParams.set(targetIndex, updated);
                    yield new Value.ArrayRef(arrayRef.type(), newParams);
                } else {
                    throw new RuntimeException("Cannot update variable: " + variable);
                }
            }
            case Value.ArrayValue arrayValue -> {
                if (access instanceof AccessPoint.Index indexAccess) {
                    final Expression indexExpression = indexAccess.expression();
                    final MemorySegment newData = MemorySegment.allocateNative(arrayValue.data().byteSize(), MemorySession.openImplicit());
                    newData.copyFrom(arrayValue.data());
                    final Value index = executor.evaluate(indexExpression, null);
                    final long targetIndex = ((Value.IntegerLiteral) index).value();
                    ValueCompute.setSegment(arrayValue.type().type(), updated, newData, targetIndex);
                    yield new Value.ArrayValue(arrayValue.type(), newData);
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
