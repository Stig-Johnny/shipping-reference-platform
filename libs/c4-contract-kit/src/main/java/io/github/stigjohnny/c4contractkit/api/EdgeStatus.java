package io.github.stigjohnny.c4contractkit.api;

/** What the comparison found for an edge, in the terms of reflexion models. */
public enum EdgeStatus {
    /** Declared, and the code references the target. */
    CONVERGENCE,
    /** Not declared, but the code references the target. A violation. */
    DIVERGENCE,
    /** Declared REQUIRED, but the code has no reference to the target. A violation. */
    ABSENCE,
    /** Declared PERMITTED, and the code has no static reference. Reported, never a violation; it does not mean unused. */
    NO_STATIC_EVIDENCE,
    /** Declared, but at least one endpoint is planned, so the edge is not evaluated yet. */
    NOT_EVALUATED
}
