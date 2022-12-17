package org.click;

import java.util.List;

public sealed interface AccessPoint {
    record Field(List<String> components) implements AccessPoint {
    }

    record Index(Expression expression) implements AccessPoint {
    }
}
