package io.github.stigjohnny.docs.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Allowlist;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * File and directory names are scanned like file contents, and a path whose name fails the scan is never printed: the
 * findings, the summary and the errors show a placeholder with a short SHA-256 of the path instead. Every term, token
 * and address here is assembled at run time.
 */
class ScannedNamesTest {

    private static final String TERM = "synthetic-" + "term-one";
    private static final String DICTIONARY = TERM + "\n" + "synthetic-" + "term-two" + "\n";
    private static final String TOKEN = "gh" + "p_" + "a1B2c3D4e5".repeat(4);
    private static final String KEY_ID = "AK" + "IA" + "QWERTYUIOPASDFGH";
    private static final String ADDRESS = "10." + "20.30.40";
    private static final String CLEAN = "<!DOCTYPE html>\n<p>Nothing to see.</p>\n";

    @TempDir
    Path dir;

    @Test
    void aDictionaryTermInAFileNameIsAFindingAndTheNameIsWithheld() throws IOException {
        write(TERM + ".html", CLEAN);

        ScanResult result = scan();

        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY", withheld(TERM + ".html")));
        assertNothingDisclosed(result, TERM);
    }

    @Test
    void aCredentialShapedFileNameIsAFindingAndTheNameIsWithheld() throws IOException {
        write(TOKEN + ".txt", "clean\n");

        ScanResult result = scan();

        assertThat(result.findings()).containsExactly(new Finding("GITHUB_TOKEN", withheld(TOKEN + ".txt")));
        assertNothingDisclosed(result, TOKEN);
    }

    @Test
    void aDictionaryTermInADirectoryNameWithholdsEveryPathBelowIt() throws IOException {
        write(TERM + "/page.html", "<p>" + ADDRESS + "</p>\n");

        ScanResult result = scan();

        assertThat(result.findings()).containsExactlyInAnyOrder(new Finding("DICTIONARY", withheld(TERM)),
                new Finding("PRIVATE_IP", withheld(TERM + "/page.html")));
        assertNothingDisclosed(result, TERM);
    }

    @Test
    void aCredentialShapedDirectoryNameIsAFindingAndTheNameIsWithheld() throws IOException {
        write(KEY_ID + "/index.html", CLEAN);

        ScanResult result = scan();

        assertThat(result.findings()).containsExactly(new Finding("AWS_ACCESS_KEY_ID", withheld(KEY_ID)));
        assertNothingDisclosed(result, KEY_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"host-01.tail" + "0000.t" + "s.net.html", "synthetic&#45;" + "term-one.html",
        "ｓｙｎｔｈｅｔｉｃ-" + "term-one", "synthe​" + "tic-term-one.md"})
    void aNameIsScannedWithoutItsExtensionAndAfterEntitiesAndNfkc(String name) throws IOException {
        write(name, "clean\n");

        ScanResult result = scan();

        assertThat(result.findings()).singleElement()
                .satisfies(finding -> assertThat(finding.file()).isEqualTo(withheld(name)));
        assertNothingDisclosed(result, name);
    }

    @Test
    void aCleanPathIsStillPrintedAsItIs() throws IOException {
        write("trust/page.html", "<p>" + ADDRESS + "</p>\n");

        assertThat(scan().findings()).containsExactly(new Finding("PRIVATE_IP", "trust/page.html"));
    }

    @Test
    void aStaleExceptionForAWithheldNameIsPrintedWithheld() throws IOException {
        write("index.html", CLEAN);

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.of(
                new ScanException(TERM + ".html", "BEARER_TOKEN", "0".repeat(64), "a demo token")), "");

        assertThat(result.findings()).containsExactly(new Finding("STALE_EXCEPTION", withheld(TERM + ".html")));
        assertNothingDisclosed(result, TERM);
    }

    @Test
    void anUnreadableFileFailsWithoutItsNameOrAnyLocalPath() throws IOException {
        Path file = write(TERM + ".html", CLEAN);
        Files.setPosixFilePermissions(file, Set.of());
        try {
            assumeThat(Files.isReadable(file)).as("the test user can still read the file").isFalse();

            assertThatThrownBy(this::scan).isInstanceOf(DocsException.class)
                    .hasMessageContaining(withheld(TERM + ".html"))
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(TERM, dir.toString()));
        } finally {
            Files.setPosixFilePermissions(file, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        }
    }

    @Test
    void anUnreadableDirectoryFailsWithoutItsNameOrAnyLocalPath() throws IOException {
        write(TERM + "/page.html", CLEAN);
        Path directory = dir.resolve("site").resolve(TERM);
        Files.setPosixFilePermissions(directory, Set.of());
        try {
            assumeThat(Files.isReadable(directory)).as("the test user can still read the directory").isFalse();

            assertThatThrownBy(this::scan).isInstanceOf(DocsException.class)
                    .hasMessageContaining("the site cannot be listed")
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(TERM, dir.toString()));
        } finally {
            Files.setPosixFilePermissions(directory, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
        }
    }

    /** The placeholder of a withheld path: the first 12 hexadecimal digits of the SHA-256 of the relative path. */
    static String withheld(String path) {
        return "[withheld path, sha256 " + Allowlist.sha256(path.getBytes(StandardCharsets.UTF_8)).substring(0, 12)
                + "]";
    }

    private static void assertNothingDisclosed(ScanResult result, String secret) {
        String everything = result.summary() + result.findings() + result.applied();
        assertThat(everything).doesNotContain(secret).doesNotContainIgnoringCase("synthetic-" + "term");
    }

    private ScanResult scan() {
        return PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.none(), "");
    }

    private Path write(String path, String text) throws IOException {
        Path file = dir.resolve("site").resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }
}
