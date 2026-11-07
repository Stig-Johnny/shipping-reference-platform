package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.MarkdownBlock;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.yaml.YamlValue;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A demonstration: what one pull request changed, for a reader who does not read code, with its evidence and its
 * limits (docs/demos/how-to.md). Its file is {@code docs/demos/<pr>-<slug>.md}; {@code docs/demos/how-to.md}, the
 * recipe, is not one. The check of its form is DemoCheck; this record only reads it.
 *
 * @param pr the pull request's number, which the file name starts with
 * @param kind what the demonstration shows, which decides the sections it needs
 * @param recorded the ISO date its evidence was recorded; it never changes, and the register sorts by it
 * @param tests test classes, by simple name, that check the change in every build; on the site only those that passed
 *     in the build that made it are evidence
 */
public record DemoRecord(DocFile doc, int pr, Kind kind, String recorded, List<String> tests) {

    public static final String DIRECTORY = "docs/demos/";
    public static final String HOW_TO = DIRECTORY + "how-to.md";
    /** A demonstration's frontmatter: the four every document has, then its own. A revision or a merge is not one. */
    public static final List<String> KEYS = List.of("title", "description", "status", "updated", "pr", "kind",
            "recorded", "tests");
    /** The register's order: the newest recording first, then the higher pull request; an edit never reorders it. */
    public static final Comparator<DemoRecord> NEWEST_FIRST = Comparator.comparing(DemoRecord::recorded,
            Comparator.reverseOrder()).thenComparing(DemoRecord::pr, Comparator.reverseOrder());

    private static final Pattern FILE = Pattern.compile("docs/demos/([1-9][0-9]{0,8})-[a-z0-9]+(?:-[a-z0-9]+)*\\.md");
    private static final Pattern NUMBER = Pattern.compile("[1-9][0-9]{0,8}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern TEST_CLASS = Pattern.compile("[A-Z][A-Za-z0-9]*Test");

    public DemoRecord {
        tests = List.copyOf(tests);
    }

    /** What a demonstration shows. */
    public enum Kind {
        VISUAL("visual", "Visual"),
        GATE("gate", "Gate"),
        BUILD("build", "Build"),
        DOCS("docs", "Documentation"),
        FEATURE("feature", "Feature");

        private final String value;
        private final String words;

        Kind(String value, String words) {
            this.value = value;
            this.words = words;
        }

        /** As the frontmatter writes it. */
        public String value() {
            return value;
        }

        /** In words, for the register and the sheet. */
        public String words() {
            return words;
        }
    }

    /**
     * One part of a demonstration's body: a level-2 section, named by its heading, whose blocks start with the heading's
     * own block; or, under "", what stands before the first section.
     */
    public record Part(String heading, List<MarkdownBlock> blocks) {

        public Part {
            blocks = List.copyOf(blocks);
        }
    }

    /** Whether a document under docs/ is a demonstration: one under docs/demos/ that is not the how-to. */
    public static boolean isDemo(String path) {
        return path.startsWith(DIRECTORY) && !path.equals(HOW_TO);
    }

    /** The pull request number a file name starts with, when the name is {@code docs/demos/<pr>-<slug>.md}. */
    public static OptionalInt fileNumber(String path) {
        Matcher file = FILE.matcher(path);
        return file.matches() ? OptionalInt.of(Integer.parseInt(file.group(1))) : OptionalInt.empty();
    }

    /** A demonstration's sheet code: X- and its pull request's number, never its position in the register. */
    public static String sheetCode(int pr) {
        return String.format(Locale.ROOT, "X-%02d", pr);
    }

    /** Reads a demonstration whose form DemoCheck has passed; any field it cannot read fails. */
    public static DemoRecord of(DocFile doc) {
        return new DemoRecord(doc, prOf(doc), kindOf(doc), recordedOf(doc), testsOf(doc));
    }

    /** Every demonstration of the sources, in the register's order. */
    public static List<DemoRecord> all(Sources sources) {
        return sources.docsUnder(DIRECTORY).stream().filter(d -> isDemo(d.path())).map(DemoRecord::of)
                .sorted(NEWEST_FIRST).toList();
    }

    /** {@code pr}: a positive integer, written as a number. */
    public static int prOf(DocFile doc) {
        YamlValue value = doc.frontmatter().find("pr")
                .orElseThrow(() -> new DocsException(doc.path() + ": frontmatter missing key 'pr'"));
        if (value instanceof YamlValue.YNumber number && NUMBER.matcher(number.text()).matches()) {
            return Integer.parseInt(number.text());
        }
        throw new DocsException(doc.path() + ": pr must be the pull request's number, a positive integer without "
                + "quotes");
    }

    /** {@code kind}: one of the five kinds. */
    public static Kind kindOf(DocFile doc) {
        String value = doc.text("kind");
        for (Kind kind : Kind.values()) {
            if (kind.value().equals(value)) {
                return kind;
            }
        }
        throw new DocsException(doc.path() + ": kind '" + value + "' is not one of visual, gate, build, docs and "
                + "feature");
    }

    /** {@code recorded}: an ISO date, YYYY-MM-DD. */
    public static String recordedOf(DocFile doc) {
        String value = doc.text("recorded");
        if (!isoDate(value)) {
            throw new DocsException(doc.path() + ": recorded '" + value + "' is not an ISO date (YYYY-MM-DD)");
        }
        return value;
    }

    /** Whether a text is a date of the calendar written YYYY-MM-DD. */
    public static boolean isoDate(String text) {
        if (!DATE.matcher(text).matches()) {
            return false;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** {@code tests}: absent, or a list of test class names, each once. */
    public static List<String> testsOf(DocFile doc) {
        Optional<YamlValue> value = doc.frontmatter().find("tests");
        if (value.isEmpty()) {
            return List.of();
        }
        if (!(value.get() instanceof YamlValue.YList list)) {
            throw new DocsException(doc.path() + ": tests is a list of test class names, such as [BrainCheckTest]");
        }
        List<String> tests = new ArrayList<>();
        for (YamlValue item : list.items()) {
            String name = item instanceof YamlValue.YString text ? text.value() : "";
            if (!TEST_CLASS.matcher(name).matches()) {
                throw new DocsException(doc.path() + ": tests names '" + name + "', which is not a test class name (it "
                        + "ends in Test)");
            }
            if (tests.contains(name)) {
                throw new DocsException(doc.path() + ": tests names " + name + " twice");
            }
            tests.add(name);
        }
        return tests;
    }

    /** Every top-level block of a demonstration's body, in order, its section headings included. */
    public static List<MarkdownBlock> outline(DocFile doc) {
        return MarkdownRenderer.outline(doc.body(), doc.path());
    }

    /** The parts of a demonstration's body, in order: first what stands before its first level-2 heading. */
    public static List<Part> parts(DocFile doc) {
        return parts(outline(doc));
    }

    /** The parts of an outline: each level-2 heading starts a section, and its block is the section's first. */
    public static List<Part> parts(List<MarkdownBlock> outline) {
        List<Part> parts = new ArrayList<>();
        String heading = "";
        List<MarkdownBlock> blocks = new ArrayList<>();
        for (MarkdownBlock block : outline) {
            if (block.kind() == MarkdownBlock.Kind.HEADING && block.level() == 2) {
                parts.add(new Part(heading, blocks));
                heading = block.text();
                blocks = new ArrayList<>();
            }
            blocks.add(block);
        }
        parts.add(new Part(heading, blocks));
        return parts;
    }

    /** A section of this demonstration, by its heading. */
    public Optional<Part> part(String heading) {
        return parts(doc).stream().filter(p -> p.heading().equals(heading)).findFirst();
    }
}
