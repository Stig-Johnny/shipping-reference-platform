package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code contracts/shipping.openapi.yaml}, read as data for the API pages and the brains, with its problem registry
 * read strictly (ADR 0025).
 */
public final class ApiContract {

    public static final String PATH = "contracts/shipping.openapi.yaml";
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");
    private static final String ADR = " (ADR 0025)";
    /** The heading in the contract's description under which the problem registry stands. */
    private static final String REGISTRY = "## Errors (RFC 9457)";
    private static final List<String> REGISTRY_HEADER = List.of("type (suffix)", "status", "title", "raised by");
    private static final Pattern REGISTRY_RULE = Pattern.compile("\\|(?: *:?-+:? *\\|)+");
    private static final Pattern REGISTRY_ROW = Pattern.compile(
            "\\| `([a-z]+(?:-[a-z]+)*)` *\\| ([1-5][0-9]{2}) *\\| ([^|`]*[^|` ]) *\\| ([^|]*[^| ]) *\\|");
    private static final Pattern OWNERS = Pattern.compile(
            "(any module|HTTP edge|[a-z]+(?:, [a-z]+)*)(?: \\(([^()]+)\\))?");
    private static final Pattern MEMBER = Pattern.compile("has `([A-Za-z]+)`");
    /** One problem type at the start of a description, its note, and the separator after it. */
    private static final Pattern NAMED_TYPE = Pattern.compile(
            "\\G`([a-z]+(?:-[a-z]+)*)`(?: \\([^()]*\\))?(, or |, | or )?");

    /** One operation, in the contract's order. */
    public record Operation(String id, String method, String path, List<String> tags, String summary,
            String description, YMap definition) {

        public Operation {
            tags = List.copyOf(tags);
        }
    }

    private final YMap root;
    private final String text;
    private final List<Operation> operations;
    private final List<ProblemType> problemTypes;

    private ApiContract(YMap root, String text, List<Operation> operations, List<ProblemType> problemTypes) {
        this.root = root;
        this.text = text;
        this.operations = List.copyOf(operations);
        this.problemTypes = List.copyOf(problemTypes);
    }

    public static ApiContract parse(String text) {
        try {
            YMap root = SafeYaml.yaml(text, PATH).asMap(PATH);
            List<Operation> operations = new ArrayList<>();
            for (Map.Entry<String, YamlValue> path : root.map("paths").entries().entrySet()) {
                for (Map.Entry<String, YamlValue> method : path.getValue().asMap(path.getKey()).entries().entrySet()) {
                    if (!METHODS.contains(method.getKey())) {
                        continue;
                    }
                    YMap operation = method.getValue().asMap(path.getKey() + " " + method.getKey());
                    operations.add(new Operation(operation.string("operationId"), method.getKey().toUpperCase(
                            java.util.Locale.ROOT), path.getKey(), operation.strings("tags"),
                            operation.optionalString("summary").orElse(""),
                            operation.optionalString("description").orElse(""), operation));
                }
            }
            return new ApiContract(root, text, operations, registry(root));
        } catch (YamlInputException e) {
            throw new DocsException(e.getMessage(), e);
        }
    }

    public YMap root() {
        return root;
    }

    /** The contract exactly as committed, for the download. */
    public String text() {
        return text;
    }

    public List<Operation> operations() {
        return operations;
    }

    public List<Operation> taggedWith(String tag) {
        return operations.stream().filter(o -> o.tags().contains(tag)).toList();
    }

    public String title() {
        return root.map("info").string("title");
    }

    public String version() {
        return root.map("info").string("version");
    }

    /** The first server URL, which every path is relative to (for example {@code /api/v1}). */
    public String serverUrl() {
        return root.find("servers").map(s -> s.asList("servers")).filter(l -> !l.isEmpty())
                .map(l -> l.getFirst().asMap("server").string("url")).orElse("");
    }

    /** Resolves a local {@code $ref} such as {@code #/components/schemas/Quote}. */
    public YMap resolve(String ref) {
        if (!ref.startsWith("#/")) {
            throw new DocsException(PATH + ": only local references are supported, not '" + ref + "'");
        }
        YamlValue current = root;
        for (String part : ref.substring(2).split("/", -1)) {
            current = current.asMap(ref).get(part.replace("~1", "/").replace("~0", "~"));
        }
        return current.asMap(ref);
    }

    public Optional<String> ref(YMap value) {
        return value.optionalString("$ref");
    }

    /** The problem registry, in its order. */
    public List<ProblemType> problemTypes() {
        return problemTypes;
    }

    public Optional<ProblemType> problemType(String type) {
        return problemTypes.stream().filter(p -> p.type().equals(type)).findFirst();
    }

    /**
     * The problem types that a description names first: code spans of types, each with an optional note in
     * parentheses, joined by commas and "or", and ended by a colon, a full stop or the end. Code spans after them are
     * prose. None when the description starts otherwise.
     */
    public static List<String> problemTypesNamed(String description) {
        String text = description.strip().replaceAll("\\s+", " ");
        Matcher item = NAMED_TYPE.matcher(text);
        List<String> types = new ArrayList<>();
        while (item.find()) {
            types.add(item.group(1));
            if (item.group(2) == null) {
                boolean ends = item.end() == text.length() || ":.".indexOf(text.charAt(item.end())) >= 0;
                return ends ? List.copyOf(types) : List.of();
            }
        }
        return List.of();
    }

    /**
     * The problem registry: the table under {@link #REGISTRY} in the contract's description, whose rows give each
     * type, its status, its title and who raises it: any module, the HTTP edge, or modules named by the contract's
     * tags, with an optional note in parentheses.
     */
    private static List<ProblemType> registry(YMap root) {
        List<String> lines = root.map("info").optionalString("description").orElse("").lines().map(String::strip)
                .toList();
        int heading = lines.indexOf(REGISTRY);
        if (heading < 0) {
            throw new DocsException(PATH + ": its description has no section '" + REGISTRY + "' with the problem "
                    + "registry" + ADR);
        }
        List<String> table = lines.subList(heading + 1, lines.size()).stream().dropWhile(l -> !l.startsWith("|"))
                .takeWhile(l -> l.startsWith("|")).toList();
        if (table.size() < 3 || !REGISTRY_HEADER.equals(cells(table.getFirst()))
                || !REGISTRY_RULE.matcher(table.get(1)).matches()) {
            throw new DocsException(PATH + ": the problem registry under '" + REGISTRY + "' is a table with the "
                    + "columns " + String.join(", ", REGISTRY_HEADER) + " and at least one row" + ADR);
        }
        List<String> tags = root.list("tags").stream().map(t -> t.asMap("tag").string("name")).toList();
        List<ProblemType> types = new ArrayList<>();
        for (String row : table.subList(2, table.size())) {
            Matcher cells = REGISTRY_ROW.matcher(row);
            if (!cells.matches()) {
                throw new DocsException(PATH + ": a row of the problem registry is not | `type` | status | title | "
                        + "raised by |: '" + row + "'" + ADR);
            }
            String type = cells.group(1);
            if (types.stream().anyMatch(t -> t.type().equals(type))) {
                throw new DocsException(PATH + ": `" + type + "` is registered twice in the problem registry" + ADR);
            }
            String raisedBy = cells.group(4);
            Matcher owners = OWNERS.matcher(raisedBy);
            boolean known = owners.matches();
            String who = known ? owners.group(1) : "";
            List<String> modules = who.equals("any module") || who.equals("HTTP edge") || who.isEmpty() ? List.of()
                    : List.of(who.split(", "));
            if (!known || !tags.containsAll(modules)) {
                throw new DocsException(PATH + ": `" + type + "` is raised by '" + raisedBy + "', which is none of "
                        + "any module, HTTP edge and the contract's tags " + String.join(", ", tags) + ", with an "
                        + "optional note in parentheses" + ADR);
            }
            String note = owners.group(2);
            Matcher member = MEMBER.matcher(note == null ? "" : note);
            types.add(new ProblemType(type, Integer.parseInt(cells.group(2)), cells.group(3), modules,
                    who.equals("any module"), member.matches() ? member.group(1) : ""));
        }
        return types;
    }

    /** The cells of a table row, stripped. */
    private static List<String> cells(String row) {
        List<String> cells = List.of(row.split("\\|", -1));
        return cells.subList(1, Math.max(1, cells.size() - 1)).stream().map(String::strip).toList();
    }
}
