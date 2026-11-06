package io.github.stigjohnny.c4contractkit.bytecode;

import java.util.Comparator;

/**
 * One direct reference from a class to another class, read from the origin's class file.
 *
 * @param originClass the binary name of the referencing class
 * @param originMember the referencing member, {@code name(parameter types)} or a field name; empty for the class
 * @param targetClass the binary name of the referenced class; for an array type, its element type
 * @param targetMember the referenced member in the same notation; empty when the type itself is referenced
 * @param kind what the reference is
 * @param sourceFile the source file recorded in the origin's class file, or empty
 * @param line the source line, or 0 when unknown
 */
public record CodeReference(String originClass, String originMember, String targetClass, String targetMember,
                            ReferenceKind kind, String sourceFile, int line) {

    /** Origin class, origin member, target class, target member, kind (by label), line, source file. */
    public static final Comparator<CodeReference> ORDER = Comparator.comparing(CodeReference::originClass)
            .thenComparing(CodeReference::originMember)
            .thenComparing(CodeReference::targetClass)
            .thenComparing(CodeReference::targetMember)
            .thenComparing(reference -> reference.kind().label())
            .thenComparingInt(CodeReference::line)
            .thenComparing(CodeReference::sourceFile);
}
