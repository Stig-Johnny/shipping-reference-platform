package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * One reason the web import check fails.
 *
 * @param type the rule that is broken
 * @param identifiers what the violation is about: source and target component for {@code DIVERGENCE},
 *     {@code ABSENCE} and {@code ENTRY_POINT_BYPASSED}; the component and the file for
 *     {@code PLANNED_COMPONENT_HAS_CODE} and {@code ENTRY_POINT_MISSING}; the component for
 *     {@code BUILT_COMPONENT_EMPTY}; the component (for a test root, the root) and the package for
 *     {@code UNDECLARED_PACKAGE}; the component, root or file and the package or built-in for
 *     {@code UNAPPROVED_IMPORT}; the importing file and the test for {@code TEST_FILE_IMPORTED}; the file and the
 *     built-in for {@code NODE_BUILTIN}; the file and the specifier for {@code UNRESOLVED_IMPORT}; the files of the
 *     cycle for {@code IMPORT_CYCLE}; and the file for {@code UNOWNED_FILE}, {@code UNSUPPORTED_SYNTAX},
 *     {@code UNSUPPORTED_FILE} and {@code UNSUPPORTED_CONFIGURATION}. Files are relative to the repository root.
 * @param message one sentence for people
 * @param evidence the imports behind the violation, sorted; empty where the violation is a missing import or a file
 */
public record WebViolation(WebViolationType type, List<String> identifiers, String message,
                           List<ImportEvidence> evidence) {

    /** Copies the lists, so a violation never changes after it is created. */
    public WebViolation {
        identifiers = List.copyOf(identifiers);
        evidence = List.copyOf(evidence);
    }
}
