package org.click.interpreter;

import org.click.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            final boolean value = initialConstant.value() && nextConstant.value();
            return new Value.BooleanLiteral(value);
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + next);
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
}