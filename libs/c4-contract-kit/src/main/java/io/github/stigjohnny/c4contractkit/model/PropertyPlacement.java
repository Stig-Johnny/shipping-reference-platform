package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ID;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ISOLATED;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.MODE;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PACKAGES;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PREFIX;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ROOT;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.STATUS;

import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Relationship;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every {@code contract.*} property must be a known one, on the kind of element it belongs to. A misspelt
 * {@code contract.mode} would otherwise turn a REQUIRED relationship into a PERMITTED one without a word.
 *
 * <p>Without the checked container (no container, or more than one, has {@code contract.root}), only what is wrong
 * whichever container is checked is reported: an unknown property, a container property on something other than a
 * container, a component property on something other than a component, and {@code contract.mode} on a relationship
 * that does not connect two components of one container.
 */
final class PropertyPlacement {

    private static final Set<String> ON_CONTAINER = Set.of(ROOT, ISOLATED);
    private static final Set<String> ON_COMPONENT = Set.of(ID, PACKAGES, STATUS);
    private static final Set<String> ON_RELATIONSHIP = Set.of(MODE);

    private final Optional<CheckedContainer> checked;
    private final ElementNames names;
    private final Problems problems;

    PropertyPlacement(Optional<CheckedContainer> checked, ElementNames names, Problems problems) {
        this.checked = checked;
        this.names = names;
        this.problems = problems;
    }

    void check(Collection<Element> elements, Collection<Relationship> relationships) {
        for (Element element : elements) {
            check(names.of(element), element.getProperties(), allowed(element));
        }
        for (Relationship relationship : relationships) {
            boolean betweenComponents = checked.isPresent() ? checked.get().connectsTwoComponents(relationship)
                    : relationship.getSource() instanceof Component source
                            && relationship.getDestination() instanceof Component destination
                            && source.getContainer().equals(destination.getContainer());
            check(names.of(relationship), relationship.getProperties(), betweenComponents ? ON_RELATIONSHIP : Set.of());
        }
    }

    private Set<String> allowed(Element element) {
        if (checked.isPresent()) {
            CheckedContainer container = checked.get();
            return element.equals(container.container()) ? ON_CONTAINER
                    : container.isComponent(element) ? ON_COMPONENT : Set.of();
        }
        return element instanceof Container ? ON_CONTAINER : element instanceof Component ? ON_COMPONENT : Set.of();
    }

    private void check(String name, Map<String, String> properties, Set<String> allowed) {
        for (String key : properties.keySet()) {
            if (key.startsWith(PREFIX) && !allowed.contains(key)) {
                problems.add(name, misplaced(key));
            }
        }
    }

    private String misplaced(String key) {
        String container = checked.map(CheckedContainer::name).orElse("the container with contract.root");
        return switch (key) {
            case ROOT -> "contract.root is only allowed on a container";
            case ISOLATED -> "contract.isolated is only allowed on the container with contract.root"
                    + checked.map(found -> " (" + found.name() + ")").orElse("");
            case ID, PACKAGES, STATUS -> key + " is only allowed on components of " + container;
            case MODE -> "contract.mode is only allowed on relationships between two components of " + container;
            default -> key + " is not a contract property";
        };
    }
}
