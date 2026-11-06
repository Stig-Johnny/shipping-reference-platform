package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YBool;
import io.github.stigjohnny.docs.yaml.YamlValue.YList;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import io.github.stigjohnny.docs.yaml.YamlValue.YNull;
import io.github.stigjohnny.docs.yaml.YamlValue.YNumber;
import io.github.stigjohnny.docs.yaml.YamlValue.YString;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code security/allowlist.json}: the reviewed components, the owner's current acceptance and the previous ones.
 * Read with SnakeYAML Engine's JSON schema; this tool never uses Jackson directly.
 */
public final class Allowlist {

    public static final String PATH = "security/allowlist.json";
    private static final Pattern PULL_REQUEST = Pattern.compile("pull request #(\\d+)");

    private final List<YMap> rawEntries;
    private final List<AllowlistEntry> entries;
    private final Acceptance current;
    private final List<Acceptance> previous;
    private final boolean accepted;

    private Allowlist(List<YMap> rawEntries, List<AllowlistEntry> entries, Acceptance current,
            List<Acceptance> previous, boolean accepted) {
        this.rawEntries = List.copyOf(rawEntries);
        this.entries = List.copyOf(entries);
        this.current = current;
        this.previous = List.copyOf(previous);
        this.accepted = accepted;
    }

    public static Allowlist parse(String json) {
        try {
            YMap root = SafeYaml.json(json, PATH).asMap(PATH);
            List<YMap> raw = new ArrayList<>();
            List<AllowlistEntry> entries = new ArrayList<>();
            for (YamlValue item : root.list("entries")) {
                YMap entry = item.asMap("entry");
                raw.add(entry);
                entries.add(entry(entry));
            }
            YMap acceptance = root.map("acceptance");
            List<Acceptance> previous = new ArrayList<>();
            for (YamlValue item : acceptance.find("previous").map(v -> v.asList("previous")).orElse(List.of())) {
                previous.add(acceptance(item.asMap("previous acceptance")));
            }
            return new Allowlist(raw, entries, acceptance(acceptance), previous,
                    "accepted".equals(acceptance.string("status")));
        } catch (YamlInputException e) {
            throw new DocsException(e.getMessage(), e);
        }
    }

    public List<AllowlistEntry> entries() {
        return entries;
    }

    public Acceptance current() {
        return current;
    }

    public List<Acceptance> previous() {
        return previous;
    }

    public boolean accepted() {
        return accepted;
    }

    public long approved() {
        return entries.stream().filter(e -> !e.rejected()).count();
    }

    public long rejected() {
        return entries.stream().filter(AllowlistEntry::rejected).count();
    }

    /** The review records the entries point to, each once, sorted. */
    public List<String> reviewRecords() {
        return entries.stream().map(AllowlistEntry::reviewRecord).distinct().sorted().toList();
    }

    /**
     * Recomputes the reviewed-set digest the way {@code tools/policy/PolicyCheck.java} does: one line per entry, the
     * entry as canonical JSON (keys sorted, no whitespace), a '|' and the SHA-256 of its review record's bytes; the
     * lines sorted and joined with '\n'; the SHA-256 of that, in hex. The acceptance record itself is not part of it.
     */
    public String reviewedSetDigest(Map<String, byte[]> reviewRecords) {
        List<String> lines = new ArrayList<>();
        for (YMap entry : rawEntries) {
            byte[] review = reviewRecords.get(entry.optionalString("reviewRecord").orElse(""));
            lines.add(canonical(entry) + "|" + (review == null ? "" : sha256(review)));
        }
        return sha256(String.join("\n", lines.stream().sorted().toList()).getBytes(StandardCharsets.UTF_8));
    }

    static String canonical(YamlValue value) {
        return switch (value) {
            case YMap map -> {
                StringBuilder out = new StringBuilder("{");
                for (String key : map.entries().keySet().stream().sorted().toList()) {
                    if (out.length() > 1) {
                        out.append(',');
                    }
                    out.append(quote(key)).append(':').append(canonical(map.get(key)));
                }
                yield out.append('}').toString();
            }
            case YList list -> {
                List<String> items = list.items().stream().map(Allowlist::canonical).toList();
                yield "[" + String.join(",", items) + "]";
            }
            case YString string -> quote(string.value());
            case YNumber number -> number.text();
            case YBool bool -> Boolean.toString(bool.value());
            case YNull ignored -> "null";
        };
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append("\\u%04x".formatted((int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static AllowlistEntry entry(YMap entry) {
        String project = String.join(", ", texts(entry, "project"));
        List<String> configurations = texts(entry, "configuration");
        String scope = project
                + (configurations.isEmpty() ? "" : (project.isEmpty() ? "" : ": ") + String.join(", ", configurations));
        return new AllowlistEntry(entry.string("kind"), entry.string("coordinate"), entry.string("version"),
                entry.optionalString("artifact").orElse(""), entry.string("verdict"), scope,
                entry.find("conditions").filter(v -> v instanceof YList).map(v -> v.asList("conditions")).orElse(
                        List.of()).stream().map(c -> c.asText("condition")).toList(),
                entry.string("reviewRecord"), entry.string("reviewedAt"), entry.string("expiresAt"));
    }

    /** A field that is a string or a list of strings, as a list of trimmed, non-empty texts. */
    private static List<String> texts(YMap entry, String key) {
        return entry.find(key).filter(v -> !(v instanceof YNull))
                .map(v -> v instanceof YList list ? list.items().stream().map(i -> i.asText(key).strip()).toList()
                        : List.of(v.asText(key).strip()))
                .orElse(List.of()).stream().filter(t -> !t.isEmpty()).toList();
    }

    private static Acceptance acceptance(YMap acceptance) {
        String how = acceptance.optionalString("how").orElse("");
        Matcher pr = PULL_REQUEST.matcher(how);
        List<String> notes = new ArrayList<>();
        acceptance.optionalString("projectPathConfirmed").ifPresent(note -> notes.add("Project path confirmed: " + note));
        return new Acceptance(acceptance.optionalString("at").orElse(""), pr.find() ? "pull request #" + pr.group(1) : "",
                acceptance.optionalString("reviewedSetSha256").orElse(""),
                acceptance.optionalString("entriesSha256").orElse(""), notes);
    }
}
