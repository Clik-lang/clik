package org.click.value;

import org.click.Token;
import org.click.Type;

import java.math.BigDecimal;

public final class ValueOperator {
    public static Value operate(Token.Type operator, Value left, Value right) {
        if (left instanceof Value.NumberLiteral leftLiteral && right instanceof Value.NumberLiteral rightLiteral) {
            assert leftLiteral.type().equals(rightLiteral.type()) : "leftLiteral.type() = " + leftLiteral.type() + ", rightLiteral.type() = " + rightLiteral.type();

            return operateInteger(operator, leftLiteral.type(), leftLiteral.value(), rightLiteral.value());
        } else if (left instanceof Value.BooleanLiteral leftLiteral && right instanceof Value.BooleanLiteral rightLiteral) {
            return operateBoolean(operator, leftLiteral.value(), rightLiteral.value());
        } else if (left instanceof Value.StringLiteral || right instanceof Value.StringLiteral) {
            if (operator != Token.Type.PLUS)
                throw new RuntimeException("Unknown string operator: " + operator);
            final String leftString = ValueSerializer.serialize(null, left);
            final String rightString = ValueSerializer.serialize(null, right);
            return new Value.StringLiteral(leftString + rightString);
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

    private static Value operateInteger(Token.Type operator, Type type, BigDecimal left, BigDecimal right) {
        boolean isComparison = false;
        final BigDecimal result = switch (operator) {
            case PLUS -> left.add(right);
            case MINUS -> left.subtract(right);
            case STAR -> left.multiply(right);
            case SLASH -> left.divide(right);
            case EQUAL_EQUAL -> {
                isComparison = true;
                yield left.compareTo(right) == 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            case GREATER -> {
                isComparison = true;
                yield left.compareTo(right) > 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            case GREATER_EQUAL -> {
                isComparison = true;
                yield left.compareTo(right) >= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            case LESS -> {
                isComparison = true;
                yield left.compareTo(right) < 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            case LESS_EQUAL -> {
                isComparison = true;
                yield left.compareTo(right) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            default -> throw new RuntimeException("Unknown operator: " + operator);
        };
        return isComparison ? new Value.BooleanLiteral(result.intValue() == 1) : new Value.NumberLiteral(type, result);
    }
}
