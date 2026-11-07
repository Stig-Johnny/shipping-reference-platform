package io.github.stigjohnny.c4contractkit.rules;

/** The rules of the web import check; see {@code api.WebViolationType} for each. */
public enum WebFindingType {
    DIVERGENCE,
    ABSENCE,
    UNOWNED_FILE,
    PLANNED_COMPONENT_HAS_CODE,
    BUILT_COMPONENT_EMPTY,
    ENTRY_POINT_BYPASSED,
    ENTRY_POINT_MISSING,
    UNRESOLVED_IMPORT,
    UNDECLARED_PACKAGE,
    IMPORT_CYCLE,
    UNSUPPORTED_SYNTAX,
    UNSUPPORTED_FILE
}
