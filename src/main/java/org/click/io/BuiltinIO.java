package org.click.io;

import org.click.Type;
import org.click.value.Value;

import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public final class BuiltinIO {
    public static final class Printer implements IO.Out {
        @Override
        public void init() {
        }

        @Override
        public void send(Value value) {
            System.out.println(value);
        }

        @Override
        public void close() {
        }
    }

    public static final class IntReader implements IO.In {
        private Thread thread;
        private volatile boolean running = true;
        private volatile int counter = 0;
        private final AtomicInteger counterAtomic = new AtomicInteger(0);
        private final AtomicInteger value = new AtomicInteger(0);

        @Override
        public void init() {
            this.thread = Thread.startVirtualThread(() -> {
                // Read integer from console
                Scanner scanner = new Scanner(System.in);
                while (running) {
                    final int value = scanner.nextInt();
                    this.value.set(value);
                    this.counterAtomic.incrementAndGet();
                }
            });
        }

        @Override
        public Value await() {
            while (true) {
                final int current = counterAtomic.get();
                if (current > counter) {
                    counter = current;
                    break;
                }
            }
            return new Value.NumberLiteral(Type.INT, value.get());
        }

        @Override
        public void close() {
            this.running = false;
            this.thread.interrupt();
        }
    }
}
