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
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options",
            "trace");
    private static final String ADR = " (ADR 0025)";
    /** The heading in the contract's description under which the problem registry stands. */
    private static final String REGISTRY = "## Errors (RFC 9457)";
    private static final List<String> REGISTRY_HEADER = List.of("type (suffix)", "status", "title", "raised by");
    private static final Pattern RULE_CELL = Pattern.compile(":?-+:?");
    private static final Pattern TYPE_CELL = Pattern.compile("`([a-z]+(?:-[a-z]+)*)`");
    private static final Pattern STATUS_CELL = Pattern.compile("[1-5][0-9]{2}");
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
            if (root.has("webhooks")) {
                throw new DocsException(PATH + ": webhooks are not read; write each operation under paths" + ADR);
            }
            List<Operation> operations = new ArrayList<>();
            for (Map.Entry<String, YamlValue> path : root.map("paths").entries().entrySet()) {
                YMap item = path.getValue().asMap(path.getKey());
                if (item.has("$ref")) {
                    throw new DocsException(PATH + " " + path.getKey() + ": a path item's $ref is not read; write its "
                            + "operations under the path" + ADR);
                }
                for (Map.Entry<String, YamlValue> method : item.entries().entrySet()) {
                    if (!METHODS.contains(method.getKey())) {
                        continue;
                    }
                    YMap operation = method.getValue().asMap(path.getKey() + " " + method.getKey());
                    String where = PATH + " " + path.getKey() + " " + method.getKey();
                    if (operation.has("callbacks")) {
                        throw new DocsException(where + ": callbacks are not read" + ADR);
                    }
                    String id = operation.string("operationId");
                    if (operations.stream().anyMatch(o -> o.id().equals(id))) {
                        throw new DocsException(where + ": the operationId '" + id + "' names another operation too"
                                + ADR);
                    }
                    operations.add(new Operation(id, method.getKey().toUpperCase(
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
     * The problem registry: the one table in the section {@link #REGISTRY} of the contract's description, which runs to
     * the next heading of level 1 or 2. Its header and rule have the four columns, and each row gives a type as a code
     * span, a status code, a title and who raises it: any module, the HTTP edge, or modules named by the contract's
     * tags, with an optional note in parentheses. A line of the section outside the table that holds a pipe fails, so
     * that no row is left out.
     */
    private static List<ProblemType> registry(YMap root) {
        List<String> lines = root.map("info").optionalString("description").orElse("").lines().map(String::strip)
                .toList();
        int heading = lines.indexOf(REGISTRY);
        if (heading < 0) {
            throw new DocsException(PATH + ": its description has no section '" + REGISTRY + "' with the problem "
                    + "registry" + ADR);
        }
        int end = heading + 1;
        while (end < lines.size() && !lines.get(end).startsWith("# ") && !lines.get(end).startsWith("## ")) {
            end++;
        }
        List<String> section = lines.subList(heading + 1, end);
        int start = 0;
        while (start < section.size() && !section.get(start).startsWith("|")) {
            start++;
        }
        int stop = start;
        while (stop < section.size() && section.get(stop).startsWith("|")) {
            stop++;
        }
        for (int i = 0; i < section.size(); i++) {
            if ((i < start || i >= stop) && section.get(i).contains("|")) {
                throw new DocsException(PATH + ": the problem registry is one table, and this line of its section is "
                        + "not a row of it: '" + section.get(i) + "'" + ADR);
            }
        }
        List<String> table = section.subList(start, stop);
        if (table.size() < 3 || !REGISTRY_HEADER.equals(cells(table.getFirst()))
                || cells(table.get(1)).size() != REGISTRY_HEADER.size()
                || !cells(table.get(1)).stream().allMatch(cell -> RULE_CELL.matcher(cell).matches())) {
            throw new DocsException(PATH + ": the problem registry under '" + REGISTRY + "' is a table with the "
                    + "columns " + String.join(", ", REGISTRY_HEADER) + " and at least one row" + ADR);
        }
        List<String> tags = root.list("tags").stream().map(t -> t.asMap("tag").string("name")).toList();
        List<ProblemType> types = new ArrayList<>();
        for (String row : table.subList(2, table.size())) {
            List<String> cells = cells(row);
            Matcher type = TYPE_CELL.matcher(cells.isEmpty() ? "" : cells.getFirst());
            if (cells.size() != REGISTRY_HEADER.size() || !type.matches()
                    || !STATUS_CELL.matcher(cells.get(1)).matches() || cells.get(2).isBlank()
                    || cells.get(2).contains("`")) {
                throw new DocsException(PATH + ": a row of the problem registry is not | `type` | status | title | "
                        + "raised by |: '" + row + "'" + ADR);
            }
            String name = type.group(1);
            if (types.stream().anyMatch(t -> t.type().equals(name))) {
                throw new DocsException(PATH + ": `" + name + "` is registered twice in the problem registry" + ADR);
            }
            String raisedBy = cells.get(3);
            Matcher owners = OWNERS.matcher(raisedBy);
            boolean known = owners.matches();
            String who = known ? owners.group(1) : "";
            List<String> modules = who.equals("any module") || who.equals("HTTP edge") || who.isEmpty() ? List.of()
                    : List.of(who.split(", "));
            if (!known || !tags.containsAll(modules)) {
                throw new DocsException(PATH + ": `" + name + "` is raised by '" + raisedBy + "', which is none of "
                        + "any module, HTTP edge and the contract's tags " + String.join(", ", tags) + ", with an "
                        + "optional note in parentheses" + ADR);
            }
            String note = owners.group(2);
            Matcher member = MEMBER.matcher(note == null ? "" : note);
            types.add(new ProblemType(name, Integer.parseInt(cells.get(1)), cells.get(2), modules,
                    who.equals("any module"), member.matches() ? member.group(1) : ""));
        }
        return types;
    }

    /** The cells of a table row, the text between its pipes, stripped; none unless it starts and ends with a pipe. */
    private static List<String> cells(String row) {
        if (row.length() < 2 || !row.startsWith("|") || !row.endsWith("|")) {
            return List.of();
        }
        return List.of(row.substring(1, row.length() - 1).split("\\|", -1)).stream().map(String::strip).toList();
    }
}
