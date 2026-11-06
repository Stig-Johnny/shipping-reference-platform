package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ID;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ISOLATED;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.MODE;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PACKAGES;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PREFIX;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ROOT;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.STATUS;

import com.structurizr.model.Element;
import com.structurizr.model.Relationship;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Every {@code contract.*} property must be a known one, on the kind of element it belongs to. A misspelt
 * {@code contract.mode} would otherwise turn a REQUIRED relationship into a PERMITTED one without a word.
 */
final class PropertyPlacement {

    private static final Set<String> ON_CONTAINER = Set.of(ROOT, ISOLATED);
    private static final Set<String> ON_COMPONENT = Set.of(ID, PACKAGES, STATUS);
    private static final Set<String> ON_RELATIONSHIP = Set.of(MODE);

    private final CheckedContainer checked;
    private final ElementNames names;
    private final Problems problems;

    PropertyPlacement(CheckedContainer checked, ElementNames names, Problems problems) {
        this.checked = checked;
        this.names = names;
        this.problems = problems;
    }

    void check(Collection<Element> elements, Collection<Relationship> relationships) {
        for (Element element : elements) {
            Set<String> allowed = element.equals(checked.container()) ? ON_CONTAINER
                    : checked.isComponent(element) ? ON_COMPONENT : Set.of();
            check(names.of(element), element.getProperties(), allowed);
        }
        for (Relationship relationship : relationships) {
            Set<String> allowed = checked.connectsTwoComponents(relationship) ? ON_RELATIONSHIP : Set.of();
            check(names.of(relationship), relationship.getProperties(), allowed);
        }
    }

    private void check(String name, Map<String, String> properties, Set<String> allowed) {
        for (String key : properties.keySet()) {
            if (key.startsWith(PREFIX) && !allowed.contains(key)) {
                problems.add(name, misplaced(key));
            }
        }
    }

    private String misplaced(String key) {
        return switch (key) {
            case ROOT -> "contract.root is only allowed on a container";
            case ISOLATED -> "contract.isolated is only allowed on the container with contract.root (" + checked.name()
                    + ")";
            case ID, PACKAGES, STATUS -> key + " is only allowed on components of " + checked.name();
            case MODE -> "contract.mode is only allowed on relationships between two components of " + checked.name();
            default -> key + " is not a contract property";
        };
    }
}
