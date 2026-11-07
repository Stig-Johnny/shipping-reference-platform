package io.github.stigjohnny.c4contractkit.api;

/**
 * One direct static reference from a class to another class, as read from the origin's class file.
 *
 * @param originClass the binary name of the referencing class, for example {@code fx.shop.orders.Order$Line}
 * @param originMember the referencing member as {@code name(parameter types)} for methods and constructors
 *     ({@code <init>}, {@code <clinit>}), the field name for fields, or the empty string for the class itself
 * @param targetClass the binary name of the referenced class; for an array type, its element type
 * @param targetMember the referenced method, constructor or field in the same notation, or the empty string when the
 *     reference is to the type itself
 * @param kind what the reference is, for example {@code method call}, {@code field type} or {@code annotation}
 * @param sourceFile the origin's source file name as recorded in the class file, or the empty string when unknown
 * @param line the source line, or 0 when unknown (references in signatures carry no line)
 */
public record Evidence(String originClass, String originMember, String targetClass, String targetMember, String kind,
                       String sourceFile, int line) {
}
