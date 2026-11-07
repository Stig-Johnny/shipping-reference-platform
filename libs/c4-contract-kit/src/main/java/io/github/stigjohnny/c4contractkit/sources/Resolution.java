package io.github.stigjohnny.c4contractkit.sources;

/** What a module specifier names: a file of the web root, a package, or nothing that can be known for certain. */
public sealed interface Resolution {

    /** A relative specifier that matches exactly one file of the web root. */
    record ToFile(String path) implements Resolution {
    }

    /** A bare specifier: the package it names, without a subpath. */
    record ToPackage(String name) implements Resolution {
    }

    /** A relative specifier that matches no file, more than one, or leaves the web root. */
    record Unresolved(String reason) implements Resolution {
    }

    /** A specifier outside the restricted grammar, such as an absolute path, a URL or a query suffix. */
    record Unsupported(String reason) implements Resolution {
    }
}
