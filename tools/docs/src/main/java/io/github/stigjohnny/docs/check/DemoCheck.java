package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.MarkdownBlock;
import io.github.stigjohnny.docs.repo.DemoRecord;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.repo.TestEvidence;
import io.github.stigjohnny.docs.yaml.YamlValue;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The demonstrations under docs/demos/ (docs/demos/how-to.md), checked with the other sources before anything is
 * generated or published. Each one:
 *
 * <ul>
 *   <li>is {@code docs/demos/<pr>-<slug>.md}, and no other demonstration has its pull request, and so its sheet;
 *   <li>has the documents' frontmatter and its own, nothing else: {@code pr}, a positive integer equal to the file
 *       name's; {@code kind}, one of five; {@code recorded}, an ISO date no later than {@code updated}; and
 *       {@code tests}, test classes that exist and that no other class is named like;
 *   <li>has its sections as Markdown headings: known, each once, in order, none empty, those its kind needs, and
 *       nothing before them;
 *   <li>keeps nothing of the template: no YYYY-MM-DD and no &lt;placeholder&gt;;
 *   <li>shows commands in {@code ```sh} fences, in Try it or Evidence, each one of the approved commands, whole and
 *       with exactly its arguments; and recorded output in {@code ```text} fences, in Evidence, after the line that
 *       says it was not rerun. Its evidence names its capture date, and a gate's shows a rejected and an accepted case;
 *   <li>is text only until a reviewed media gate exists: no image, no raw HTML and no link to a media file, read from
 *       its percent-decoded path; and {@code docs/demos/} holds nothing but Markdown, because the repository is
 *       public as well.
 * </ul>
 *
 * <p>Every block is held to these checks, the section headings and anything before the first section included.
 *
 * <p>No command is ever run. Whether See it reaches pages of the site is checked where the pages are known, when the
 * site is built (DemoPages).
 */
final class DemoCheck {

    static final String MARKER = "Recorded output \u2014 not rerun by this site build.";
    static final List<String> SECTIONS = List.of("What changed", "See it", "Try it", "Evidence", "Limits");

    /** The commands a demonstration may show, each whole and with exactly these arguments: README.md documents them. */
    static final List<String> COMMANDS = List.of(
            "java tools/policy/PolicyCheck.java .",
            "./gradlew check",
            "./gradlew :tools:docs:check",
            "./gradlew :tools:docs:generate",
            "./gradlew :tools:docs:site",
            "git checkout -- libs/c4-contract-kit/architecture/self.dsl");

    /** The checker's documented run of chosen tests: the task, --rerun, then each test class as --tests '*NameTest'. */
    static final Pattern CHECKER_TESTS = Pattern.compile(
            "\\./gradlew :libs:c4-contract-kit:test --rerun(?: --tests '\\*[A-Z][A-Za-z0-9]*Test')+");

    private static final String CHECKER_SOURCES = "libs/c4-contract-kit/src/test/java";
    private static final Pattern CHECKER_TEST = Pattern.compile("--tests '\\*([A-Z][A-Za-z0-9]*Test)'");
    private static final String TRY_IT = "Try it";
    private static final String SEE_IT = "See it";
    private static final String EVIDENCE = "Evidence";
    private static final String BEFORE_THE_SECTIONS = "the text before the first section";
    private static final String SH = "sh";
    private static final String TEXT = "text";
    private static final String SHELL = "; a command is approved whole, without shell syntax";
    private static final String TEXT_ONLY = "; a demonstration is text only until the reviewed media gate exists";
    private static final Pattern PLACEHOLDER = Pattern.compile("<[A-Za-z][^<>\\n]*>");
    private static final Pattern PLACEHOLDER_LINE = Pattern.compile("<[A-Za-z][^<>]*\\s[^<>]*>");
    private static final Pattern TEMPLATE_DATE = Pattern.compile("(?i)YYYY-MM-DD");
    private static final Pattern ABSOLUTE_PATH = Pattern.compile("(?:^|[\\s=])(?:/|~|[A-Za-z]:[\\\\/])");
    private static final Pattern MEDIA = Pattern.compile("(?i)\\.(?:png|jpe?g|gif|webp|avif|svg|bmp|ico|tiff?|apng|mp4|"
            + "m4v|webm|mov|avi|mkv|ogv|mp3|m4a|wav|ogg|oga|flac|cast)$");

    private DemoCheck() {
    }

    static void check(Sources sources, Problems problems) {
        // The repository is public too: a screenshot committed here would be published, if not on the site.
        for (String file : Sources.files(sources.repo(), DemoRecord.DIRECTORY, "")) {
            if (!file.endsWith(".md")) {
                problems.add(file + ": docs/demos/ holds demonstrations as Markdown only; media waits for the reviewed "
                        + "media gate");
            }
        }
        problems(sources, sources.docsUnder(DemoRecord.DIRECTORY).stream().filter(d -> DemoRecord.isDemo(d.path()))
                .toList()).forEach(problems::add);
    }

    /** The problems of the given demonstrations, read against the sources: the tests and commands they name. */
    static List<String> problems(Sources sources, List<DocFile> demos) {
        Set<String> problems = new TreeSet<>();
        Set<String> checkerTests = new HashSet<>();
        for (String file : Sources.files(sources.repo(), CHECKER_SOURCES, ".java")) {
            checkerTests.add(file.substring(file.lastIndexOf('/') + 1, file.length() - ".java".length()));
        }
        Map<Integer, String> byPullRequest = new TreeMap<>();
        for (DocFile doc : demos.stream().sorted(Comparator.comparing(DocFile::path)).toList()) {
            List<String> found = new ArrayList<>();
            Optional<DemoRecord.Kind> kind = read(() -> DemoRecord.kindOf(doc), found);
            Optional<String> recorded = read(() -> DemoRecord.recordedOf(doc), found);
            metadata(doc, recorded, sources.evidence(), byPullRequest, found);
            body(doc, kind, recorded, checkerTests, found);
            found.forEach(problem -> problems.add(problem.startsWith(doc.path() + ": ") ? problem
                    : doc.path() + ": " + problem));
        }
        return List.copyOf(problems);
    }

    private static void metadata(DocFile doc, Optional<String> recorded, TestEvidence evidence,
            Map<Integer, String> byPullRequest, List<String> found) {
        OptionalInt number = DemoRecord.fileNumber(doc.path());
        if (number.isEmpty()) {
            found.add("a demonstration is named docs/demos/<pr>-<slug>.md: its pull request's number, a hyphen and a "
                    + "kebab-case slug");
        }
        for (String key : doc.frontmatter().entries().keySet()) {
            if (!DemoRecord.KEYS.contains(key)) {
                found.add("frontmatter key '" + key + "' is not one of title, description, status, updated, pr, kind, "
                        + "recorded and tests; a revision or a merge is never entered by hand");
            }
        }
        read(() -> DemoRecord.prOf(doc), found).ifPresent(pr -> {
            if (number.isPresent() && number.getAsInt() != pr) {
                found.add("the file name starts with " + number.getAsInt() + ", but pr is " + pr);
            }
            String previous = byPullRequest.putIfAbsent(pr, doc.path());
            if (previous != null) {
                found.add("pull request #" + pr + " already has a demonstration, " + previous + "; each has one sheet, "
                        + DemoRecord.sheetCode(pr));
            }
        });
        recorded.ifPresent(date -> doc.optional("updated")
                .filter(updated -> DemoRecord.isoDate(updated) && date.compareTo(updated) > 0)
                .ifPresent(updated -> found.add("recorded " + date + " is later than updated " + updated)));
        read(() -> DemoRecord.testsOf(doc), found).ifPresent(tests -> tests.forEach(test -> {
            if (!evidence.exists(test)) {
                found.add("tests names " + test + ", which does not exist in the repository");
            } else if (evidence.ambiguous(test)) {
                found.add("tests names " + test + ", which is ambiguous: more than one test class is called " + test);
            }
        }));
        for (String key : List.of("title", "description")) {
            doc.optional(key).ifPresent(value -> placeholder(value).ifPresent(left -> found.add(key + " holds '" + left
                    + "', an unfilled template placeholder")));
        }
        boolean dateLeft = TEMPLATE_DATE.matcher(doc.body()).find() || doc.frontmatter().entries().values().stream()
                .anyMatch(value -> value instanceof YamlValue.YString text
                        && TEMPLATE_DATE.matcher(text.value()).find());
        if (dateLeft) {
            found.add("YYYY-MM-DD is left from the template");
        }
    }

    private static void body(DocFile doc, Optional<DemoRecord.Kind> kind, Optional<String> recorded,
            Set<String> checkerTests, List<String> found) {
        List<MarkdownBlock> outline = DemoRecord.outline(doc);
        // Every block first, before the sections are told apart: the section headings and what stands before the first
        // section are held to the same checks as the text under a heading.
        String under = BEFORE_THE_SECTIONS;
        for (MarkdownBlock block : outline) {
            if (block.kind() == MarkdownBlock.Kind.HEADING && block.level() == 2) {
                under = block.text();
                block("the heading '" + under + "'", block, checkerTests, found);
                continue;
            }
            if (block.kind() == MarkdownBlock.Kind.HEADING && block.level() == 1) {
                found.add("a level-1 heading; a demonstration's title is its frontmatter title");
            }
            block(under, block, checkerTests, found);
        }
        List<DemoRecord.Part> parts = DemoRecord.parts(outline);
        boolean before = parts.getFirst().blocks().stream()
                .anyMatch(b -> b.kind() != MarkdownBlock.Kind.HEADING || b.level() != 1);
        if (before) {
            found.add("text stands before the first section; everything is under What changed, See it, Try it, "
                    + "Evidence or Limits");
        }
        Set<String> seen = new HashSet<>();
        String previous = "";
        for (DemoRecord.Part part : parts.subList(1, parts.size())) {
            String section = part.heading();
            if (!SECTIONS.contains(section)) {
                found.add("the section '" + section + "' is not one of What changed, See it, Try it, Evidence and "
                        + "Limits");
            } else if (!seen.add(section)) {
                found.add("the section '" + section + "' appears twice");
            } else if (!previous.isEmpty() && SECTIONS.indexOf(section) < SECTIONS.indexOf(previous)) {
                found.add("the section '" + section + "' comes after '" + previous + "'; the order is "
                        + String.join(", ", SECTIONS));
            } else {
                previous = section;
            }
            if (part.blocks().stream().noneMatch(MarkdownBlock::hasContent)) {
                found.add("the section '" + section + "' is empty");
            }
        }
        kind.ifPresent(k -> required(k, seen, found));
        parts.stream().filter(p -> p.heading().equals(TRY_IT)).findFirst().ifPresent(tryIt -> {
            boolean command = tryIt.blocks().stream().anyMatch(b -> b.kind() == MarkdownBlock.Kind.FENCED_CODE
                    && b.info().equals(SH) && !b.text().isBlank());
            if (!command) {
                found.add("Try it has no command (```sh)");
            }
        });
        parts.stream().filter(p -> p.heading().equals(EVIDENCE)).findFirst()
                .ifPresent(evidence -> evidence(evidence.blocks(), kind, recorded, found));
    }

    /** One block, wherever it stands, a section's heading included: its fences, commands, placeholders and media. */
    private static void block(String section, MarkdownBlock block, Set<String> checkerTests, List<String> found) {
        switch (block.kind()) {
            case FENCED_CODE -> {
                if (block.info().equals(SH)) {
                    if (!section.equals(TRY_IT) && !section.equals(EVIDENCE)) {
                        found.add(section + " has a command (```sh); commands belong in Try it or Evidence");
                    }
                    if (block.text().isBlank()) {
                        found.add(section + " has an empty command fence");
                    }
                    commands(block.text(), checkerTests).forEach(problem -> found.add(section + ": " + problem));
                } else if (block.info().equals(TEXT)) {
                    if (!section.equals(EVIDENCE)) {
                        found.add(section + " has recorded output (```text); recorded output belongs in Evidence");
                    }
                    if (block.text().isBlank()) {
                        found.add(section + " has an empty output fence");
                    }
                    block.text().lines().map(String::strip).filter(line -> PLACEHOLDER_LINE.matcher(line).matches())
                            .findFirst().ifPresent(left -> found.add(section + " holds '" + left + "', an unfilled "
                                    + "template placeholder"));
                } else {
                    found.add(section + " has " + (block.info().isEmpty() ? "a fence without ```sh or ```text"
                            : "a ```" + block.info() + " fence") + "; a fence is ```sh for a command or ```text for "
                            + "recorded output");
                }
            }
            case INDENTED_CODE -> found.add(section + " has an indented code block; use a ```sh or ```text fence");
            case HEADING, OTHER -> {
                if (!block.code().isEmpty()) {
                    found.add(section + " has a code block inside a list or a quote; fences stand on their own in a "
                            + "section");
                }
                for (String image : block.images()) {
                    found.add(section + " shows the image '" + image + "'" + TEXT_ONLY);
                }
                for (String link : block.links()) {
                    Optional<String> path = decodedPath(link);
                    if (path.isEmpty()) {
                        found.add(section + " links '" + link + "', whose percent-encoding is malformed");
                    } else if (MEDIA.matcher(path.get()).find()) {
                        found.add(section + " links the media file '" + link + "'" + TEXT_ONLY);
                    }
                }
                if (!block.html().isEmpty()) {
                    found.add(section + " holds '" + block.html().getFirst() + "': raw HTML or an unfilled template "
                            + "placeholder; a demonstration is text only");
                }
                placeholder(block.prose()).ifPresent(left -> found.add(section + " holds '" + left + "', an unfilled "
                        + "template placeholder"));
            }
        }
    }

    private static void required(DemoRecord.Kind kind, Set<String> seen, List<String> found) {
        List<String> needed = new ArrayList<>(List.of("What changed", EVIDENCE, "Limits"));
        switch (kind) {
            case VISUAL, DOCS -> needed.add(SEE_IT);
            case GATE, BUILD -> needed.add(TRY_IT);
            case FEATURE -> {
                if (!seen.contains(SEE_IT) && !seen.contains(TRY_IT)) {
                    found.add("a feature demonstration needs the section 'See it' or 'Try it'");
                }
            }
        }
        for (String section : SECTIONS) {
            if (needed.contains(section) && !seen.contains(section)) {
                found.add("a " + kind.value() + " demonstration needs the section '" + section + "'");
            }
        }
    }

    /** The evidence: the line that it was not rerun before any recorded output, its capture date, a gate's cases. */
    private static void evidence(List<MarkdownBlock> blocks, Optional<DemoRecord.Kind> kind, Optional<String> recorded,
            List<String> found) {
        int marker = -1;
        int output = -1;
        int outputs = 0;
        for (int i = 0; i < blocks.size(); i++) {
            MarkdownBlock block = blocks.get(i);
            if (marker < 0 && block.kind() == MarkdownBlock.Kind.OTHER
                    && block.text().lines().map(String::strip).anyMatch(MARKER::equals)) {
                marker = i;
            }
            if (block.kind() == MarkdownBlock.Kind.FENCED_CODE && block.info().equals(TEXT)) {
                outputs++;
                output = output < 0 ? i : output;
            }
        }
        if (marker < 0) {
            found.add("Evidence lacks the line '" + MARKER + "'");
        } else if (output >= 0 && output < marker) {
            found.add("Evidence shows recorded output before the line '" + MARKER + "'");
        }
        String text = String.join("\n", blocks.stream().map(MarkdownBlock::text).toList());
        recorded.filter(date -> !text.contains(date))
                .ifPresent(date -> found.add("Evidence does not name its capture date, recorded " + date));
        if (kind.filter(DemoRecord.Kind.GATE::equals).isPresent() && outputs < 2) {
            found.add("the evidence of a gate shows a rejected and an accepted case: two recorded outputs (```text), "
                    + "not " + outputs);
        }
    }

    /**
     * The problem of each command of a ```sh fence. A line that ends with a backslash continues on the next, as in the
     * shell: the backslash and the newline are removed and nothing takes their place, so {@code ./gradlew\} and
     * {@code check} on the next line are {@code ./gradlewcheck}; whitespace that is written stays.
     */
    private static List<String> commands(String fence, Set<String> checkerTests) {
        List<String> problems = new ArrayList<>();
        StringBuilder command = new StringBuilder();
        for (String line : fence.lines().toList()) {
            if (line.endsWith("\\")) {
                command.append(line, 0, line.length() - 1);
                continue;
            }
            command.append(line);
            String whole = command.toString().strip();
            command.setLength(0);
            if (!whole.isEmpty()) {
                reason(whole, checkerTests).ifPresent(reason -> problems.add("'" + whole + "' " + reason));
            }
        }
        if (!command.isEmpty()) {
            problems.add("'" + command.toString().stripLeading() + "\\' ends with a line continuation (\\) and no next "
                    + "line");
        }
        return problems;
    }

    /** Why a command is not approved, or nothing: shell syntax first, then the command whole, with its arguments. */
    private static Optional<String> reason(String command, Set<String> checkerTests) {
        if (command.contains("$") || command.contains("`")) {
            return Optional.of("substitutes or expands a value ($ or `)" + SHELL);
        }
        if (command.contains("<") || command.contains(">")) {
            return Optional.of("redirects input or output (< or >)" + SHELL);
        }
        if (command.contains(";") || command.contains("&") || command.contains("||")) {
            return Optional.of("chains commands (;, &, && or ||)" + SHELL);
        }
        if (command.contains("|")) {
            return Optional.of("pipes into another command (|)" + SHELL);
        }
        if (command.contains("#")) {
            return Optional.of("holds a comment (#); explanations go outside the fence");
        }
        if (ABSOLUTE_PATH.matcher(command).find()) {
            return Optional.of("names an absolute path; commands run from the repository root");
        }
        if (command.contains("\\")) {
            return Optional.of("escapes a character (\\)" + SHELL);
        }
        String normalized = command.replaceAll("[ \\t]+", " ");
        if (COMMANDS.contains(normalized)) {
            return Optional.empty();
        }
        if (CHECKER_TESTS.matcher(normalized).matches()) {
            Matcher test = CHECKER_TEST.matcher(normalized);
            while (test.find()) {
                if (!checkerTests.contains(test.group(1))) {
                    return Optional.of("names " + test.group(1) + ", which is not a test class of "
                            + "libs/c4-contract-kit");
                }
            }
            return Optional.empty();
        }
        return Optional.of("is not an approved command; a demonstration shows only the commands docs/demos/how-to.md "
                + "lists, whole and with exactly their arguments");
    }

    /** The first unfilled placeholder of a text, such as {@code <observable outcome>}. */
    private static Optional<String> placeholder(String text) {
        Matcher left = PLACEHOLDER.matcher(text);
        return left.find() ? Optional.of(left.group()) : Optional.empty();
    }

    /**
     * A link's path as a server reads it: without its query and fragment, then percent-decoded, so that
     * {@code shot%2epng} is {@code shot.png}. Empty when the encoding is malformed: a {@code %} without two ASCII hex
     * digits, or bytes that are not UTF-8.
     */
    private static Optional<String> decodedPath(String link) {
        String path = link.replaceFirst("[?#].*$", "");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '%') {
                int high = i + 1 < path.length() ? hex(path.charAt(i + 1)) : -1;
                int low = i + 2 < path.length() ? hex(path.charAt(i + 2)) : -1;
                if (high < 0 || low < 0) {
                    return Optional.empty();
                }
                bytes.write(high * 16 + low);
                i += 3;
            } else {
                int codePoint = path.codePointAt(i);
                bytes.writeBytes(Character.toString(codePoint).getBytes(StandardCharsets.UTF_8));
                i += Character.charCount(codePoint);
            }
        }
        try {
            return Optional.of(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray()))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    /** An ASCII hex digit's value, or -1: other digits, such as fullwidth ones, are not hex in a URL. */
    private static int hex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        return c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
    }

    /** A field of the frontmatter, or nothing when it cannot be read; the problem is added to {@code found}. */
    private static <T> Optional<T> read(Supplier<T> field, List<String> found) {
        try {
            return Optional.of(field.get());
        } catch (DocsException e) {
            found.add(e.getMessage());
            return Optional.empty();
        }
    }
}
