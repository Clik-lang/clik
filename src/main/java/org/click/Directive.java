package org.click;

public sealed interface Directive {
    sealed interface Statement extends Directive {
        record Load(String path) implements Statement {
        }
    }
}
