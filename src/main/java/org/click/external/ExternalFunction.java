package org.click.external;

import org.click.value.Value;

public interface ExternalFunction {
    Value run(Value... args);
}
