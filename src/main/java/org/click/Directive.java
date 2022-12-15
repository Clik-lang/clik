package org.click;

public sealed interface Directive {
    sealed interface Statement extends Directive {
        record Sleep(Expression expression) implements Statement {
        }
    }
}
