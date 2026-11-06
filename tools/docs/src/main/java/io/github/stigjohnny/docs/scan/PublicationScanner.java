package io.github.stigjohnny.docs.scan;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Allowlist;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The publication scanner. It reads the built site as data, file by file, and reports every rule that fires with the
 * file it fired in, never the value. File and directory names are scanned with the same rules ({@link PathNames}), and
 * a path whose name fails is never printed: a placeholder stands in for it in findings, the summary and errors. The
 * scan fails closed: without a dictionary, with a file it cannot read as text, with anything that is not a regular
 * file, or with an exception that no longer matches.
 *
 * <p>The dictionary comes from the environment variable {@value #DICTIONARY_VARIABLE}: one term per line, matched
 * anywhere and in any case after HTML entities are decoded and the text is normalised with NFKC (see {@link Text}).
 */
public final class PublicationScanner {

    public static final String DICTIONARY_VARIABLE = "PUBLIC_SCAN_DICTIONARY";
    public static final String DICTIONARY_RULE = "DICTIONARY";
    public static final String EXCEPTIONS = "tools/docs/publication-scan-exceptions.json";

    static final String DICTIONARY_UNSET = "DICTIONARY_UNSET";
    static final String STALE_EXCEPTION = "STALE_EXCEPTION";
    static final String NOT_A_REGULAR_FILE = "NOT_A_REGULAR_FILE";
    static final String UNREADABLE_TEXT = "UNREADABLE_TEXT";

    private static final List<String> MARKUP = List.of(".html", ".htm", ".svg", ".xml");

    private PublicationScanner() {
    }

    /** Scans the site; {@code revision} is the source revision the summary names, or empty for a local scan. */
    public static ScanResult scan(Path site, @Nullable String dictionary, ScanExceptions exceptions,
            String revision) {
        Path root = site.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new DocsException("there is no site to scan; build it first with ./gradlew :tools:docs:site");
        }
        List<String> terms = terms(dictionary);
        SortedSet<Finding> findings = new TreeSet<>();
        if (terms.isEmpty()) {
            findings.add(new Finding(DICTIONARY_UNSET, ""));
        }
        Set<ScanException> used = new HashSet<>();
        PathNames names = new PathNames(terms);
        StringBuilder manifest = new StringBuilder();
        int files = 0;
        for (String file : paths(root)) {
            Path path = root.resolve(file);
            String display = names.display(file);
            for (String rule : names.ownRules(file)) {
                findings.add(new Finding(rule, display));
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                findings.add(new Finding(NOT_A_REGULAR_FILE, display));
                continue;
            }
            byte[] bytes = read(path, display);
            files++;
            manifest.append(Allowlist.sha256(bytes)).append("  ./").append(file).append('\n');
            Optional<String> text = utf8(bytes);
            if (text.isEmpty()) {
                findings.add(new Finding(UNREADABLE_TEXT, display));
                continue;
            }
            scanText(file, display, text.get(), terms, exceptions, findings, used);
        }
        for (ScanException exception : exceptions.entries()) {
            if (!used.contains(exception)) {
                findings.add(new Finding(STALE_EXCEPTION, names.display(exception.file())));
            }
        }
        List<ScanException> applied = exceptions.entries().stream().filter(used::contains)
                .map(e -> new ScanException(names.display(e.file()), e.rule(), e.sha256(), e.reason())).toList();
        return new ScanResult(List.copyOf(findings), applied,
                Allowlist.sha256(manifest.toString().getBytes(StandardCharsets.UTF_8)), files, revision,
                !terms.isEmpty());
    }

    /** The dictionary's terms, normalised like the text they are compared with; blank lines are skipped. */
    static List<String> terms(@Nullable String dictionary) {
        if (dictionary == null) {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        dictionary.lines().map(line -> Text.forDictionary(Text.normalize(line)).strip())
                .filter(term -> !term.isEmpty()).forEach(terms::add);
        return List.copyOf(terms);
    }

    private static void scanText(String file, String display, String raw, List<String> terms,
            ScanExceptions exceptions, Set<Finding> findings, Set<ScanException> used) {
        boolean markup = MARKUP.stream().anyMatch(file::endsWith);
        String decoded = Text.decoded(raw);
        for (Rule rule : Rule.values()) {
            if (rule == Rule.HTML_COMMENT && !markup) {
                continue;
            }
            Set<String> values = new LinkedHashSet<>(rule.matches(raw));
            if (rule != Rule.HTML_COMMENT) {
                values.addAll(rule.matches(decoded));
            }
            for (String value : values) {
                Optional<ScanException> exception = exceptions.find(file, rule.name(),
                        Allowlist.sha256(value.getBytes(StandardCharsets.UTF_8)));
                if (exception.isPresent()) {
                    used.add(exception.get());
                } else {
                    findings.add(new Finding(rule.name(), display));
                }
            }
        }
        if (terms.isEmpty()) {
            return;
        }
        List<String> views = new ArrayList<>();
        views.add(Text.forDictionary(decoded));
        if (markup) {
            views.add(Text.forDictionary(Text.withoutTags(raw, "")));
            views.add(Text.forDictionary(Text.withoutTags(raw, " ")));
        }
        if (terms.stream().anyMatch(term -> views.stream().anyMatch(view -> view.contains(term)))) {
            findings.add(new Finding(DICTIONARY_RULE, display));
        }
    }

    /**
     * Every path under the site, relative and with forward slashes, sorted as the digest's manifest is. A directory that
     * cannot be listed fails the scan with a message that names no path.
     */
    private static List<String> paths(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(path -> !path.equals(root))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted().toList();
        } catch (IOException e) {
            throw new DocsException("the site cannot be listed (" + e.getClass().getSimpleName() + ")", e);
        } catch (UncheckedIOException e) {
            Throwable cause = e.getCause();
            throw new DocsException("the site cannot be listed (" + (cause == null ? e : cause).getClass().getSimpleName()
                    + ")", e);
        }
    }

    /** The file's bytes; a file that cannot be read fails the scan under its printable name. */
    private static byte[] read(Path path, String display) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new DocsException(display + ": cannot be read (" + e.getClass().getSimpleName() + ")", e);
        }
    }

    private static Optional<String> utf8(byte[] bytes) {
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }
}
