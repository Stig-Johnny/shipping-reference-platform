package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * One reason the check fails.
 *
 * @param type the rule that is broken
 * @param identifiers what the violation is about: source and target component for {@code DIVERGENCE} and
 *     {@code ABSENCE}, the class for {@code UNMAPPED_CLASS}, the component and the class for
 *     {@code PLANNED_COMPONENT_HAS_CODE}, the component for {@code BUILT_COMPONENT_EMPTY}, and the depending and the
 *     depended-on side (a component id or an isolated class) for {@code ISOLATION_BREACH}
 * @param message one sentence for people
 * @param evidence the references behind the violation, sorted; empty where the violation is a missing reference or a
 *     class
 */
public record Violation(ViolationType type, List<String> identifiers, String message, List<Evidence> evidence) {

    /** Copies the lists, so a violation never changes after it is created. */
    public Violation {
        identifiers = List.copyOf(identifiers);
        evidence = List.copyOf(evidence);
    }
}
