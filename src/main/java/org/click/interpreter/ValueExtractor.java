package org.click.interpreter;

import org.click.Type;

public final class ValueExtractor {
    public static Type extractType(Value expression) {
        return switch (expression) {
            case Value.IntegerLiteral integerLiteral -> integerLiteral.type();
            case Value.FloatLiteral floatLiteral -> floatLiteral.type();
            case Value.BooleanLiteral ignored -> Type.BOOL;
            case Value.StringLiteral ignored -> Type.STRING;
            case Value.Struct struct -> Type.of(struct.name());
            default -> throw new RuntimeException("Unknown type: " + expression);
        };
    }

    public static Value deconstruct(ScopeWalker<Value> walker, Value expression, int index) {
        return switch (expression) {
            case Value.Struct struct -> {
                final Value.StructDecl decl = (Value.StructDecl) walker.find(struct.name());
                final String fieldName = decl.parameters().get(index).name();
                yield struct.parameters().get(fieldName);
            }
            default -> throw new RuntimeException("Cannot deconstruct: " + expression);
        };
    }
}
