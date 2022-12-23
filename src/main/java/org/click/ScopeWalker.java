package org.click;

import org.click.interpreter.Executor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ScopeWalker<T> {
    final ArrayDeque<Scope> scopes = new ArrayDeque<>();

    public void enterBlock(Executor executor) {
        final Scope currentScope = scopes.peek();
        if (currentScope == null) {
            this.scopes.push(new Scope(executor, new HashMap<>()));
        } else {
            this.scopes.push(new Scope(executor, currentScope));
        }
    }

    public void exitBlock() {
        this.scopes.pop();
    }

    public void register(@NotNull String name, @UnknownNullability T value) {
        currentScope().register(name, value);
    }

    public void update(@NotNull String name, @NotNull T value) {
        currentScope().update(name, value);
    }

    public @UnknownNullability T find(@NotNull String name) {
        return currentScope().tracked.get(name);
    }

    public @NotNull Scope currentScope() {
        final Scope currentScope = scopes.peek();
        assert currentScope != null;
        return currentScope;
    }

    public final class Scope {
        final Executor executor;
        final @Nullable Scope parent;
        final @NotNull Map<String, T> tracked;

        public Scope(Executor executor, @NotNull Map<String, T> tracked) {
            this.executor = executor;
            this.parent = null;
            this.tracked = tracked;
        }

        Scope(Executor executor, Scope scope) {
            this.executor = executor;
            this.parent = scope;
            this.tracked = new HashMap<>(scope.tracked);
        }

        public Map<String, T> tracked() {
            return Map.copyOf(tracked);
        }

        void register(String name, T value) {
            this.tracked.put(name, value);
        }

        void update(String name, T value) {
            Objects.requireNonNull(value);
            this.tracked.put(name, value);
            if (parent != null) {
                parent.update(name, value);
            }
        }
    }
}