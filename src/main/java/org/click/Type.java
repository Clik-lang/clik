package org.click;

public record Type(String name, boolean primitive) {
    public static final Type VOID = new Type("void", true);
    public static final Type BOOL = new Type("bool", true);
    public static final Type I8 = new Type("i8", true);
    public static final Type U8 = new Type("u8", true);
    public static final Type I16 = new Type("i16", true);
    public static final Type U16 = new Type("u16", true);
    public static final Type I32 = new Type("i32", true);
    public static final Type U32 = new Type("u32", true);
    public static final Type I64 = new Type("i64", true);
    public static final Type U64 = new Type("u64", true);
    public static final Type F32 = new Type("f32", true);
    public static final Type F64 = new Type("f64", true);
    public static final Type STRING = new Type("string", true);

    public static Type of(String name) {
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
            case "string" -> STRING;
            default -> new Type(name, false);
        };
    }
}
