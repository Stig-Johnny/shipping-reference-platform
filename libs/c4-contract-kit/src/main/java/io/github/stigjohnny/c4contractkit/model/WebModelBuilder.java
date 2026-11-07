package io.github.stigjohnny.c4contractkit.model;

import com.structurizr.model.Component;
import com.structurizr.model.Container;
import com.structurizr.model.Element;
import com.structurizr.model.Model;
import com.structurizr.model.Relationship;
import com.structurizr.model.StaticStructureElementInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Validates the web vocabulary of a parsed workspace and builds the {@link WebModel}; it adds its problems to those
 * the parser found and fails once, listing them all.
 *
 * <ul>
 *   <li>Exactly one container has {@code web.root}, a relative path of plain directory names. It may name test roots
 *       ({@code web.testRoots}) and public roots ({@code web.publicRoots}): relative paths of plain directory names
 *       inside the app's directory, the directory above {@code web.root}, none equal to or inside another or the web
 *       root; the Node.js built-ins tests may import ({@code web.testBuiltins}, each written with {@code node:}); and
 *       the npm packages tests may import ({@code web.testPackages}).</li>
 *   <li>Each of its components has {@code web.id} ({@code [a-z][a-z0-9-]*}, unique), {@code web.paths} (directories
 *       relative to {@code web.root}, none equal to or inside another, in any component) and {@code web.status}
 *       ({@code built} or {@code planned}, with the tag {@code Planned} exactly when planned); optionally
 *       {@code web.entry}, a {@code .ts} or {@code .tsx} file inside its paths that is not a test, and
 *       {@code web.packages}, distinct npm package names.</li>
 *   <li>Relationships between two of its components may have {@code web.mode} ({@code REQUIRED} or
 *       {@code PERMITTED}, absent meaning {@code PERMITTED}), with the tag {@code Required import} exactly when
 *       {@code REQUIRED}; at most one per ordered pair, and no cycle.</li>
 *   <li>Any other {@code web.*} property, or one on the wrong element, is a problem, and so is a {@code contract.*}
 *       property on the web container, its components or their relationships. Implied relationships are rejected.</li>
 * </ul>
 */
final class WebModelBuilder {

    static final String PREFIX = "web.";
    static final String ROOT = "web.root";
    static final String ID = "web.id";
    static final String PATHS = "web.paths";
    static final String STATUS = "web.status";
    static final String ENTRY = "web.entry";
    static final String PACKAGES = "web.packages";
    static final String MODE = "web.mode";
    static final String TEST_ROOTS = "web.testRoots";
    static final String PUBLIC_ROOTS = "web.publicRoots";
    static final String TEST_BUILTINS = "web.testBuiltins";
    static final String TEST_PACKAGES = "web.testPackages";
    static final String REQUIRED_IMPORT_TAG = "Required import";

    private static final Set<String> ON_CONTAINER = Set.of(ROOT, TEST_ROOTS, PUBLIC_ROOTS, TEST_BUILTINS,
            TEST_PACKAGES);
    private static final Set<String> ON_COMPONENT = Set.of(ID, PATHS, STATUS, ENTRY, PACKAGES);
    private static final Set<String> ON_RELATIONSHIP = Set.of(MODE);
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9._-]*");
    private static final Pattern PACKAGE_PART = Pattern.compile("[a-z0-9-][a-z0-9._-]*");
    private static final Pattern BUILTIN = Pattern.compile("node:[a-z][a-z0-9_]*(?:/[a-z][a-z0-9_]*)*");

    private final Model model;
    private final ElementNames names;
    private final Problems problems;

    WebModelBuilder(ParsedModel parsed, Problems problems) {
        this.model = parsed.workspace().getModel();
        this.names = new ElementNames(parsed.identifiers());
        this.problems = problems;
    }

    WebModel build() throws InvalidModelException {
        List<Relationship> relationships = List.copyOf(model.getRelationships());
        rejectImpliedRelationships(relationships);
        Optional<CheckedContainer> found = webContainer();
        checkPlacement(found, relationships);
        CheckedContainer web = found.orElseThrow(problems::failure);
        String root = Objects.requireNonNullElse(web.container().getProperties().get(ROOT), "");
        if (!isRelativePath(root)) {
            problems.add(web.name(), "web.root '" + root + "' is not a relative path of plain directory names");
        }
        Map<String, String> container = web.container().getProperties();
        List<String> testRoots = roots(web.name(), TEST_ROOTS, container.get(TEST_ROOTS));
        List<String> publicRoots = roots(web.name(), PUBLIC_ROOTS, container.get(PUBLIC_ROOTS));
        checkRoots(web.name(), root, testRoots, publicRoots);
        List<String> testBuiltins = builtins(web.name(), container.get(TEST_BUILTINS));
        List<String> testPackages = packages(web.name(), TEST_PACKAGES, container.get(TEST_PACKAGES));
        List<Draft> drafts = web.container().getComponents().stream().map(this::draft).toList();
        checkUniqueIds(drafts);
        checkNoOverlap(drafts);
        List<WebRelationship> edges = relationships(web, relationships, drafts);
        problems.throwIfAny();
        return new WebModel(root, web.name(), drafts.stream().map(Draft::toComponent).toList(), edges, testRoots,
                publicRoots, testBuiltins, testPackages);
    }

    /** Directories of a root list: relative paths of plain names. Every other entry is a problem. */
    private List<String> roots(String name, String property, @Nullable String value) {
        if (value == null) {
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(name, property + " has an empty entry");
        }
        Set<String> valid = new TreeSet<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (isRelativePath(entry)) {
                valid.add(entry);
            } else {
                problems.add(name, property + " entry '" + entry + "' is not a relative path of plain directory names");
            }
        }
        return List.copyOf(valid);
    }

    /**
     * Test and public roots lie inside the app's directory, the one above {@code web.root}, and no root equals or lies
     * inside another, the web root included.
     */
    private void checkRoots(String name, String root, List<String> testRoots, List<String> publicRoots) {
        int slash = root.lastIndexOf('/');
        String app = slash < 0 ? "" : root.substring(0, slash);
        List<String> all = new ArrayList<>();
        all.add(root);
        all.addAll(testRoots);
        all.addAll(publicRoots);
        for (String other : all.subList(1, all.size())) {
            if (!app.isEmpty() && !other.startsWith(app + "/")) {
                problems.add(name, "'" + other + "' is not inside the app's directory '" + app + "', the directory "
                        + "above web.root");
            }
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                String first = all.get(i);
                String second = all.get(j);
                if (first.equals(second) || first.startsWith(second + "/") || second.startsWith(first + "/")) {
                    problems.add(name, "the roots '" + first + "' and '" + second + "' overlap");
                }
            }
        }
    }

    /** Built-ins of Node.js that tests may import: each written with node:, distinct. */
    private List<String> builtins(String name, @Nullable String value) {
        if (value == null) {
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(name, TEST_BUILTINS + " has an empty entry");
        }
        Set<String> seen = new TreeSet<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (!BUILTIN.matcher(entry).matches()) {
                problems.add(name, TEST_BUILTINS + " entry '" + entry + "' is not a built-in module of Node.js "
                        + "written with node:, such as node:test");
            } else if (!seen.add(entry)) {
                problems.add(name, TEST_BUILTINS + " lists '" + entry + "' more than once");
            }
        }
        return List.copyOf(seen);
    }

    /** As in the contract check: the model must say {@code !impliedRelationships false} and never switch them on. */
    private void rejectImpliedRelationships(List<Relationship> relationships) {
        for (Relationship relationship : relationships) {
            String linked = relationship.getLinkedRelationshipId();
            if (linked != null && !(relationship.getSource() instanceof StaticStructureElementInstance)) {
                problems.add(names.of(relationship), "implied by " + names.of(model.getRelationship(linked))
                        + "; implied relationships are disabled, so the model must say !impliedRelationships false"
                        + " and never switch them on");
            }
        }
    }

    private Optional<CheckedContainer> webContainer() {
        List<Container> containers = model.getElements().stream()
                .filter(Container.class::isInstance)
                .map(Container.class::cast)
                .filter(container -> container.getProperties().containsKey(ROOT))
                .toList();
        if (containers.isEmpty()) {
            problems.add("model", "no container has web.root; exactly one must");
            return Optional.empty();
        }
        if (containers.size() > 1) {
            problems.add(containers.stream().map(names::of).sorted().collect(Collectors.joining(", ")),
                    "more than one container has web.root; exactly one may");
            return Optional.empty();
        }
        Container container = containers.getFirst();
        return Optional.of(new CheckedContainer(container, names.of(container)));
    }

    /**
     * Every {@code web.*} property is a known one, on the kind of element it belongs to; without the web container,
     * only what is wrong whichever container it is. The web container, its components and their relationships carry
     * no {@code contract.*} property.
     */
    private void checkPlacement(Optional<CheckedContainer> found, Collection<Relationship> relationships) {
        String container = found.map(CheckedContainer::name).orElse("the container with web.root");
        for (Element element : model.getElements()) {
            Set<String> allowed;
            boolean web = false;
            if (found.isPresent()) {
                CheckedContainer checked = found.get();
                web = element.equals(checked.container()) || checked.isComponent(element);
                allowed = element.equals(checked.container()) ? ON_CONTAINER
                        : checked.isComponent(element) ? ON_COMPONENT : Set.of();
            } else {
                allowed = element instanceof Container ? ON_CONTAINER
                        : element instanceof Component ? ON_COMPONENT : Set.of();
            }
            check(names.of(element), element.getProperties(), allowed, web, container);
        }
        for (Relationship relationship : relationships) {
            boolean between = found.isPresent() ? found.get().connectsTwoComponents(relationship)
                    : relationship.getSource() instanceof Component source
                            && relationship.getDestination() instanceof Component destination
                            && source.getContainer().equals(destination.getContainer());
            check(names.of(relationship), relationship.getProperties(), between ? ON_RELATIONSHIP : Set.of(),
                    between && found.isPresent(), container);
        }
    }

    private void check(String name, Map<String, String> properties, Set<String> allowed, boolean web,
                       String container) {
        for (String key : properties.keySet()) {
            if (key.startsWith(PREFIX) && !allowed.contains(key)) {
                problems.add(name, misplaced(key, container));
            }
            if (web && key.startsWith(ContractProperties.PREFIX)) {
                problems.add(name, key + " is a property of the API's checked container, not of the web container "
                        + "or its components, which use web.* properties");
            }
        }
    }

    private static String misplaced(String key, String container) {
        return switch (key) {
            case ROOT, TEST_ROOTS, PUBLIC_ROOTS, TEST_BUILTINS, TEST_PACKAGES ->
                    key + " is only allowed on a container";
            case ID, PATHS, STATUS, ENTRY, PACKAGES -> key + " is only allowed on components of " + container;
            case MODE -> "web.mode is only allowed on relationships between two components of " + container;
            default -> key + " is not a web property";
        };
    }

    private Draft draft(Component component) {
        String name = names.of(component);
        Map<String, String> properties = component.getProperties();
        List<String> paths = paths(name, properties.get(PATHS));
        return new Draft(name, component.getName(), id(name, properties.get(ID)),
                status(name, component, properties.get(STATUS)), paths, entry(name, properties.get(ENTRY), paths),
                packages(name, PACKAGES, properties.get(PACKAGES)));
    }

    private Optional<String> id(String name, @Nullable String value) {
        if (value == null) {
            problems.add(name, "web.id is missing");
            return Optional.empty();
        }
        if (!ID_PATTERN.matcher(value).matches()) {
            problems.add(name, "web.id '" + value + "' does not match " + ID_PATTERN.pattern());
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private Optional<ComponentStatus> status(String name, Component component, @Nullable String value) {
        if (value == null) {
            problems.add(name, "web.status is missing");
            return Optional.empty();
        }
        Optional<ComponentStatus> status = switch (value) {
            case "built" -> Optional.of(ComponentStatus.BUILT);
            case "planned" -> Optional.of(ComponentStatus.PLANNED);
            default -> Optional.empty();
        };
        if (status.isEmpty()) {
            problems.add(name, "web.status '" + value + "' is neither built nor planned");
        }
        boolean tagged = component.hasTag(ContractProperties.PLANNED_TAG);
        if (status.equals(Optional.of(ComponentStatus.PLANNED)) && !tagged) {
            problems.add(name, "web.status is planned, but the tag Planned is missing");
        }
        if (status.equals(Optional.of(ComponentStatus.BUILT)) && tagged) {
            problems.add(name, "the tag Planned is present, but web.status is built");
        }
        return status;
    }

    /** The directories that can be used: valid relative paths. Every other entry is a problem. */
    private List<String> paths(String name, @Nullable String value) {
        if (value == null) {
            problems.add(name, "web.paths is missing");
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(name, "web.paths has an empty entry");
        }
        List<String> valid = new ArrayList<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (isRelativePath(entry)) {
                valid.add(entry);
            } else {
                problems.add(name, "web.paths entry '" + entry + "' is not a relative path of plain directory names");
            }
        }
        return valid;
    }

    private Optional<String> entry(String name, @Nullable String value, List<String> paths) {
        if (value == null) {
            return Optional.empty();
        }
        String problem;
        if (!isRelativePath(value)) {
            problem = "is not a relative path of plain names";
        } else if (paths.stream().noneMatch(path -> value.startsWith(path + "/"))) {
            problem = "is not a file in the component's web.paths";
        } else if (!value.endsWith(".ts") && !value.endsWith(".tsx")) {
            problem = "is not a .ts or .tsx file";
        } else if (value.endsWith(".test.ts") || value.endsWith(".test.tsx")) {
            problem = "is a test file, which no production file may import";
        } else {
            return Optional.of(value);
        }
        problems.add(name, "web.entry '" + value + "' " + problem);
        return Optional.empty();
    }

    private List<String> packages(String name, String property, @Nullable String value) {
        if (value == null) {
            return List.of();
        }
        List<String> entries = ContractProperties.entries(value);
        if (entries.contains("")) {
            problems.add(name, property + " has an empty entry");
        }
        Set<String> seen = new TreeSet<>();
        for (String entry : entries.stream().filter(entry -> !entry.isEmpty()).toList()) {
            if (!isPackageName(entry)) {
                problems.add(name, property + " entry '" + entry + "' is not an npm package name");
            } else if (!seen.add(entry)) {
                problems.add(name, property + " lists '" + entry + "' more than once");
            }
        }
        return List.copyOf(seen);
    }

    private void checkUniqueIds(List<Draft> drafts) {
        Map<String, List<String>> namesById = new TreeMap<>();
        for (Draft draft : drafts) {
            draft.id().ifPresent(id -> namesById.computeIfAbsent(id, _ -> new ArrayList<>()).add(draft.name()));
        }
        namesById.forEach((id, owners) -> {
            if (owners.size() > 1) {
                problems.add(joined(owners), "web.id '" + id + "' is used by more than one component");
            }
        });
    }

    /** No directory of one component may be, or lie inside, a directory of another or of the same component. */
    private void checkNoOverlap(List<Draft> drafts) {
        List<Directory> roots = drafts.stream()
                .flatMap(draft -> draft.paths().stream().map(path -> new Directory(path, draft.name())))
                .sorted(Comparator.comparing(Directory::path).thenComparing(Directory::owner))
                .toList();
        for (int i = 0; i < roots.size(); i++) {
            Directory first = roots.get(i);
            for (Directory second : roots.subList(i + 1, roots.size())) {
                if (second.path().equals(first.path()) || second.path().startsWith(first.path() + "/")) {
                    problems.add(joined(List.of(first.owner(), second.owner())), "web.paths entries '"
                            + first.path() + "' and '" + second.path() + "' overlap");
                }
            }
        }
    }

    private List<WebRelationship> relationships(CheckedContainer web, List<Relationship> relationships,
                                                List<Draft> drafts) {
        Map<String, Integer> perPair = new TreeMap<>();
        Map<String, Collection<String>> edges = new TreeMap<>();
        List<WebRelationship> result = new ArrayList<>();
        for (Relationship relationship : relationships) {
            String name = names.of(relationship);
            if (!web.connectsTwoComponents(relationship)) {
                if (relationship.hasTag(REQUIRED_IMPORT_TAG)) {
                    problems.add(name, "the tag Required import is only allowed on relationships between two "
                            + "components of " + web.name());
                }
                continue;
            }
            perPair.merge(name, 1, Integer::sum);
            edges.computeIfAbsent(names.of(relationship.getSource()), _ -> new TreeSet<>())
                    .add(names.of(relationship.getDestination()));
            Optional<String> source = idOf(relationship.getSource(), drafts);
            Optional<String> target = idOf(relationship.getDestination(), drafts);
            mode(name, relationship).ifPresent(mode -> source.ifPresent(sourceId -> target.ifPresent(
                    targetId -> result.add(new WebRelationship(sourceId, targetId, mode)))));
        }
        perPair.forEach((pair, count) -> {
            if (count > 1) {
                problems.add(pair, count + " relationships connect these components; at most one may");
            }
        });
        for (List<String> cycle : ComponentCycles.in(edges)) {
            problems.add(String.join(", ", cycle), "the web component relationships form a cycle");
        }
        return result;
    }

    private Optional<String> idOf(Element element, List<Draft> drafts) {
        String name = names.of(element);
        return drafts.stream().filter(draft -> draft.name().equals(name)).findFirst().flatMap(Draft::id);
    }

    private Optional<RelationshipMode> mode(String name, Relationship relationship) {
        @Nullable String value = relationship.getProperties().get(MODE);
        Optional<RelationshipMode> mode = value == null ? Optional.of(RelationshipMode.PERMITTED) : switch (value) {
            case "REQUIRED" -> Optional.of(RelationshipMode.REQUIRED);
            case "PERMITTED" -> Optional.of(RelationshipMode.PERMITTED);
            default -> Optional.empty();
        };
        if (mode.isEmpty()) {
            problems.add(name, "web.mode '" + value + "' is neither REQUIRED nor PERMITTED");
        }
        boolean tagged = relationship.hasTag(REQUIRED_IMPORT_TAG);
        if (mode.equals(Optional.of(RelationshipMode.REQUIRED)) && !tagged) {
            problems.add(name, "web.mode is REQUIRED, but the tag Required import is missing");
        }
        if (mode.equals(Optional.of(RelationshipMode.PERMITTED)) && tagged) {
            problems.add(name, "the tag Required import is present, but web.mode is PERMITTED");
        }
        return mode;
    }

    /** A path of plain names separated by {@code /}: no empty, {@code .} or {@code ..} segment, no leading dot. */
    static boolean isRelativePath(String value) {
        return !value.isEmpty() && List.of(value.split("/", -1)).stream()
                .allMatch(segment -> PLAIN_NAME.matcher(segment).matches());
    }

    /** An npm package name by the rules for new packages, optionally scoped; never with a subpath. */
    static boolean isPackageName(String value) {
        if (value.length() > 214) {
            return false;
        }
        if (value.startsWith("@")) {
            String[] parts = value.substring(1).split("/", -1);
            return parts.length == 2 && PACKAGE_PART.matcher(parts[0]).matches()
                    && PACKAGE_PART.matcher(parts[1]).matches();
        }
        return PACKAGE_PART.matcher(value).matches();
    }

    private static String joined(Collection<String> owners) {
        return owners.stream().distinct().sorted().collect(Collectors.joining(", "));
    }

    /** A component as read; id and status are empty where the model gives no valid value. */
    private record Draft(String name, String title, Optional<String> id, Optional<ComponentStatus> status,
                         List<String> paths, Optional<String> entry, List<String> packages) {

        /** Called only when the model has no problem, so id and status are present. */
        WebComponent toComponent() {
            return new WebComponent(id.orElseThrow(), title, status.orElseThrow(), paths, entry, packages);
        }
    }

    /** A directory of a component, with the component's name. */
    private record Directory(String path, String owner) {
    }
}
