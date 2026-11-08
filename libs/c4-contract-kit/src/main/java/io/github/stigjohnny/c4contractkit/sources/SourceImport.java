package io.github.stigjohnny.c4contractkit.sources;

/**
 * One import as written in a source file.
 *
 * @param line the line of the module specifier, from 1
 * @param specifier the module specifier exactly as written between the quotes
 * @param kind how the file depends on the module
 */
public record SourceImport(int line, String specifier, ImportKind kind) {
}
