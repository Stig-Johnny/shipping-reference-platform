package io.github.stigjohnny.c4contractkit.sources;

/** How a source file depends on a module. Every kind is a dependency; the kind is evidence, not an exemption. */
public enum ImportKind {
    /** {@code import a from 'x'}, {@code import { a } from 'x'}, {@code import * as a from 'x'}. */
    IMPORT("import"),
    /** {@code import type { A } from 'x'}: erased when compiled, still a dependency of the source. */
    TYPE_IMPORT("import type"),
    /** {@code import 'x'}: imported for its side effects, such as a style sheet. */
    SIDE_EFFECT("side-effect import"),
    /** {@code import('x')} with one plain string literal, in an expression or a type. */
    DYNAMIC("import()"),
    /** {@code export * from 'x'}, {@code export { a } from 'x'}. */
    RE_EXPORT("export from"),
    /** {@code export type { A } from 'x'}, {@code export type * from 'x'}. */
    TYPE_RE_EXPORT("export type from");

    private final String label;

    ImportKind(String label) {
        this.label = label;
    }

    /** The kind as reports show it, for example {@code import type}. */
    public String label() {
        return label;
    }
}
