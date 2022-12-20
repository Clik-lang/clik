package org.click;

import java.util.List;
import java.util.stream.Collectors;

public interface Type {
    Type VOID = new Primitive("void");
    Type BOOL = new Primitive("bool");
    Type I8 = new Primitive("i8");
    Type U8 = new Primitive("u8");
    Type I16 = new Primitive("i16");
    Type U16 = new Primitive("u16");
    Type I32 = new Primitive("i32");
    Type U32 = new Primitive("u32");
    Type I64 = new Primitive("i64");
    Type U64 = new Primitive("u64");
    Type F32 = new Primitive("f32");
    Type F64 = new Primitive("f64");
    Type INT = new Primitive("int");
    Type UINT = new Primitive("uint");
    Type RUNE = new Primitive("rune");
    Type STRING = new Primitive("string");

    String name();

    boolean primitive();

    record Identifier(String name) implements Type {
        @Override
        public boolean primitive() {
            return false;
        }
    }

    record Array(Type type, long length) implements Type {
        @Override
        public String name() {
            return "[]" + type.name();
        }

        @Override
        public boolean primitive() {
            return false;
        }
    }

    record Map(Type key, Type value) implements Type {
        @Override
        public String name() {
            return "map[" + key.name() + "]" + value.name();
        }

        @Override
        public boolean primitive() {
            return false;
        }
    }

    record Function(List<Parameter> parameters, Type returnType) implements Type {
        @Override
        public String name() {
            return "(" + parameters.stream().map(Parameter::name).collect(Collectors.joining(", ")) + ") " + returnType.name();
        }

        @Override
        public boolean primitive() {
            return false;
        }
    }

    record Primitive(String name) implements Type {
        public boolean primitive() {
            return true;
        }
    }

    static Type of(String name) {
        return switch (name) {
            case "void" -> VOID;
            case "bool" -> BOOL;
            case "i8" -> I8;
            case "u8" -> U8;
            case "i16" -> I16;
            case "u16" -> U16;
            case "i32" -> I32;
            case "u32" -> U32;
            case "i64" -> I64;
            case "u64" -> U64;
            case "f32" -> F32;
            case "f64" -> F64;
            case "int" -> INT;
            case "uint" -> UINT;
            case "rune" -> RUNE;
            case "string" -> STRING;
            default -> new Identifier(name);
        };
    }
}
