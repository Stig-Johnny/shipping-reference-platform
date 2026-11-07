package io.github.stigjohnny.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.scan.PublicationScanner;
import io.github.stigjohnny.docs.site.SiteBuilder;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command line: every failure, an {@link Error} included, fails the run with its exit code; a run that takes too
 * long is stopped; and the scan reads its dictionary from the environment, fails closed without it and prints only
 * rule IDs and files.
 */
class DocsToolTest {

    private static final String TERM = "synthetic-" + "term-one";
    private static final String DICTIONARY = TERM + "\n" + "synthetic-" + "term-two";
    private static final String REVISION = "0123456789abcdef0123456789abcdef01234567";

    @TempDir
    Path dir;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void aStackOverflowErrorFailsWithExitCode2() {
        int code = DocsTool.guarded(() -> {
            throw new StackOverflowError();
        }, stream(err), Duration.ofSeconds(10));

        assertThat(code).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("StackOverflowError");
    }

    @Test
    void aDocumentationProblemFailsWithExitCode1() {
        int code = DocsTool.guarded(() -> {
            throw new DocsException("docs/x.md: a problem");
        }, stream(err), Duration.ofSeconds(10));

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("docs/x.md: a problem");
    }

    @Test
    void aRunThatTakesTooLongIsStopped() {
        CountDownLatch never = new CountDownLatch(1);
        int code = DocsTool.guarded(never::await, stream(err), Duration.ofMillis(200));

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("timed out");
    }

    @Test
    void anUnknownCommandFails() {
        assertThat(run(Map.of(), "publish", "--repo", dir.toString())).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("unknown command 'publish'");
    }

    @Test
    void theScanPassesTheRepositorysSiteWithASyntheticDictionaryAndWritesTheSummary() throws IOException {
        Path site = builtSite();
        Path summary = dir.resolve("summary.md");

        int code = run(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY, "GITHUB_SHA", REVISION), "scan",
                "--repo", repo().toString(), "--site", site.toString(), "--summary", summary.toString());

        assertThat(code).as(out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8)).isZero();
        assertThat(summary).content(StandardCharsets.UTF_8).contains("passed", REVISION, "Dictionary: present",
                "BEARER_TOKEN in model/flow-quote-book-label.html");
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("scan: passed");
    }

    @Test
    void theScanFailsClosedWithoutTheDictionary() throws IOException {
        Path site = builtSite();

        int code = run(Map.of(), "scan", "--repo", repo().toString(), "--site", site.toString(), "--summary",
                dir.resolve("summary.md").toString());

        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("scan: DICTIONARY_UNSET");
        assertThat(dir.resolve("summary.md")).content(StandardCharsets.UTF_8).contains("FAILED",
                "Source revision: not set");
    }

    @Test
    void theScanPrintsOnlyTheRuleAndTheFileNeverTheValueOrTheTerm() throws IOException {
        Path site = builtSite();
        String key = "-----BEGIN " + "RSA PRIVATE KEY-----";
        Files.writeString(site.resolve("trust/gaps.html"), "<p>" + key + " synthetic-" + "term-two</p>\n");

        int code = run(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY), "scan", "--repo",
                repo().toString(), "--site", site.toString(), "--summary", dir.resolve("summary.md").toString());

        String printed = out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8)
                + Files.readString(dir.resolve("summary.md"));
        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("scan: DICTIONARY trust/gaps.html",
                "scan: PRIVATE_KEY trust/gaps.html");
        assertThat(printed).doesNotContain(key, "RSA PRIVATE KEY").doesNotContainIgnoringCase("synthetic-term");
    }

    @Test
    void theScanAppendsTheSummaryToTheStepSummaryWhenThereIsOne() throws IOException {
        Path site = builtSite();
        Path stepSummary = dir.resolve("step-summary.md");
        Files.writeString(stepSummary, "## Earlier step\n");

        run(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY, "GITHUB_STEP_SUMMARY", stepSummary.toString()),
                "scan", "--repo", repo().toString(), "--site", site.toString(), "--summary",
                dir.resolve("summary.md").toString());

        assertThat(stepSummary).content(StandardCharsets.UTF_8).startsWith("## Earlier step\n")
                .contains("Publication scan");
    }

    @Test
    void aFindingInAFileNameFailsTheScanAndPrintsTheNameWithheld() throws IOException {
        Path site = builtSite();
        Files.writeString(site.resolve(TERM + ".html"), "<p>clean</p>\n");

        int code = scan(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY));

        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("scan: DICTIONARY [withheld path, sha256 ");
        assertNothingDisclosed();
    }

    @Test
    void aMalformedDictionaryFailsTheScanWithoutPrintingAnyOfIt() throws IOException {
        builtSite();

        int code = scan(Map.of(PublicationScanner.DICTIONARY_VARIABLE, TERM + "\n\u0001"));

        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("scan: DICTIONARY_MALFORMED");
        assertNothingDisclosed();
    }

    @Test
    void anUnreadableFileFailsTheScanWithoutItsNameOrALocalPath() throws IOException {
        Path site = builtSite();
        Path file = site.resolve(TERM + ".html");
        Files.writeString(file, "<p>clean</p>\n");
        Files.setPosixFilePermissions(file, Set.of());
        try {
            assumeThat(Files.isReadable(file)).as("the test user can still read the file").isFalse();

            int code = scan(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY));

            assertThat(code).isEqualTo(1);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("cannot be read");
            assertNothingDisclosed();
        } finally {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void aDirectoryThatCannotBeListedFailsTheScanWithoutItsNameOrALocalPath() throws IOException {
        Path site = builtSite();
        Path directory = site.resolve(TERM);
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("page.html"), "<p>clean</p>\n");
        Files.setPosixFilePermissions(directory, Set.of());
        try {
            assumeThat(Files.isReadable(directory)).as("the test user can still read the directory").isFalse();

            int code = scan(Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY));

            assertThat(code).isEqualTo(1);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("the site cannot be listed");
            assertNothingDisclosed();
        } finally {
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    @Test
    void aScanThatTakesTooLongIsStoppedAndPrintsNothingElse() throws IOException {
        Path site = builtSite();
        Files.writeString(site.resolve(TERM + ".html"), "<p>" + TERM + "</p>\n");

        int code = DocsTool.run(new String[] {"scan", "--repo", repo().toString(), "--site", site.toString(),
            "--summary", dir.resolve("summary.md").toString()}, stream(out), stream(err), Duration.ofMillis(1),
                Map.of(PublicationScanner.DICTIONARY_VARIABLE, DICTIONARY)::get);

        assertThat(code).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("timed out");
        assertNothingDisclosed();
    }

    @Test
    void anUnexpectedFailureOfTheScanPrintsItsKindOnly() {
        String detail = "/Us" + "ers/someone/" + TERM;
        int code = DocsTool.guarded(() -> {
            throw new IllegalStateException(detail);
        }, stream(err), Duration.ofSeconds(10), true);

        assertThat(code).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("IllegalStateException")
                .doesNotContain(detail, "someone", "\tat ");
    }

    private int scan(Map<String, String> environment) {
        return run(environment, "scan", "--repo", repo().toString(), "--site", dir.resolve("site").toString(),
                "--summary", dir.resolve("summary.md").toString());
    }

    /** Nothing printed or written names a dictionary term, the term-named paths or the test's local paths. */
    private void assertNothingDisclosed() throws IOException {
        Path summary = dir.resolve("summary.md");
        String printed = out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8)
                + (Files.exists(summary) ? Files.readString(summary) : "");
        assertThat(printed).doesNotContainIgnoringCase("synthetic-" + "term").doesNotContain(dir.toString());
    }

    private Path repo() {
        return dir.resolve("copy/repo");
    }

    private Path builtSite() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence();
        Path site = dir.resolve("site");
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), site, REVISION);
        return site;
    }

    private int run(Map<String, String> environment, String... args) {
        return DocsTool.run(args, stream(out), stream(err), Duration.ofMinutes(2), environment::get);
    }

    private static PrintStream stream(ByteArrayOutputStream bytes) {
        return new PrintStream(bytes, true, StandardCharsets.UTF_8);
    }
}
