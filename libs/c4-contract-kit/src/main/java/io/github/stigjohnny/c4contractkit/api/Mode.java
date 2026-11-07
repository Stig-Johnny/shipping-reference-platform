package io.github.stigjohnny.c4contractkit.api;

/** How the model declares an edge between two components. */
public enum Mode {
    /** {@code contract.mode REQUIRED}: references are allowed, and at least one must exist. */
    REQUIRED,
    /** {@code contract.mode PERMITTED}, or no {@code contract.mode}: references are allowed. */
    PERMITTED,
    /** The model declares no relationship; any reference is a divergence. */
    UNDECLARED
}
