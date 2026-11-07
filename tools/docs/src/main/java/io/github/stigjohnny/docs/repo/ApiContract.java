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

/** {@code contracts/shipping.openapi.yaml}, read as data for the API pages and the brains. */
public final class ApiContract {

    public static final String PATH = "contracts/shipping.openapi.yaml";
    private static final Set<String> METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");

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

    private ApiContract(YMap root, String text, List<Operation> operations) {
        this.root = root;
        this.text = text;
        this.operations = List.copyOf(operations);
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
            return new ApiContract(root, text, operations);
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
}
