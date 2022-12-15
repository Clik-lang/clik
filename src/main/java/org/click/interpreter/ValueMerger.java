package org.click.interpreter;

import org.click.Type;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ValueMerger {
    public static void update(ScopeWalker<Value> walker, ScopeWalker<Value> updated) {
        for (Map.Entry<String, Value> entry : updated.currentScope().tracked.entrySet()) {
            final String name = entry.getKey();
            final Value value = entry.getValue();
            walker.update(name, value);
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
                if (!Objects.equals(initial, value)) {
                    final Value current = changes.computeIfAbsent(name, s -> initial);
                    final Value merged = ValueMerger.merge(current, value);
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
        if (initial instanceof Value.Constant initialConstant && next instanceof Value.Constant nextConstant) {
            final Object initialValue = initialConstant.value();
            final Object nextValue = nextConstant.value();
            if (initialValue instanceof Integer initialInt && nextValue instanceof Integer nextInt) {
                return new Value.Constant(Type.I32, initialInt + nextInt);
            } else if (initialValue instanceof Boolean initialBool && nextValue instanceof Boolean nextBool) {
                return new Value.Constant(Type.BOOL, initialBool && nextBool);
            } else {
                throw new RuntimeException("Unknown types: " + initialValue.getClass() + " and " + nextValue.getClass());
            }
        } else {
            throw new RuntimeException("Unknown types: " + initial + " and " + next);
        }
    }
}
