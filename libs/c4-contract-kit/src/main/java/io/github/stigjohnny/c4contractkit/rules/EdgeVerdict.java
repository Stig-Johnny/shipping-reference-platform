package io.github.stigjohnny.c4contractkit.rules;

/** What the comparison found for an edge. */
public enum EdgeVerdict {
    CONVERGENCE,
    DIVERGENCE,
    ABSENCE,
    NO_STATIC_EVIDENCE,
    NOT_EVALUATED
}
