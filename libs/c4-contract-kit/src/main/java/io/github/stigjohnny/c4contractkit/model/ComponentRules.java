package io.github.stigjohnny.c4contractkit.model;

import static io.github.stigjohnny.c4contractkit.model.ContractProperties.ID;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PACKAGES;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.PLANNED_TAG;
import static io.github.stigjohnny.c4contractkit.model.ContractProperties.STATUS;

import com.structurizr.model.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import org.jspecify.annotations.Nullable;

/** The rules for the components of the checked container: id, packages, status and the Planned tag. */
final class ComponentRules {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");

    private final ElementNames names;
    private final Problems problems;
    private final Optional<String> root;

    /** {@code root} is empty when contract.root itself is invalid; packages are then not checked against it. */
    ComponentRules(ElementNames names, Problems problems, Optional<String> root) {
        this.names = names;
        this.problems = problems;
        this.root = root;
    }

    List<ComponentDraft> read(Collection<Component> components) {
        List<ComponentDraft> drafts = components.stream().map(this::draft).toList();
        checkUniqueIds(drafts);
        checkNoOverlap(drafts);
        return drafts;
    }

    private ComponentDraft draft(Component component) {
        String name = names.of(component);
        Map<String, String> properties = component.getProperties();
        return new ComponentDraft(component, name, id(name, properties.get(ID)),
                status(name, component, properties.get(STATUS)), packages(name, properties.get(PACKAGES)));
    }

    private Optional<String> id(String name, @Nullable String value) {
        if (value == null) {
            problems.add(name, "contract.id is missing");
            return Optional.empty();
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            problems.add(name, "contract.id '" + value + "' does not match " + ID_PATTERN.pattern());
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private Optional<ComponentStatus> status(String name, Component component, @Nullable String value) {
        if (value == null) {
            problems.add(name, "contract.status is missing");
            return Optional.empty();
        }
        Optional<ComponentStatus> status = switch (value) {
            case "built" -> Optional.of(ComponentStatus.BUILT);
            case "planned" -> Optional.of(ComponentStatus.PLANNED);
            default -> Optional.empty();
        };
        if (status.isEmpty()) {
            problems.add(name, "contract.status '" + value + "' is neither built nor planned");
        }
        boolean tagged = component.hasTag(PLANNED_TAG);
        if (status.equals(Optional.of(ComponentStatus.PLANNED)) && !tagged) {
            problems.add(name, "contract.status is planned, but the tag Planned is missing");
        }
        if (status.equals(Optional.of(ComponentStatus.BUILT)) && tagged) {
            problems.add(name, "the tag Planned is present, but contract.status is built");
        }
        return status;
    }

    /** The package roots that can be used: valid names under the root. Every other entry is a problem. */
    private List<String> packages(String name, @Nullable String value) {
        if (value == null) {
            problems.add(name, "contract.packages is missing");
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(name, "contract.packages has an empty entry");
        }
        List<String> valid = new ArrayList<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (!SourceVersion.isName(entry)) {
                problems.add(name, "contract.packages entry '" + entry + "' is not a Java package name");
            } else if (root.isPresent() && !entry.startsWith(root.get() + ".")) {
                problems.add(name, "contract.packages entry '" + entry + "' is not under contract.root '"
                        + root.get() + "'");
            } else {
                valid.add(entry);
            }
        }
        return valid;
    }

    private void checkUniqueIds(List<ComponentDraft> drafts) {
        Map<String, List<String>> namesById = new TreeMap<>();
        for (ComponentDraft draft : drafts) {
            draft.id().ifPresent(id -> namesById.computeIfAbsent(id, _ -> new ArrayList<>()).add(draft.name()));
        }
        namesById.forEach((id, owners) -> {
            if (owners.size() > 1) {
                problems.add(joined(owners), "contract.id '" + id + "' is used by more than one component");
            }
        });
    }

    private void checkNoOverlap(List<ComponentDraft> drafts) {
        List<PackageRoot> roots = drafts.stream()
                .flatMap(draft -> draft.packages().stream().map(root -> new PackageRoot(root, draft.name())))
                .sorted(Comparator.comparing(PackageRoot::root).thenComparing(PackageRoot::owner))
                .toList();
        for (int i = 0; i < roots.size(); i++) {
            PackageRoot first = roots.get(i);
            for (PackageRoot second : roots.subList(i + 1, roots.size())) {
                // Sorted, so when two roots overlap the first is the second or contains it.
                if (ContractProperties.contains(first.root(), second.root())) {
                    problems.add(joined(List.of(first.owner(), second.owner())), "contract.packages entries '"
                            + first.root() + "' and '" + second.root() + "' overlap");
                }
            }
        }
    }

    private static String joined(Collection<String> names) {
        return names.stream().distinct().sorted().collect(Collectors.joining(", "));
    }

    private record PackageRoot(String root, String owner) {
    }
}
