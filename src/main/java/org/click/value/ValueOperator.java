package org.click.value;

import org.click.BinStandard;
import org.click.Token;

import java.lang.foreign.MemorySegment;
import java.math.BigDecimal;

public final class ValueOperator {
    public static Value operate(Token.Type operator, Value left, Value right) {
        return switch (left) {
            case Value.NumberLiteral leftLiteral when right instanceof Value.NumberLiteral rightLiteral ->
                    operateInteger(operator, leftLiteral.value(), rightLiteral.value());
            case Value.BooleanLiteral leftLiteral when right instanceof Value.BooleanLiteral rightLiteral ->
                    operateBoolean(operator, leftLiteral.value(), rightLiteral.value());
            case Value.Binary leftBin when right instanceof Value.Binary rightBin -> {
                if (leftBin.standard() != rightBin.standard())
                    throw new RuntimeException("Cannot operate on different binaries: " + leftBin.standard() + " and " + rightBin.standard());
                final BinStandard standard = leftBin.standard();
                final MemorySegment segment = standard.operate(leftBin.segment(), rightBin.segment(), operator);
                yield new Value.Binary(standard, segment);
            }
            default -> throw new RuntimeException("Unknown types: " + left + " and " + right);
        };
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

    private static Value operateInteger(Token.Type operator, BigDecimal left, BigDecimal right) {
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
        return isComparison ? new Value.BooleanLiteral(result.intValue() == 1) : new Value.NumberLiteral(result);
    }
}
