package org.click.interpreter;

import org.click.Type;

public final class ValueMerger {
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
