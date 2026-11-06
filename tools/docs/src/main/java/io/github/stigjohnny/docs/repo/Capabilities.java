package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.List;

/** {@code docs/capabilities/capabilities.yaml}: the capabilities of each module and the controls, with evidence. */
public record Capabilities(List<Capability> capabilities, List<Capability> controls) {

    public static final String PATH = "docs/capabilities/capabilities.yaml";
    private static final String NONE_YET = "none yet";

    public Capabilities {
        capabilities = List.copyOf(capabilities);
        controls = List.copyOf(controls);
    }

    public static Capabilities parse(String text) {
        try {
            YMap root = SafeYaml.yaml(text, PATH).asMap(PATH);
            if (!"1".equals(root.string("schemaVersion"))) {
                throw new DocsException(PATH + ": schemaVersion must be 1");
            }
            return new Capabilities(entries(root.list("capabilities"), false), entries(root.list("controls"), true));
        } catch (YamlInputException e) {
            throw new DocsException(e.getMessage().startsWith(PATH) ? e.getMessage() : PATH + ": " + e.getMessage(),
                    e);
        }
    }

    /** Whether no product capability is built yet: every capability (not control) is planned. */
    public boolean specificationOnly() {
        return capabilities.stream().allMatch(c -> c.status() == Capability.Status.PLANNED);
    }

    /** Capabilities first, then controls. */
    public List<Capability> all() {
        List<Capability> all = new ArrayList<>(capabilities);
        all.addAll(controls);
        return List.copyOf(all);
    }

    public List<Capability> ofModule(String module) {
        return capabilities.stream().filter(c -> c.module().equals(module)).toList();
    }

    public List<Capability> documentedIn(String docPath) {
        return all().stream().filter(c -> c.docs().equals(docPath)).toList();
    }

    private static List<Capability> entries(List<YamlValue> items, boolean control) {
        List<Capability> out = new ArrayList<>();
        for (YamlValue item : items) {
            YMap entry = item.asMap("capability");
            String id = entry.string("id");
            String where = PATH + " " + id;
            out.add(new Capability(id, control,
                    control ? "" : entry.string("module"),
                    control ? entry.string("where") : "",
                    control ? entry.string("check") : "",
                    entry.string("title"),
                    Capability.Status.of(entry.string("status"), where),
                    entry.string("target"),
                    evidence(entry, where),
                    entry.strings("acceptanceTests"),
                    entry.string("docs"),
                    control ? entry.string("negativeFixture") : "",
                    entry.optionalString("enforcedSubset").orElse(""),
                    entry.optionalString("plannedRemainder").orElse("")));
        }
        return out;
    }

    private static List<String> evidence(YMap entry, String where) {
        YamlValue evidence = entry.get("evidence");
        if (evidence instanceof YamlValue.YString text) {
            if (!NONE_YET.equals(text.value())) {
                throw new DocsException(where + ": evidence is a list of test names or the string 'none yet'");
            }
            return List.of();
        }
        List<String> names = entry.strings("evidence");
        if (names.isEmpty()) {
            throw new DocsException(where + ": an empty evidence list must be written 'none yet'");
        }
        return names;
    }
}
