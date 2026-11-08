package io.github.stigjohnny.c4contractkit.sources;

/**
 * A regular file under the web root.
 *
 * @param path relative to the web root, with {@code /} as separator
 * @param kind what the file is, by its name
 */
public record SourceFile(String path, FileKind kind) {
}
