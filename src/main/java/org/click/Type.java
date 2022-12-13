package org.click;

public record Type(String name, boolean primitive) {
    public static final Type VOID = new Type("void", true);
    public static final Type I32 = new Type("i32", true);

    public static Type of(String name) {
        return switch (name) {
            case "void" -> VOID;
            case "i32" -> I32;
            default -> new Type(name, false);
        };
    }
}
