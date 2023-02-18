package org.click.io;

import org.click.value.Value;

public sealed interface IO {
    non-sealed interface In extends IO {
        void init();

        Value await();

        void close();
    }

    non-sealed interface Out extends IO {
        void init();

        void send(Value value);

        void close();
    }

    interface Inout extends In, Out {
    }
}
