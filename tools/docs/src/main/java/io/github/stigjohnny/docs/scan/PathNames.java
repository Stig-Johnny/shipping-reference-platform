package io.github.stigjohnny.docs.scan;

import io.github.stigjohnny.docs.repo.Allowlist;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The names in the site's paths, scanned with the same rules as file contents. Each segment of a path is scanned, with
 * and without its extension, before and after HTML entities are decoded and NFKC is applied; the dictionary matches
 * them like text. A path is printed only when every segment passes; otherwise it is withheld behind a placeholder that
 * holds the first 12 hexadecimal digits of the SHA-256 of the relative path, so the owner can find it locally.
 */
final class PathNames {

    private final List<String> terms;
    private final Map<String, Set<String>> bySegment = new HashMap<>();

    PathNames(List<String> terms) {
        this.terms = List.copyOf(terms);
    }

    /** The rules that the last segment of this path fails: its own name, not its directories'. */
    Set<String> ownRules(String path) {
        int slash = path.lastIndexOf('/');
        return rules(slash < 0 ? path : path.substring(slash + 1));
    }

    /** The path as it may be printed: itself when every segment passes, else a placeholder. */
    String display(String path) {
        for (String segment : path.split("/", -1)) {
            if (!rules(segment).isEmpty()) {
                return withheld(path);
            }
        }
        return path;
    }

    static String withheld(String path) {
        return "[withheld path, sha256 " + Allowlist.sha256(path.getBytes(StandardCharsets.UTF_8)).substring(0, 12)
                + "]";
    }

    private Set<String> rules(String segment) {
        return bySegment.computeIfAbsent(segment, this::scan);
    }

    private Set<String> scan(String segment) {
        Set<String> fired = new TreeSet<>();
        int dot = segment.lastIndexOf('.');
        List<String> views = dot > 0 ? List.of(segment, segment.substring(0, dot)) : List.of(segment);
        for (String view : views) {
            String decoded = Text.decoded(view);
            for (Rule rule : Rule.values()) {
                if (rule != Rule.HTML_COMMENT && (!rule.matches(view).isEmpty() || !rule.matches(decoded).isEmpty())) {
                    fired.add(rule.name());
                }
            }
            String forDictionary = Text.forDictionary(decoded);
            if (terms.stream().anyMatch(forDictionary::contains)) {
                fired.add(PublicationScanner.DICTIONARY_RULE);
            }
        }
        return fired;
    }
}
