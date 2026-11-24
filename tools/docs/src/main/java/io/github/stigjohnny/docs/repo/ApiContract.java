package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.BlockLines;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.MarkdownTable;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String REGISTRY = "Errors (RFC 9457)";
    private static final List<String> REGISTRY_HEADER = List.of("type (suffix)", "status", "title", "raised by");
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

    public Optional<Operation> operation(String id) {
        return operations.stream().filter(o -> o.id().equals(id)).findFirst();
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
     * The problem registry: the one table in the one section {@link #REGISTRY} of the contract's description, which
     * runs to the next heading of its level or above, read as the site's parser reads and shows it. A heading's title is
     * compared with its whitespace collapsed and in any letter case, and a section of that title inside a quote or a
     * list fails, because the site would show it. Text of the section outside the table that holds a pipe fails, so
     * that no row falls out of it; code is no table and no text. Any other table, at any depth, whose column names are
     * the registry's fails. Each row gives a type as a code span, a status code, a title and who raises it: any
     * module, the HTTP edge, or modules named by the contract's tags, with an optional note in parentheses.
     */
    private static List<ProblemType> registry(YMap root) {
        String description = root.map("info").optionalString("description").orElse("");
        List<String> lines = description.lines().map(String::strip).toList();
        if (MarkdownRenderer.nestedHeadings(description, PATH).stream().anyMatch(ApiContract::registryTitle)) {
            throw new DocsException(PATH + ": its description has a section '" + REGISTRY + "' inside a quote or a "
                    + "list, which is not read; the problem registry stands in one section at the top level" + ADR);
        }
        List<BlockLines> blocks = MarkdownRenderer.blockLines(description, PATH);
        List<Integer> headings = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).kind() == BlockLines.Kind.HEADING && registryTitle(blocks.get(i).text())) {
                headings.add(i);
            }
        }
        if (headings.size() != 1) {
            throw new DocsException(PATH + ": its description has " + headings.size() + " sections '" + REGISTRY
                    + "'; the problem registry stands in one" + ADR);
        }
        int at = headings.getFirst();
        BlockLines heading = blocks.get(at);
        int next = at + 1;
        while (next < blocks.size() && !(blocks.get(next).kind() == BlockLines.Kind.HEADING
                && blocks.get(next).level() <= heading.level())) {
            next++;
        }
        int sectionEnd = next < blocks.size() ? blocks.get(next).firstLine() : lines.size();
        List<MarkdownTable> tables = MarkdownRenderer.tables(description, PATH);
        List<MarkdownTable> inSection = tables.stream()
                .filter(t -> t.line() > heading.lastLine() && t.line() < sectionEnd).toList();
        String shape = PATH + ": the problem registry under '## " + REGISTRY + "' is a table with the columns "
                + String.join(", ", REGISTRY_HEADER) + " and at least one row";
        if (inSection.size() != 1) {
            throw new DocsException(shape + "; its section holds " + inSection.size() + " tables" + ADR);
        }
        for (BlockLines block : blocks.subList(at + 1, next)) {
            if (block.prose().contains("|")) {
                throw new DocsException(PATH + ": the problem registry is one table, and this text of its section "
                        + "holds a pipe outside it: '" + lines.get(block.firstLine()) + "'" + ADR);
            }
        }
        MarkdownTable registry = inSection.getFirst();
        for (MarkdownTable other : tables) {
            if (other.line() != registry.line() && registryColumns(other.rows().getFirst().texts())) {
                throw new DocsException(PATH + ": a table with the problem registry's columns stands outside its "
                        + "section, at line " + (other.line() + 1) + " of its description; the problem registry is "
                        + "one table" + ADR);
            }
        }
        List<MarkdownTable.Row> rows = registry.rows();
        if (rows.size() < 2 || !registryColumns(rows.getFirst().texts())) {
            throw new DocsException(shape + ADR);
        }
        List<String> tags = root.list("tags").stream().map(t -> t.asMap("tag").string("name")).toList();
        List<ProblemType> types = new ArrayList<>();
        for (MarkdownTable.Row row : rows.subList(1, rows.size())) {
            List<String> cells = row.cells().stream().map(ApiContract::collapse).toList();
            Matcher type = TYPE_CELL.matcher(cells.getFirst());
            if (cells.size() != REGISTRY_HEADER.size() || cells.stream().anyMatch(String::isEmpty) || !type.matches()
                    || !STATUS_CELL.matcher(cells.get(1)).matches() || cells.get(2).contains("`")) {
                throw new DocsException(PATH + ": a row of the problem registry is not | `type` | status | title | "
                        + "raised by |: '" + lines.get(row.line()) + "'" + ADR);
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

    /** A text with every run of whitespace, a no-break space too, as one space, as the site shows it. */
    private static String collapse(String text) {
        return text.replaceAll("(?U)\\s+", " ").strip();
    }

    /**
     * Whether a table's column names, as the site shows them and in any case, are the registry's: a name written as
     * code reads as its text, and a literal backtick stays part of it.
     */
    private static boolean registryColumns(List<String> texts) {
        return REGISTRY_HEADER.equals(texts.stream().map(c -> collapse(c).toLowerCase(Locale.ROOT)).toList());
    }

    /** Whether a heading is the registry's: its text, with whitespace collapsed as the site shows it, in any case. */
    private static boolean registryTitle(String heading) {
        return collapse(heading).equalsIgnoreCase(REGISTRY);
    }
}
