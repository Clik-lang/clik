package org.click;

import org.jetbrains.annotations.Nullable;

public sealed interface AccessPoint {
    record Field(String component) implements AccessPoint {
    }

    record Index(Expression expression, @Nullable Type transmuteType) implements AccessPoint {
    }
}
