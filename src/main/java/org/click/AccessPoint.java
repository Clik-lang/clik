package org.click;

import java.util.List;

public record AccessPoint(List<Access> accesses) {
    public sealed interface Access {
    }

    public record Field(String component) implements Access {
    }

    public record Index(Expression expression) implements Access {
    }
}
