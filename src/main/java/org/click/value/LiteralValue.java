package org.click.value;

import java.math.BigDecimal;

public sealed interface LiteralValue {
    record Text(String value) implements LiteralValue {
    }

    record Number(BigDecimal value) implements LiteralValue {
        public Number(java.lang.Number number) {
            this(BigDecimal.valueOf(number.longValue()));
        }
    }
}
