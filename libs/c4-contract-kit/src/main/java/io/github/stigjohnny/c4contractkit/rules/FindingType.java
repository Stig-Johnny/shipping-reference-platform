package io.github.stigjohnny.c4contractkit.rules;

/** The violation rules (libs/c4-contract-kit/AGENTS.md, "Comparison semantics"). */
public enum FindingType {
    DIVERGENCE,
    ABSENCE,
    UNMAPPED_CLASS,
    ISOLATION_BREACH,
    PLANNED_COMPONENT_HAS_CODE,
    BUILT_COMPONENT_EMPTY
}
