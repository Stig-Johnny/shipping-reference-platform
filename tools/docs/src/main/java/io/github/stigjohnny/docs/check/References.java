package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.markdown.InlineCode;
import io.github.stigjohnny.docs.markdown.MarkdownRenderer;
import io.github.stigjohnny.docs.markdown.ReferenceMarkers;
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
 *   <li>a span that is a test class name, {@code NameTest}, names a test class that a test source in the repository
 *       declares ({@link JavaDeclarations}, {@link #testSource});
 *   <li>{@code planned:test:NameTest} names a test that does not exist yet and is registered as planned: an acceptance
 *       test of a capability or control that is not shipped, or a test that an ADR's planned enforcement names;
 *   <li>{@code illustrative:test:<...>} is a schematic example with a placeholder in angle brackets;
 *   <li>{@code cap:id} names a capability or control in {@code docs/capabilities/capabilities.yaml}; a bare id that the
 *       register has fails, because it must be marked, and so does a bare token with the shape of an id, a known
 *       prefix and a hyphenated name, that the register does not have;
 *   <li>any other span that starts with {@code planned:}, {@code illustrative:} or {@code absent:} is a marker that the
 *       grammar does not define yet, and fails.
 * </ul>
 *
 * <p>Not read: code blocks, and Markdown that is no current prose ({@link CurrentProse}): generated files, records
 * (contribution history and runs, demonstrations other than the how-to, security reviews and verifications) and
 * superseded ADRs; nor the directories the instruction check skips. The site shows labels where this check reads. The
 * check proves names and markers, not claims: whether a sentence says that a test passes is outside it.
 */
final class References {

    static final Pattern TEST = ReferenceMarkers.TEST_NAME;
    private static final Pattern TEST_IN_TEXT = Pattern.compile("\\b[A-Z][A-Za-z0-9]*Test\\b");
    private static final Pattern MARKER = Pattern.compile("(planned|illustrative|absent):(.*)", Pattern.DOTALL);
    private static final Pattern PLACEHOLDER = ReferenceMarkers.PLACEHOLDER;
    private static final String UNDEFINED = "uses a marker that ADR 0023 does not define yet; the markers are "
            + "planned:test:NameTest, illustrative:test:<placeholder> and cap:<id> (ADR 0023)";
    private static final String CAP = "cap:";
    private static final String REGISTER = "docs/capabilities/capabilities.yaml";

    private References() {
    }

    static void check(Sources sources, Problems problems) {
        Path repo = sources.repo();
        Set<String> tests = new TreeSet<>();
        List<String> markdown = new ArrayList<>();
        walk(repo, tests, markdown);
        Set<String> planned = new TreeSet<>();
        Set<String> ids = new TreeSet<>();
        for (Capability entry : sources.capabilities().all()) {
            ids.add(entry.id());
            if (entry.status() != Capability.Status.SHIPPED) {
                planned.addAll(entry.acceptanceTests());
            }
        }
        Pattern idShape = idShape(ids);
        for (DocFile adr : sources.docsUnder("docs/adr/")) {
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
            if (!CurrentProse.of(sources, path)) {
                continue;
            }
            String text = text(repo, path).replace("\r\n", "\n");
            int body = bodyStart(text);
            int offset = (int) text.substring(0, body).chars().filter(c -> c == '\n').count();
            for (InlineCode code : MarkdownRenderer.inlineCode(text.substring(body), path)) {
                String where = path + ":" + (code.line() == 0 ? 0 : code.line() + offset);
                check(code.text(), where, new Register(tests, planned, ids, idShape), problems);
            }
        }
    }

    /** What the references are checked against: the declared and planned tests, and the capability register. */
    private record Register(Set<String> tests, Set<String> planned, Set<String> ids, Pattern idShape) {
    }

    private static void check(String text, String where, Register register, Problems problems) {
        Matcher marker = MARKER.matcher(text);
        if (marker.matches()) {
            marker(text, marker.group(1), marker.group(2), where, register.tests(), register.planned(), problems);
        } else if (text.startsWith(CAP)) {
            capability(text, where, register.ids(), problems);
        } else if (TEST.matcher(text).matches() && !register.tests().contains(text)) {
            problems.add(where + ": `" + text + "` names no test class in the repository; if it is planned, write "
                    + "planned:test:" + text + " (ADR 0023)");
        } else if (register.ids().contains(text)) {
            problems.add(where + ": `" + text + "` names a capability or control; write cap:" + text + " (ADR 0023)");
        } else if (register.idShape().matcher(text).matches()) {
            problems.add(where + ": `" + text + "` has the shape of a capability or control id, but no entry in "
                    + REGISTER + " has it (ADR 0023)");
        }
    }

    private static void capability(String text, String where, Set<String> ids, Problems problems) {
        String id = text.substring(CAP.length());
        if (id.isEmpty() || id.chars().anyMatch(Character::isWhitespace)) {
            problems.add(where + ": `" + text + "` needs one capability or control id, such as "
                    + "cap:control.inline-references (ADR 0023)");
        } else if (!ids.contains(id)) {
            problems.add(where + ": `" + text + "`: " + id + " is no capability or control in " + REGISTER
                    + " (ADR 0023)");
        }
    }

    /**
     * The shape of a capability or control id that a bare token is checked for: a prefix that the register's ids use,
     * a dot and a lower-case name with at least one hyphen, so that a table name or a configuration key such as
     * {@code tenants.tenant} is not taken for one.
     */
    private static Pattern idShape(Set<String> ids) {
        Set<String> prefixes = new TreeSet<>();
        for (String id : ids) {
            if (id.indexOf('.') > 0) {
                prefixes.add(Pattern.quote(id.substring(0, id.indexOf('.'))));
            }
        }
        return Pattern.compile("(?:" + String.join("|", prefixes) + ")\\.[a-z0-9]+(?:-[a-z0-9]+)+");
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

    /** Where the body starts: after YAML frontmatter between two '---' lines, or at the start. */
    private static int bodyStart(String text) {
        if (!text.startsWith("---\n")) {
            return 0;
        }
        int close = text.indexOf("\n---\n", 3);
        return close < 0 ? 0 : close + "\n---\n".length();
    }

    /**
     * Collects the test classes that the test sources declare and the paths of the Markdown files, outside the skipped
     * directories and Claude Code's worktrees, without following links.
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
                    if (attributes.isRegularFile() && name.endsWith(".java") && testSource(relative(repo, file))) {
                        tests.addAll(JavaDeclarations.testClasses(text(repo, relative(repo, file))));
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

    /**
     * Whether a Java file, repository-relative, is a test source: under a source set named test or ending in Test, such
     * as {@code src/test/java} or {@code src/providerIdentityTest/java}, or a file whose name ends in Test, such as a
     * test that runs on the JDK alone.
     */
    static boolean testSource(String path) {
        if (path.endsWith("Test.java")) {
            return true;
        }
        String[] names = path.split("/", -1);
        for (int k = 0; k + 2 < names.length; k++) {
            boolean testSet = names[k + 1].equals("test") || names[k + 1].endsWith("Test");
            if (names[k].equals("src") && testSet && names[k + 2].equals("java")) {
                return true;
            }
        }
        return false;
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
