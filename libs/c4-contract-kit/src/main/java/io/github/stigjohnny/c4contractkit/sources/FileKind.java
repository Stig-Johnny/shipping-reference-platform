package io.github.stigjohnny.c4contractkit.sources;

/** What a file under the web root is, by its name. */
public enum FileKind {
    /** {@code .ts}, {@code .tsx} and {@code .d.ts}: scanned, and its imports are dependencies. */
    CODE,
    /**
     * {@code *.test.ts} and {@code *.test.tsx}, and every code file of a test root: scanned like code, but held to the
     * rules for tests.
     */
    TEST,
    /** {@code .css}: searched for {@code @import}, which is refused. */
    STYLE,
    /** Images and fonts that code may import by their full name: never scanned. */
    ASSET,
    /** Any other file, such as {@code .js} or {@code .json}: the restricted grammar does not cover it. */
    UNSUPPORTED
}
