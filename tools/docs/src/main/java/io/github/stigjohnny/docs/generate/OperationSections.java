package io.github.stigjohnny.docs.generate;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.BlockLines;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.repo.ApiContract;
import io.github.stigjohnny.docs.repo.Capabilities;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.ProblemType;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The module documents' HTTP operation sections (ADR 0025). The generator owns, in {@code docs/modules/<tag>.md} for
 * each tag that an operation carries, the one top-level level-2 section whose heading starts with {@link #HEADING}, as
 * the site's parser finds it: its lines from the heading up to the next heading of level 1 or 2, blank lines included,
 * or up to the end. It writes a notice and one row per operation of the tag, in the contract's order: its operationId,
 * its method and path, its success responses and the problem types that the registry gives the module, with the
 * planned marks that the capabilities give, then one blank line before the next heading. Every value is written as a
 * code span that the site's parser reads back as the value. Every byte before the heading and from the next heading on
 * stays as written. A document of a module without operations has no such section.
 */
public final class OperationSections {

    /** The start of the owned section's heading text. */
    public static final String HEADING = "HTTP operations";
    private static final String MODULES = "docs/modules/";
    private static final String ADR = " (ADR 0025)";
    private static final String NOTICE = """
            Generated from `contracts/shipping.openapi.yaml` and `docs/capabilities/capabilities.yaml`. Edit those \
            sources, then
            run `./gradlew :tools:docs:generate`; do not edit this section.

            The last column lists problem types whose registry entry explicitly names this module. "none" means none \
            in that
            subset; common errors still apply. The API pages show each operation's declared responses, and the API \
            overview
            documents common errors.

            An operation or problem is marked planned when every capability naming it is planned. An unmarked entry \
            may still be
            partly built. Query parameters are shown by name without values; request headers shown here are required. \
            Response
            headers common to every declared response are omitted.
            """;

    private OperationSections() {
    }

    /** The generated section of one module, from its heading to its table's last row and line break. */
    public static String section(Sources sources, String tag) {
        ApiContract contract = sources.contract();
        Capabilities capabilities = sources.capabilities();
        Set<String> common = commonHeaders(contract);
        StringBuilder out = new StringBuilder("## " + HEADING + " (`" + ApiContract.PATH + "`, tag "
                + code(tag, "tag") + ")\n\n").append(NOTICE).append('\n')
                .append("| operationId | Method and path | Success | Module-specific problems |\n|---|---|---|---|\n");
        for (ApiContract.Operation operation : contract.taggedWith(tag)) {
            out.append("| ").append(cell(code(operation.id(), operation.id())))
                    .append(capabilities.plannedOperation(operation.id()) ? " (planned)" : "")
                    .append(" | ").append(cell(request(contract, operation)))
                    .append(" | ").append(cell(success(contract, operation, common)))
                    .append(" | ").append(cell(problems(contract, capabilities, operation, tag))).append(" |\n");
        }
        return out.toString();
    }

    /**
     * Every module document of a tag of the contract with its section composed, keyed by path; the documents are read
     * from the repository and nothing is written. A document without its section, with two, or a section in the
     * document of a module that the contract tags no operation with, fails.
     */
    public static Map<String, String> compose(Sources sources) {
        // The modules whose documents have a section: every tag of an operation, in the contract's order.
        Set<String> tags = new LinkedHashSet<>();
        sources.contract().operations().forEach(o -> tags.addAll(o.tags()));
        Map<String, String> composed = new TreeMap<>();
        for (DocFile doc : sources.docsUnder(MODULES)) {
            String module = doc.path().substring(MODULES.length()).replaceFirst("\\.md$", "");
            String text = sources.text(doc.path());
            if (text.replace("\r\n", "\n").indexOf('\r') >= 0) {
                // The parser ends a line at a lone carriage return too, so the section's lines would be miscounted.
                throw new DocsException(doc.path() + " holds a carriage return without a line feed" + ADR);
            }
            int body = bodyStart(text);
            List<BlockLines> blocks = MarkdownRenderer.blockLines(text.substring(body), doc.path());
            List<Integer> owned = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                BlockLines block = blocks.get(i);
                if (block.kind() == BlockLines.Kind.HEADING && block.level() == 2 && block.text().startsWith(HEADING)) {
                    owned.add(i);
                }
            }
            if (!tags.contains(module)) {
                if (!owned.isEmpty()) {
                    throw new DocsException(doc.path() + " has a section " + HEADING + ", but the contract tags no "
                            + "operation with " + module + "; remove the section" + ADR);
                }
                continue;
            }
            if (owned.size() != 1) {
                throw new DocsException(doc.path() + " has " + owned.size() + " sections that start with the heading "
                        + HEADING + "; the contract tags operations with " + module + ", so it has one" + ADR);
            }
            composed.put(doc.path(), text.substring(0, body) + replace(text.substring(body), blocks, owned.getFirst(),
                    section(sources, module)));
        }
        for (String tag : tags) {
            if (!composed.containsKey(MODULES + tag + ".md")) {
                throw new DocsException("the contract tags operations with " + tag + ", and " + MODULES + tag
                        + ".md does not exist" + ADR);
            }
        }
        return composed;
    }

    /** Every composed module document that differs from the repository's, with its first different line. */
    public static List<String> compare(Map<String, String> composed, Path repo) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> document : composed.entrySet()) {
            String actual = Sources.text(repo, document.getKey());
            if (!actual.equals(document.getValue())) {
                problems.add(document.getKey() + ": its section " + HEADING + " differs from what the contract and the "
                        + "capabilities generate, first at line " + firstDifferentLine(actual, document.getValue())
                        + "; run ./gradlew :tools:docs:generate" + ADR);
            }
        }
        return problems;
    }

    /** Writes each composed module document that differs from the repository's. */
    public static void write(Map<String, String> composed, Path repo) {
        try {
            for (Map.Entry<String, String> document : composed.entrySet()) {
                Path path = repo.resolve(document.getKey());
                if (!Files.readString(path, StandardCharsets.UTF_8).equals(document.getValue())) {
                    Files.writeString(path, document.getValue(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            throw new DocsException("the module documents cannot be written: " + e.getClass().getSimpleName(), e);
        }
    }

    /** The body with its owned section, from its heading to the next heading of level 1 or 2, replaced. */
    private static String replace(String body, List<BlockLines> blocks, int at, String section) {
        int next = at + 1;
        while (next < blocks.size() && !(blocks.get(next).kind() == BlockLines.Kind.HEADING
                && blocks.get(next).level() <= 2)) {
            next++;
        }
        int start = offset(body, blocks.get(at).firstLine());
        return next < blocks.size() ? body.substring(0, start) + section + "\n" + body.substring(offset(body,
                blocks.get(next).firstLine())) : body.substring(0, start) + section;
    }

    /** The character offset at which a line of a text starts, counted from 0. */
    private static int offset(String text, int line) {
        int at = 0;
        for (int i = 0; i < line; i++) {
            at = text.indexOf('\n', at) + 1;
        }
        return at;
    }

    /** Where a document's body starts: after its frontmatter between two lines of three dashes, if it has one. */
    private static int bodyStart(String text) {
        if (!text.startsWith("---\n")) {
            return 0;
        }
        int end = text.indexOf("\n---\n", 3);
        return end < 0 ? 0 : end + 5;
    }

    private static int firstDifferentLine(String actual, String expected) {
        String[] a = actual.split("\n", -1);
        String[] b = expected.split("\n", -1);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (!a[i].equals(b[i])) {
                return i + 1;
            }
        }
        return Math.min(a.length, b.length) + 1;
    }

    /**
     * The method and path, with the names of the query parameters and the required parameters of other places than the
     * path, such as a request header: {@code POST /api/v1/shipments} (header {@code Idempotency-Key}).
     */
    private static String request(ApiContract contract, ApiContract.Operation operation) {
        Map<String, YMap> parameters = new LinkedHashMap<>();
        YMap item = contract.root().map("paths").map(operation.path());
        for (YamlValue value : item.find("parameters").map(p -> p.asList("parameters")).orElse(List.of())) {
            YMap parameter = resolved(contract, value.asMap("parameter"));
            parameters.put(parameter.string("in") + " " + parameter.string("name"), parameter);
        }
        for (YamlValue value : operation.definition().find("parameters").map(p -> p.asList("parameters"))
                .orElse(List.of())) {
            YMap parameter = resolved(contract, value.asMap("parameter"));
            parameters.put(parameter.string("in") + " " + parameter.string("name"), parameter);
        }
        List<String> query = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (YMap parameter : parameters.values()) {
            String in = parameter.string("in");
            boolean required = parameter.optionalString("required").orElse("false").equals("true");
            if (in.equals("query")) {
                query.add(parameter.string("name"));
            } else if (!in.equals("path") && required) {
                others.add(in + " " + code(parameter.string("name"), operation.id()));
            }
        }
        String target = operation.method() + " " + contract.serverUrl() + operation.path()
                + (query.isEmpty() ? "" : "?" + String.join("&", query));
        return code(target, operation.id()) + (others.isEmpty() ? "" : " (" + String.join(", ", others) + ")");
    }

    /** Each success response, in the contract's order: its status, its body and the headers that set it apart. */
    private static String success(ApiContract contract, ApiContract.Operation operation, Set<String> common) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, YamlValue> entry : operation.definition().map("responses").entries().entrySet()) {
            if (!entry.getKey().startsWith("2")) {
                continue;
            }
            YMap response = resolved(contract, entry.getValue().asMap(entry.getKey()));
            List<String> bodies = new ArrayList<>();
            for (Map.Entry<String, YamlValue> media : response.find("content").map(c -> c.asMap("content").entries())
                    .orElse(Map.of()).entrySet()) {
                YMap definition = media.getValue().asMap(media.getKey());
                Optional<String> reference = definition.find("schema").flatMap(s -> contract.ref(s.asMap("schema")));
                bodies.add(code(reference.map(r -> r.substring(r.lastIndexOf('/') + 1)).orElse(media.getKey()),
                        operation.id()));
            }
            List<String> headers = response.find("headers").map(h -> h.asMap("headers").entries().keySet())
                    .orElse(Set.of()).stream().filter(h -> !common.contains(h.toLowerCase(Locale.ROOT)))
                    .map(h -> code(h, operation.id())).toList();
            parts.add(code(entry.getKey(), operation.id()) + " " + (bodies.isEmpty() ? "no body"
                    : String.join(" or ", bodies))
                    + (headers.isEmpty() ? "" : (headers.size() == 1 ? ", header " : ", headers ")
                            + String.join(", ", headers)));
        }
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    /** The problem types of the operation's responses that the registry gives the module, in the contract's order. */
    private static String problems(ApiContract contract, Capabilities capabilities, ApiContract.Operation operation,
            String tag) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, YamlValue> entry : operation.definition().map("responses").entries().entrySet()) {
            if (!entry.getKey().matches("[45][0-9]{2}")) {
                continue;
            }
            YMap response = resolved(contract, entry.getValue().asMap(entry.getKey()));
            for (String type : ApiContract.problemTypesNamed(response.optionalString("description").orElse(""))) {
                Optional<ProblemType> registered = contract.problemType(type);
                if (registered.isPresent() && registered.get().modules().contains(tag)) {
                    problems.add(code(entry.getKey() + " " + type, operation.id())
                            + (registered.get().member().isEmpty() ? ""
                                    : " with " + code(registered.get().member(), operation.id()))
                            + (capabilities.plannedProblem(type) ? " (planned)" : ""));
                }
            }
        }
        return problems.isEmpty() ? "none" : String.join(", ", problems);
    }

    /** The names, in lower case, of the headers that every declared response of the contract carries. */
    private static Set<String> commonHeaders(ApiContract contract) {
        Set<String> common = null;
        for (ApiContract.Operation operation : contract.operations()) {
            for (Map.Entry<String, YamlValue> entry : operation.definition().map("responses").entries().entrySet()) {
                YMap response = resolved(contract, entry.getValue().asMap(entry.getKey()));
                Set<String> names = new TreeSet<>();
                response.find("headers").ifPresent(h -> h.asMap("headers").entries().keySet()
                        .forEach(name -> names.add(name.toLowerCase(Locale.ROOT))));
                if (common == null) {
                    common = names;
                } else {
                    common.retainAll(names);
                }
            }
        }
        return common == null ? Set.of() : common;
    }

    /** A value or the component its reference names, which must define it rather than refer to another reference. */
    private static YMap resolved(ApiContract contract, YMap value) {
        Optional<String> reference = contract.ref(value);
        if (reference.isEmpty()) {
            return value;
        }
        YMap target = contract.resolve(reference.get());
        if (target.has("$ref")) {
            throw new DocsException(ApiContract.PATH + ": " + reference.get() + " is a reference itself; reference the "
                    + "component that defines it" + ADR);
        }
        return target;
    }

    /**
     * A value as an inline code span that the site's parser reads back as the value: delimited by one backtick more than
     * the longest run of backticks in it, and padded with a space where CommonMark would otherwise take one off or read
     * a backtick as part of the delimiter. A value with a line break cannot stand in a table row, and fails.
     */
    static String code(String value, String where) {
        if (value.isBlank() || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new DocsException(ApiContract.PATH + " " + where + ": the value '" + value.replace("\r", "\\r")
                    .replace("\n", "\\n") + "' is blank or holds a line break, so no table row can show it" + ADR);
        }
        int longest = 0;
        int run = 0;
        for (int i = 0; i < value.length(); i++) {
            run = value.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        String fence = "`".repeat(longest + 1);
        boolean pad = value.startsWith("`") || value.endsWith("`") || (value.startsWith(" ") && value.endsWith(" "));
        return pad ? fence + " " + value + " " + fence : fence + value + fence;
    }

    /** A table cell's text, with a pipe escaped so that it cannot end the cell; the parser unescapes it in a code span. */
    private static String cell(String text) {
        return text.replace("|", "\\|");
    }
}
