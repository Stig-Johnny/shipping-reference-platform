package io.github.stigjohnny.docs.repo;

import java.util.List;

/**
 * One row of the contract's problem registry, the table under "Errors (RFC 9457)" in its description (ADR 0025).
 *
 * @param type the type's suffix under the problem base URI, such as {@code quote-not-found}
 * @param modules the modules that raise it, by the contract's tags; empty when any module or the HTTP edge does
 * @param anyModule whether any module may raise it
 * @param member the extension member that its note names, as in "has `reason`", or empty
 */
public record ProblemType(String type, int status, String title, List<String> modules, boolean anyModule,
        String member) {

    public ProblemType {
        modules = List.copyOf(modules);
    }
}
