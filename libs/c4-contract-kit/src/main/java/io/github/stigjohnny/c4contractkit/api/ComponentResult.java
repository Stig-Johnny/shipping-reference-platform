package io.github.stigjohnny.c4contractkit.api;

import java.util.List;

/**
 * One component of the checked container.
 *
 * @param id the component's {@code contract.id}
 * @param name the component's name in the model
 * @param status whether the component is built or planned
 * @param packages the component's package roots ({@code contract.packages}), sorted
 * @param classCount how many imported classes belong to the component; {@code package-info} does not count
 */
public record ComponentResult(String id, String name, ComponentStatus status, List<String> packages,
                              int classCount) {

    /** Copies the package list, so a result never changes after it is created. */
    public ComponentResult {
        packages = List.copyOf(packages);
    }
}
