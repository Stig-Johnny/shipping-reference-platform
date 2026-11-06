package io.github.stigjohnny.c4contractkit.rules;

/** How the model declares an edge; {@link #UNDECLARED} for a dependency without a relationship. */
public enum EdgeMode {
    REQUIRED,
    PERMITTED,
    UNDECLARED
}
