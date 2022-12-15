package org.click.interpreter;

import org.click.Parameter;

import java.util.List;

record ValueSerializer(ScopeWalker<Value> walker) {
    String serialize(Value expression) {
        return switch (expression) {
            case Value.Constant constant -> constant.value().toString();
            case Value.Struct struct -> {
                if (!(walker.find(struct.name()) instanceof Value.StructDecl structDeclaration)) {
                    throw new RuntimeException("Struct not found: " + struct.name());
                }
                var parameters = structDeclaration.parameters();
                final StringBuilder builder = new StringBuilder();
                builder.append(struct.name()).append("{");
                for (int i = 0; i < parameters.size(); i++) {
                    final Parameter param = parameters.get(i);
                    final Value field = struct.parameters().get(param.name());
                    builder.append(serialize(field));
                    if (i < parameters.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append("}");
                yield builder.toString();
            }
            case Value.Union union -> {
                if (!(walker.find(union.name()) instanceof Value.UnionDecl)) {
                    throw new RuntimeException("Union not found: " + union.name());
                }
                final Value field = union.value();
                yield union.name() + "." + serialize(field);
            }
            case Value.Array array -> {
                final List<Value> values = array.values();
                final StringBuilder builder = new StringBuilder();
                builder.append("[");
                for (int i = 0; i < values.size(); i++) {
                    final Value value = values.get(i);
                    builder.append(serialize(value));
                    if (i < values.size() - 1) {
                        builder.append(", ");
                    }
                }
                builder.append("]");
                yield builder.toString();
            }
            case null, default -> throw new RuntimeException("Unknown expression: " + expression);
        };
    }
}
