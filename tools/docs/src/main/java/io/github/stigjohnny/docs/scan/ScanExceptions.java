package io.github.stigjohnny.docs.scan;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The exact exceptions of the publication scan, from {@code tools/docs/publication-scan-exceptions.json}. They exist
 * for the documented public demo tokens only, so every entry names {@link Rule#BEARER_TOKEN}, one file of the site and
 * the SHA-256 of one value, and gives a reason.
 */
public record ScanExceptions(List<ScanException> entries) {

    /** The only rule an exception may name: the documented demo tokens are bearer credentials. */
    static final String DEMO_TOKEN_RULE = Rule.BEARER_TOKEN.name();

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SITE_PATH = Pattern.compile("[A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)*");
    private static final Set<String> TOP_LEVEL = Set.of("about", "exceptions");
    private static final Set<String> FIELDS = Set.of("file", "rule", "sha256", "reason");

    public ScanExceptions {
        entries = List.copyOf(entries);
    }

    public static ScanExceptions none() {
        return new ScanExceptions(List.of());
    }

    public static ScanExceptions of(ScanException... entries) {
        return new ScanExceptions(List.of(entries));
    }

    /** Parses and checks the exceptions file; anything else than exact demo-token exceptions fails. */
    public static ScanExceptions parse(String json, String source) {
        try {
            YMap root = SafeYaml.json(json, source).asMap(source);
            if (!TOP_LEVEL.containsAll(root.entries().keySet()) || !root.has("exceptions")) {
                throw new DocsException(source + ": expected the keys 'about' and 'exceptions' only");
            }
            List<ScanException> entries = new ArrayList<>();
            Set<ScanException> seen = new HashSet<>();
            int number = 0;
            for (YamlValue item : root.list("exceptions")) {
                number++;
                String where = source + ": exception " + number;
                YMap entry = item.asMap(where);
                if (!entry.entries().keySet().equals(FIELDS)) {
                    throw new DocsException(where + ": expected exactly the keys " + FIELDS.stream().sorted().toList());
                }
                ScanException exception = new ScanException(entry.string("file"), entry.string("rule"),
                        entry.string("sha256"), entry.string("reason"));
                check(exception, where);
                if (!seen.add(exception)) {
                    throw new DocsException(where + ": is listed twice");
                }
                entries.add(exception);
            }
            return new ScanExceptions(entries);
        } catch (YamlInputException e) {
            throw new DocsException(source + ": " + e.getMessage(), e);
        }
    }

    /** The exception for this value of this rule in this file, if there is one. */
    Optional<ScanException> find(String file, String rule, String sha256) {
        return entries.stream()
                .filter(e -> e.file().equals(file) && e.rule().equals(rule) && e.sha256().equals(sha256))
                .findFirst();
    }

    private static void check(ScanException exception, String where) {
        if (!exception.rule().equals(DEMO_TOKEN_RULE)) {
            throw new DocsException(where + ": exceptions are only for " + DEMO_TOKEN_RULE
                    + ", the rule of the documented public demo tokens");
        }
        List<String> segments = List.of(exception.file().split("/", -1));
        if (!SITE_PATH.matcher(exception.file()).matches() || segments.contains("..") || segments.contains(".")) {
            throw new DocsException(where + ": 'file' must be a path inside the site, such as model/page.html");
        }
        if (!SHA256.matcher(exception.sha256()).matches()) {
            throw new DocsException(where + ": 'sha256' must be 64 lower-case hexadecimal digits");
        }
        if (exception.reason().isBlank()) {
            throw new DocsException(where + ": 'reason' must say why the value is public");
        }
    }
}
