package io.github.stigjohnny.c4contractkit.api;

/** A component's {@code contract.status}. */
public enum ComponentStatus {
    /** The component's packages must contain code, and its relationships are evaluated. */
    BUILT,
    /** The component's packages must contain no class, and its relationships are not evaluated yet. */
    PLANNED
}
