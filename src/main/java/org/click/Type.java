package org.click;

import java.util.List;
import java.util.stream.Collectors;

import static org.click.Ast.Parameter;

public interface Type {
    Type VOID = new Primitive("void");
    Type BOOL = new Primitive("bool");
    Type RUNE = new Primitive("rune");
    Type NUMBER = new Primitive("number");
    Type STRING = new BuiltIn("string");

    String name();

    record Identifier(String name) implements Type {
    }

    record Array(Type type, long length) implements Type {
        @Override
        public String name() {
            return "[]" + type.name();
        }
    }

    record Function(List<Parameter> parameters, Type returnType) implements Type {
        @Override
        public String name() {
            return "(" + parameters.stream().map(Parameter::name).collect(Collectors.joining(", ")) + ") " + returnType.name();
        }
    }

    /**
     * Constant sized type.
     */
    record Primitive(String name) implements Type {
    }

    /**
     * Convenient type supported by the language.
     */
    record BuiltIn(String name) implements Type {
    }

    enum NumberSet {
        NATURAL, INTEGER, RATIONAL, REAL
    }

    static Type of(String name) {
        return switch (name) {
            case "void" -> VOID;
            case "bool" -> BOOL;
            case "number" -> NUMBER;
            case "rune" -> RUNE;
            case "string" -> STRING;
            default -> new Identifier(name);
        };
    }
}
