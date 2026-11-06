package io.github.stigjohnny.c4contractkit.api;

/** The rules whose violation fails the check. */
public enum ViolationType {
    /** A class of component A references a class of component B, and the model declares no relationship A -> B. */
    DIVERGENCE,
    /** The model declares A -> B as REQUIRED, both are built, and no class of A references a class of B. */
    ABSENCE,
    /** A class under {@code contract.root} is in no component's packages and not in {@code contract.isolated}. */
    UNMAPPED_CLASS,
    /** An isolated class references a component class, or a component class references an isolated class. */
    ISOLATION_BREACH,
    /** A planned component's packages contain a class. */
    PLANNED_COMPONENT_HAS_CODE,
    /** A built component's packages contain no class. */
    BUILT_COMPONENT_EMPTY
}
