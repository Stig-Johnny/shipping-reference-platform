package io.github.stigjohnny.c4contractkit.sources;

/**
 * One import of a code file and what its specifier names.
 *
 * @param file the importing file, relative to the web root
 * @param line the line of the specifier, from 1
 * @param specifier as written
 * @param kind how the file depends on the module
 * @param resolution the file or package it names, or why it names nothing certain
 */
public record ResolvedImport(String file, int line, String specifier, ImportKind kind, Resolution resolution) {
}
