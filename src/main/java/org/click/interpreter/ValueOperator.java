package org.click.interpreter;

import org.click.Token;
import org.click.Type;

public final class ValueOperator {
    public static Value operate(Token operator, Value left, Value right) {
        if (left instanceof Value.Constant leftConstant && right instanceof Value.Constant rightConstant) {
            return operateConstant(operator, leftConstant, rightConstant);
        } else {
            throw new RuntimeException("Unknown types: " + left + " and " + right);
        }
    }

    private static Value operateConstant(Token operator, Value.Constant left, Value.Constant right) {
        assert left.type().equals(right.type()) : "Left type: " + left.type() + ", right type: " + right.type();
        final Type type = left.type();
        final Object leftValue = left.value();
        final Object rightValue = right.value();
        if (leftValue instanceof Integer leftInt && rightValue instanceof Integer rightInt) {
            boolean isComparison = false;
            final int result = switch (operator.type()) {
                case PLUS -> leftInt + rightInt;
                case MINUS -> leftInt - rightInt;
                case STAR -> leftInt * rightInt;
                case SLASH -> leftInt / rightInt;
                case EQUAL_EQUAL -> {
                    isComparison = true;
                    yield leftInt.equals(rightInt) ? 1 : 0;
                }
                case GREATER -> {
                    isComparison = true;
                    yield leftInt > rightInt ? 1 : 0;
                }
                case GREATER_EQUAL -> {
                    isComparison = true;
                    yield leftInt >= rightInt ? 1 : 0;
                }
                case LESS -> {
                    isComparison = true;
                    yield leftInt < rightInt ? 1 : 0;
                }
                case LESS_EQUAL -> {
                    isComparison = true;
                    yield leftInt <= rightInt ? 1 : 0;
                }
                default -> throw new RuntimeException("Unknown operator: " + operator);
            };
            return isComparison ? new Value.Constant(Type.BOOL, result == 1) : new Value.Constant(type, result);
        } else if (leftValue instanceof Boolean leftBool && rightValue instanceof Boolean rightBool) {
            final boolean result = switch (operator.type()) {
                case OR -> leftBool || rightBool;
                case AND -> leftBool && rightBool;
                case EQUAL_EQUAL -> leftBool.equals(rightBool);
                default -> throw new RuntimeException("Unknown operator: " + operator);
            };
            return new Value.Constant(Type.BOOL, result);
        } else {
            throw new RuntimeException("Unknown types: " + leftValue.getClass() + " and " + rightValue.getClass());
        }
    }
}
