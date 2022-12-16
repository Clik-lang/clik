package org.click;

public sealed interface Directive {
    sealed interface Statement extends Directive {
        record Load(String path) implements Statement {
        }

        record Intrinsic() implements Statement {
        }

        record Sleep(Expression expression) implements Statement {
        }
    }
}
