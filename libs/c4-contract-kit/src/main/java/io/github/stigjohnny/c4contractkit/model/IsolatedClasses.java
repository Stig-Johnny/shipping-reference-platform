package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ISOLATED;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.SourceVersion;

/** {@code contract.isolated}: fully qualified class names under the root and outside every component. */
final class IsolatedClasses {

    private final CheckedContainer checked;
    private final Problems problems;
    private final Optional<String> root;

    IsolatedClasses(CheckedContainer checked, Problems problems, Optional<String> root) {
        this.checked = checked;
        this.problems = problems;
        this.root = root;
    }

    List<String> read(List<ComponentDraft> components) {
        String value = checked.container().getProperties().get(ISOLATED);
        if (value == null) {
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(checked.name(), "contract.isolated has an empty entry");
        }
        Set<String> seen = new HashSet<>();
        List<String> isolated = new ArrayList<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (!seen.add(entry)) {
                problems.add(checked.name(), "contract.isolated lists '" + entry + "' more than once");
            } else if (!SourceVersion.isName(entry)) {
                problems.add(checked.name(), "contract.isolated entry '" + entry
                        + "' is not a fully qualified class name");
            } else if (root.isPresent() && !entry.startsWith(root.get() + ".")) {
                problems.add(checked.name(), "contract.isolated entry '" + entry + "' is not under contract.root '"
                        + root.get() + "'");
            } else {
                checkOutsideComponents(entry, components);
                isolated.add(entry);
            }
        }
        return isolated;
    }

    private void checkOutsideComponents(String entry, List<ComponentDraft> components) {
        for (ComponentDraft component : components) {
            if (component.packages().stream().anyMatch(packageRoot -> entry.startsWith(packageRoot + "."))) {
                problems.add(checked.name(), "contract.isolated entry '" + entry + "' is inside the packages of "
                        + component.name());
            }
        }
    }
}
