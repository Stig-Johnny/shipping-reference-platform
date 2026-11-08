package io.github.stigjohnny.c4contractkit.api;

/** The rules whose violation fails the web import check. */
public enum WebViolationType {
    /** A file of component A imports a file of component B, and the model declares no relationship A -> B. */
    DIVERGENCE,
    /** The model declares A -> B as REQUIRED, both are built, and no file of A imports a file of B. */
    ABSENCE,
    /** A file under {@code web.root} lies in no component's {@code web.paths}. */
    UNOWNED_FILE,
    /** A planned component's {@code web.paths} contain a file. */
    PLANNED_COMPONENT_HAS_CODE,
    /** A built component's {@code web.paths} contain no {@code .ts} or {@code .tsx} file. */
    BUILT_COMPONENT_EMPTY,
    /** A file of another component imports a file of a component with {@code web.entry} other than that entry. */
    ENTRY_POINT_BYPASSED,
    /** A built component's {@code web.entry} does not exist. */
    ENTRY_POINT_MISSING,
    /** A relative specifier matches no file, more than one, or leaves {@code web.root}. */
    UNRESOLVED_IMPORT,
    /** A bare specifier names a package that the importing component's {@code web.packages} does not list. */
    UNDECLARED_PACKAGE,
    /** Code files import each other in a cycle, or a file imports itself. */
    IMPORT_CYCLE,
    /** A construct or specifier outside the restricted import grammar, such as {@code require} or a URL. */
    UNSUPPORTED_SYNTAX,
    /** A file type outside the restricted import grammar, such as {@code .js} or {@code .json}. */
    UNSUPPORTED_FILE,
    /** A production file imports a test file, whose imports are held to other rules and are never evidence. */
    TEST_FILE_IMPORTED,
    /**
     * Browser code imports a built-in module of Node.js, or a test imports one without the {@code node:} prefix or one
     * that {@code web.testBuiltins} does not list.
     */
    NODE_BUILTIN,
    /**
     * A declared package or a listed built-in has no approving review: {@code package.json} names no exact version,
     * or {@code security/allowlist.json} approves no entry for the app at that version and in that scope, or the
     * allowlist cannot be read.
     */
    UNAPPROVED_IMPORT,
    /**
     * A setting of the app's configuration would change what a specifier names or add an import, or a file of it
     * cannot be read with certainty.
     */
    UNSUPPORTED_CONFIGURATION
}
