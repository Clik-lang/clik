package org.click.interpreter;

import org.click.Token;
import org.click.Type;

public final class ValueOperator {
    public static Value operate(Token.Type operator, Value left, Value right) {
        if (left instanceof Value.IntegerLiteral leftLiteral && right instanceof Value.IntegerLiteral rightLiteral) {
            assert leftLiteral.type().equals(rightLiteral.type()) : "leftLiteral.type() = " + leftLiteral.type() + ", rightLiteral.type() = " + rightLiteral.type();

            return operateInteger(operator, leftLiteral.type(), leftLiteral.value(), rightLiteral.value());
        } else if (left instanceof Value.BooleanLiteral leftLiteral && right instanceof Value.BooleanLiteral rightLiteral) {
            return operateBoolean(operator, leftLiteral.value(), rightLiteral.value());
        } else {
            throw new RuntimeException("Unknown types: " + left + " and " + right);
        }
    }

    private static Value operateBoolean(Token.Type operator, boolean left, boolean right) {
        final boolean result = switch (operator) {
            case OR -> left || right;
            case AND -> left && right;
            case EQUAL_EQUAL -> left == right;
            default -> throw new RuntimeException("Unknown operator: " + operator);
        };
        return new Value.BooleanLiteral(result);
    }

    private static Value operateInteger(Token.Type operator, Type type, long left, long right) {
        boolean isComparison = false;
        final long result = switch (operator) {
            case PLUS -> left + right;
            case MINUS -> left - right;
            case STAR -> left * right;
            case SLASH -> left / right;
            case EQUAL_EQUAL -> {
                isComparison = true;
                yield left == right ? 1 : 0;
            }
            case GREATER -> {
                isComparison = true;
                yield left > right ? 1 : 0;
            }
            case GREATER_EQUAL -> {
                isComparison = true;
                yield left >= right ? 1 : 0;
            }
            case LESS -> {
                isComparison = true;
                yield left < right ? 1 : 0;
            }
            case LESS_EQUAL -> {
                isComparison = true;
                yield left <= right ? 1 : 0;
            }
            default -> throw new RuntimeException("Unknown operator: " + operator);
        };
        return isComparison ? new Value.BooleanLiteral(result == 1) : new Value.IntegerLiteral(type, result);
    }
}
