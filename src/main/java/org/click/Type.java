package org.click;

public record Type(String name, boolean primitive) {
    public static final Type I32 = new Type("i32", true);
}
