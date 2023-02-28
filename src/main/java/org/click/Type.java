package org.click;

import java.util.List;
import java.util.stream.Collectors;

import static org.click.Ast.Parameter;

public interface Type {
    Type VOID = new Primitive("void");
    Type BOOL = new Primitive("bool");
    Type RUNE = new Primitive("rune");

    Type NATURAL = new Number(NumberSet.NATURAL);
    Type INT = new Number(NumberSet.INTEGER);
    Type RATIONAL = new Number(NumberSet.RATIONAL);
    Type REAL = new Number(NumberSet.REAL);
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

    record Number(NumberSet set) implements Type {
        @Override
        public String name() {
            final String prefix = "number";
            final String suffix = switch (set) {
                case NATURAL -> "n";
                case INTEGER -> "i";
                case RATIONAL -> "q";
                case REAL -> "R";
            };
            return prefix + "'" + suffix;
        }
    }

    enum NumberSet {
        NATURAL, INTEGER, RATIONAL, REAL
    }

    static Type of(String name) {
        return switch (name) {
            case "void" -> VOID;
            case "bool" -> BOOL;
            case "number'n", "number'N" -> NATURAL;
            case "number'i", "number'I" -> INT;
            case "number'q", "number'Q" -> RATIONAL;
            case "number", "number'R" -> REAL;
            case "rune" -> RUNE;
            case "string" -> STRING;
            default -> new Identifier(name);
        };
    }
}
