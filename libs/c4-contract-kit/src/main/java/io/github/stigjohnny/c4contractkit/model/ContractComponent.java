package io.github.stigjohnny.c4contractkit.model;

import java.util.List;

/**
 * A component of the checked container.
 *
 * @param id its {@code contract.id}
 * @param name its name in the model
 * @param status its {@code contract.status}
 * @param packages its package roots, sorted
 */
public record ContractComponent(String id, String name, ComponentStatus status, List<String> packages) {

    public ContractComponent {
        packages = packages.stream().sorted().toList();
    }

    /** Whether a class in {@code packageName} belongs to this component: a root equals it or is a prefix plus ".". */
    public boolean owns(String packageName) {
        return packages.stream().anyMatch(root -> ContractProperties.contains(root, packageName));
    }
}
