package io.github.stigjohnny.c4contractkit.sources;

/** What a directory read as a web root holds, which decides what each of its files is. */
public enum RootKind {
    /** The web app's sources: code, tests named {@code *.test.ts} or {@code *.test.tsx}, style sheets and assets. */
    SOURCE,
    /** A directory of tests only: every {@code .ts} and {@code .tsx} file in it is a test. */
    TEST,
    /** Files served as they are: style sheets, images and fonts only. */
    PUBLIC
}
