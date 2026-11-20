package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.InlineCode;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.repo.Capability;
import io.github.stigjohnny.docs.repo.DocFile;
import io.github.stigjohnny.docs.repo.Sources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The references in inline code (ADR 0023). In every hand-written Markdown file:
 *
 * <ul>
 *   <li>a span that is a test class name, {@code NameTest}, names a test class that exists in the repository;
 *   <li>{@code planned:test:NameTest} names a test that does not exist yet and is registered as planned: an acceptance
 *       test of a capability or control that is not shipped, or a test that an ADR's planned enforcement names;
 *   <li>{@code illustrative:test:<...>} is a schematic example with a placeholder in angle brackets;
 *   <li>any other span that starts with {@code planned:}, {@code illustrative:} or {@code absent:} is a marker that the
 *       grammar does not define yet, and fails.
 * </ul>
 *
 * <p>Not read: code blocks, generated files, records (contribution history and runs, demonstrations other than the
 * how-to, security reviews and verifications), superseded ADRs, and the directories the instruction check skips. The
 * check proves names and markers, not claims: whether a sentence says that a test passes is outside it.
 */
final class References {

    static final Pattern TEST = Pattern.compile("[A-Z][A-Za-z0-9]*Test");
    private static final Pattern TEST_IN_TEXT = Pattern.compile("\\b[A-Z][A-Za-z0-9]*Test\\b");
    private static final Pattern MARKER = Pattern.compile("(planned|illustrative|absent):(.*)", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = Pattern.compile(".*<[^<>]+>.*", Pattern.DOTALL);
    private static final List<String> NOT_READ = List.of("BRAIN.md", "docs/brain/", "docs/architecture/",
            "docs/contributions/", "security/reviews/", "security/verifications/");
    private static final String DEMOS = "docs/demos/";
    private static final String HOW_TO = "docs/demos/how-to.md";
    private static final String UNDEFINED = "uses a marker that ADR 0023 does not define yet; the markers are "
            + "planned:test:NameTest and illustrative:test:<placeholder> (ADR 0023)";

    private References() {
    }

    static void check(Sources sources, Problems problems) {
        Path repo = sources.repo();
        Set<String> tests = new TreeSet<>();
        List<String> markdown = new ArrayList<>();
        walk(repo, tests, markdown);
        Set<String> planned = new TreeSet<>();
        Set<String> superseded = new TreeSet<>();
        for (Capability entry : sources.capabilities().all()) {
            if (entry.status() != Capability.Status.SHIPPED) {
                planned.addAll(entry.acceptanceTests());
            }
        }
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
            if ("superseded".equals(adr.status())) {
                superseded.add(adr.path());
            }
            if (adr.frontmatter().has("plannedEnforcement")) {
                for (String entry : adr.frontmatter().strings("plannedEnforcement")) {
                    Matcher test = TEST_IN_TEXT.matcher(entry);
                    while (test.find()) {
                        planned.add(test.group());
                    }
                }
            }
        }
        for (String path : markdown) {
            if (!read(path) || superseded.contains(path)) {
                continue;
            }
            String text = text(repo, path);
            int body = bodyStart(text);
            int offset = (int) text.substring(0, body).chars().filter(c -> c == '\n').count();
            for (InlineCode code : MarkdownRenderer.inlineCode(text.substring(body), path)) {
                String where = path + ":" + (code.line() == 0 ? 0 : code.line() + offset);
                check(code.text(), where, tests, planned, problems);
            }
        }
    }

    private static void check(String text, String where, Set<String> tests, Set<String> planned, Problems problems) {
        Matcher marker = MARKER.matcher(text);
        if (marker.matches()) {
            marker(text, marker.group(1), marker.group(2), where, tests, planned, problems);
        } else if (TEST.matcher(text).matches() && !tests.contains(text)) {
            problems.add(where + ": `" + text + "` names no test class in the repository; if it is planned, write "
                    + "planned:test:" + text + " (ADR 0023)");
        }
    }

    private static void marker(String text, String state, String rest, String where, Set<String> tests,
            Set<String> planned, Problems problems) {
        String quoted = where + ": `" + text + "` ";
        if (!rest.startsWith("test:")) {
            problems.add(quoted + UNDEFINED);
            return;
        }
        String name = rest.substring("test:".length());
        switch (state) {
            case "planned" -> {
                if (!TEST.matcher(name).matches()) {
                    problems.add(quoted + "needs a test class name, such as planned:test:NameTest (ADR 0023)");
                } else if (tests.contains(name)) {
                    problems.add(where + ": `" + text + "`: " + name + " exists, so name it without planned: "
                            + "(ADR 0023)");
                } else if (!planned.contains(name)) {
                    problems.add(where + ": `" + text + "`: " + name + " is not registered as planned; list it in the "
                            + "acceptanceTests of a capability or control that is not shipped, or in an ADR's "
                            + "plannedEnforcement (ADR 0023)");
                }
            }
            case "illustrative" -> {
                if (!PLACEHOLDER.matcher(name).matches()) {
                    problems.add(quoted + "needs a placeholder in angle brackets, such as "
                            + "illustrative:test:<TestClass> (ADR 0023)");
                }
            }
            default -> problems.add(quoted + UNDEFINED);
        }
    }

    /** Whether a Markdown file is current prose that the check reads, rather than generated or a record. */
    private static boolean read(String path) {
        if (path.startsWith(DEMOS)) {
            return path.equals(HOW_TO);
        }
        return NOT_READ.stream().noneMatch(entry -> entry.endsWith("/") ? path.startsWith(entry) : path.equals(entry));
    }

    /** Where the body starts: after YAML frontmatter between two '---' lines, or at the start. */
    private static int bodyStart(String text) {
        if (!text.startsWith("---\n")) {
            return 0;
        }
        int close = text.indexOf("\n---\n", 3);
        return close < 0 ? 0 : close + "\n---\n".length();
    }

    /**
     * Collects the simple names of the Java files named like test classes, and the paths of the Markdown files,
     * outside the skipped directories and Claude Code's worktrees, without following links.
     */
    private static void walk(Path repo, Set<String> tests, List<String> markdown) {
        try {
            Files.walkFileTree(repo, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    boolean skipped = !directory.equals(repo) && AgentInstructions.skipped(relative(repo, directory));
                    return skipped ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    String name = name(file);
                    if (attributes.isRegularFile() && name.endsWith("Test.java")) {
                        tests.add(name.substring(0, name.length() - ".java".length()));
                    } else if (attributes.isRegularFile() && name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                        markdown.add(relative(repo, file));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                    throw e;
                }
            });
        } catch (IOException e) {
            throw new DocsException("the documents cannot be listed: " + e.getClass().getSimpleName(), e);
        }
        markdown.sort(null);
    }

    private static String name(Path path) {
        return String.valueOf(path.getFileName());
    }

    /** The path from the repository, its names joined with '/', each name kept whole. */
    private static String relative(Path repo, Path path) {
        List<String> names = new ArrayList<>();
        for (Path name : repo.relativize(path)) {
            names.add(name.toString());
        }
        return String.join("/", names);
    }

    private static String text(Path repo, String path) {
        Path file = repo;
        for (String name : path.split("/", -1)) {
            file = file.resolve(name);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DocsException(path + " cannot be read: " + e.getClass().getSimpleName(), e);
        }
    }
}
