package io.github.stigjohnny.c4contractkit.api;

/**
 * One import, or one construct the restricted grammar refused, as evidence.
 *
 * @param file the importing file, relative to the repository root, with {@code /} as separator
 * @param line the line of the specifier (or of the refused construct), from 1
 * @param specifier the module specifier as written; empty for a refused construct that has none
 * @param kind {@code import}, {@code import type}, {@code side-effect import}, {@code import()}, {@code export from},
 *     {@code export type from}, or {@code refused}
 * @param target the file (relative to the repository root) or package the specifier names; empty when it names
 *     nothing certain
 */
public record ImportEvidence(String file, int line, String specifier, String kind, String target) {
}
