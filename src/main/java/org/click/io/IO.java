package org.click.io;

import org.click.value.Value;

public sealed interface IO {
    void init();

    void close();

    non-sealed interface In extends IO {
        Value await();
    }

    non-sealed interface Out extends IO {
        void send(Value value);
    }

    interface Inout extends In, Out {
    }
}
