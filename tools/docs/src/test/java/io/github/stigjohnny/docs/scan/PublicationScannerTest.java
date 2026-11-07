package io.github.stigjohnny.docs.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.repo.Allowlist;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.site.SiteBuilder;
import io.github.stigjohnny.docs.testing.Decks;
import io.github.stigjohnny.docs.testing.Demos;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The publication scanner's negative fixtures: one per rule, each a synthetic value that only that rule matches. The
 * values are assembled at run time, so that no secret-shaped string is in this file, and the dictionary is synthetic.
 */
class PublicationScannerTest {

    static final String DICTIONARY = "synthetic-term-one\nsynthetic-term-two\n";
    static final String CLEAN = "<!DOCTYPE html>\n<html lang=\"en\"><head><title>Clean</title></head><body>"
            + "<p>Quote a parcel with Bearer &lt;token&gt;; the scheme is bearer.</p>\n"
            + "<p>Version 10.17.2, 127.0.0.1, 8.8.8.8 and 100.128.0.1 are not private addresses.</p></body></html>\n";

    @TempDir
    Path dir;

    /** One synthetic value per rule; only that rule may match it. */
    static Map<String, String> samples() {
        Map<String, String> samples = new TreeMap<>();
        samples.put("PRIVATE_KEY", "-----BEGIN " + "OPENSSH PRIVATE KEY-----");
        samples.put("GITHUB_TOKEN", "gh" + "p_" + "a1B2c3D4e5".repeat(4));
        samples.put("AWS_ACCESS_KEY_ID", "AK" + "IA" + "QWERTYUIOPASDFGH");
        samples.put("SLACK_TOKEN", "xo" + "xb-" + "1234567890-" + "abcdefghijklmnop");
        samples.put("SLACK_WEBHOOK", "https://hooks.slack" + ".com/services/" + "T0000/B0000/abcdefgh");
        samples.put("DISCORD_WEBHOOK", "https://discord" + ".com/api/webhooks/" + "123456789/abc-DEF_ghi");
        samples.put("GOOGLE_API_KEY", "AI" + "za" + "Sy" + "b".repeat(33));
        samples.put("STRIPE_KEY", "sk" + "_live_" + "0123456789abcdefghij");
        samples.put("NPM_TOKEN", "np" + "m_" + "0123456789abcdefghijklmnopqrstuvwxyz");
        samples.put("JWT", "ey" + "JhbGciOiJIUzI1NiJ9." + "ey" + "JzdWIiOiJzeW50aGV0aWMifQ." + "c2lnbmF0dXJlLXN5bnRoZXRpYw");
        samples.put("BEARER_TOKEN", "Authorization: Bear" + "er synthetic0token0value");
        samples.put("URL_CREDENTIALS", "https://someone:" + "hunter2hunter2" + "@example.com/");
        samples.put("SECRET_ASSIGNMENT", "client_secret = \"" + "s3cr3t-v4lu3-synthetic" + "\"");
        samples.put("PRIVATE_REPOSITORY_LINK", "https://github.com/" + "Stig-Johnny/shipping-reference-platform/pull/7");
        samples.put("LOCAL_PATH", "/Us" + "ers/someone/project");
        samples.put("PRIVATE_IP", "10." + "20.30.40");
        samples.put("INTERNAL_HOSTNAME", "build-01.tail" + "0000.t" + "s.net");
        samples.put("HTML_COMMENT", "<!-- " + "a note -->");
        samples.put("DICTIONARY", "synthetic-term-one");
        return samples;
    }

    static String sample(String rule) {
        return Objects.requireNonNull(samples().get(rule), rule);
    }

    static List<String> rules() {
        return List.copyOf(samples().keySet());
    }

    @Test
    void aCleanSitePasses() throws IOException {
        write("index.html", CLEAN);

        ScanResult result = scan();

        assertThat(result.findings()).isEmpty();
        assertThat(result.passed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("rules")
    void eachRuleFiresOnItsNegativeFixtureAndNoOtherRuleDoes(String rule) throws IOException {
        write("index.html", CLEAN);
        write("rule.html", "<!DOCTYPE html>\n<p>" + sample(rule) + "</p>\n");

        assertThat(scan().findings()).containsExactly(new Finding(rule, "rule.html"));
    }

    @Test
    void everyRuleHasANegativeFixture() {
        List<String> ids = new ArrayList<>(Rule.ids());
        ids.add(PublicationScanner.DICTIONARY_RULE);

        assertThat(samples().keySet()).containsExactlyInAnyOrderElementsOf(ids);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/ho" + "me/someone/x", "/priv" + "ate/tmp/x", "C:\\" + "Users\\someone", "d:\\work"})
    void everyKindOfLocalPathFires(String path) throws IOException {
        write("page.txt", "Built in " + path + ".\n");

        assertThat(scan().findings()).containsExactly(new Finding("LOCAL_PATH", "page.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"172.16.0.1", "172.31.255.254", "192.168.1.10", "100." + "64.0.1", "100." + "100.100.100",
        "100." + "127.255.254", "169.254.10.20", "fd7a:115c:a1e0::1", "fe80::1"})
    void privateLinkLocalAndTailscaleAddressesFire(String address) throws IOException {
        write("page.txt", "Reached at " + address + ".\n");

        assertThat(scan().findings()).containsExactly(new Finding("PRIVATE_IP", "page.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "8.8.8.8", "100.128.0.1", "10.1.2.300", "1.10.20.30.40", "172.32.0.1",
        "version 10.20.30", "0.0.0.0"})
    void publicLoopbackAndVersionLikeNumbersDoNotFire(String text) throws IOException {
        write("page.txt", "See " + text + ".\n");

        assertThat(scan().findings()).isEmpty();
    }

    @Test
    void theRepositorysNameAsPlainTextIsNotALinkToIt() throws IOException {
        String name = "Stig-Johnny/" + "shipping-reference-platform";
        write("index.html", "<!DOCTYPE html>\n<p>The repository is " + name + " on GitHub.</p>\n");

        assertThat(scan().findings()).isEmpty();

        write("index.html", "<!DOCTYPE html>\n<p>The repository is https://github.com/" + name + ".</p>\n");

        assertThat(scan().findings()).containsExactly(new Finding("PRIVATE_REPOSITORY_LINK", "index.html"));
    }

    @Test
    void theDocumentationSitesOwnAddressIsNotALinkToTheRepository() throws IOException {
        write("page.html", "<!DOCTYPE html>\n<p>[Open](https://stig-johnny.github.io/" + "shipping-reference-platform/"
                + "model/system-context.html#figure-system-context)</p>\n");

        assertThat(scan().findings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a bearer credential, sent with bearer authentication", "Authorization: Bearer <token>",
        "scheme: bearer\n  bearerFormat: demo", "the password is required", "secret: \"short\"",
        "a private key never leaves the host", "https://example.com/path"})
    void proseAboutCredentialsDoesNotFire(String text) throws IOException {
        write("page.txt", text + "\n");

        assertThat(scan().findings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SYNTHETIC-TERM-ONE", "synthetic&#45;term&#x2D;one", "synthetic&hyphen;term-one",
        "\uFF53\uFF59\uFF4E\uFF54\uFF48\uFF45\uFF54\uFF49\uFF43-term-one", "synthetic-<em>term</em>-one",
        "synthe\u200Btic-term-one", "synthetic&shy;-term-one", "Synthetic-Term-Two"})
    void theDictionaryMatchesCaseInsensitivelyAfterEntitiesNfkcAndTags(String text) throws IOException {
        write("page.html", "<!DOCTYPE html>\n<p>" + text + "</p>\n");

        assertThat(scan().findings()).containsExactly(new Finding("DICTIONARY", "page.html"));
    }

    @Test
    void aParaphraseIsNotFoundWhichIsALimitOfTheScanner() throws IOException {
        write("page.html", "<!DOCTYPE html>\n<p>the first synthetic term</p>\n");

        assertThat(scan().findings()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n", "  \n \r\n"})
    void anEmptyDictionaryFailsClosed(String dictionary) throws IOException {
        write("index.html", CLEAN);

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), dictionary, ScanExceptions.none(), "");

        assertThat(result.passed()).isFalse();
        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY_UNSET", ""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0001", "synthetic-" + "term-one\n\u0001", "\u0001\u0002\n\u0003", "synthetic\u0000term",
        "synthetic\tterm", "synthetic\u0085term", "\u200B", "\u200B\n\u00AD"})
    void aDictionaryWithAControlCharacterOrNoValidEntryIsMalformedAndFailsClosed(String dictionary)
            throws IOException {
        write("index.html", CLEAN);

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), dictionary, ScanExceptions.none(), "");

        assertThat(result.passed()).isFalse();
        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY_MALFORMED", ""));
        assertThat(result.summary()).contains("Dictionary: malformed").doesNotContain("synthetic");
    }

    @Test
    void aMalformedDictionaryStillScansWithItsValidEntries() throws IOException {
        write("page.html", "<p>" + "synthetic-" + "term-one</p>");

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), "synthetic-" + "term-one\n\u0007",
                ScanExceptions.none(), "");

        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY_MALFORMED", ""),
                new Finding("DICTIONARY", "page.html"));
    }

    @Test
    void anUnsetDictionaryFailsClosed() throws IOException {
        write("index.html", CLEAN);

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), null, ScanExceptions.none(), "");

        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY_UNSET", ""));
    }

    @Test
    void theOtherRulesStillRunWhenTheDictionaryIsUnset() throws IOException {
        write("rule.html", "<p>" + sample("PRIVATE_IP") + "</p>");

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), null, ScanExceptions.none(), "");

        assertThat(result.findings()).containsExactly(new Finding("DICTIONARY_UNSET", ""),
                new Finding("PRIVATE_IP", "rule.html"));
    }

    @Test
    void anExactExceptionSuppressesOnlyThatValueInThatFileForThatRule() throws IOException {
        String token = sample("BEARER_TOKEN").substring("Authorization: ".length());
        write("a.html", "<p>" + token + " and Bearer other0synthetic0value</p>");
        write("b.html", "<p>" + token + "</p>");

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.of(
                new ScanException("a.html", "BEARER_TOKEN", Allowlist.sha256(utf8(token)), "a public demo token")),
                "");

        assertThat(result.findings()).containsExactly(new Finding("BEARER_TOKEN", "a.html"),
                new Finding("BEARER_TOKEN", "b.html"));
        assertThat(result.applied()).containsExactly(
                new ScanException("a.html", "BEARER_TOKEN", Allowlist.sha256(utf8(token)), "a public demo token"));
    }

    @Test
    void aMatchingExceptionLetsTheFilePass() throws IOException {
        String token = sample("BEARER_TOKEN").substring("Authorization: ".length());
        write("a.html", "<p>" + token + "</p>");

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.of(
                new ScanException("a.html", "BEARER_TOKEN", Allowlist.sha256(utf8(token)), "a public demo token")),
                "");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void anExceptionThatNoLongerMatchesIsAFinding() throws IOException {
        write("a.html", CLEAN);

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.of(
                new ScanException("a.html", "BEARER_TOKEN", "0".repeat(64), "a public demo token")), "");

        assertThat(result.findings()).containsExactly(new Finding("STALE_EXCEPTION", "a.html"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRIVATE_KEY", "DICTIONARY", "PRIVATE_REPOSITORY_LINK", "LOCAL_PATH", "PRIVATE_IP",
        "NO_SUCH_RULE"})
    void exceptionsAreForTheDemoTokensRuleOnly(String rule) {
        String json = "{\"exceptions\": [{\"file\": \"a.html\", \"rule\": \"" + rule + "\", \"sha256\": \""
                + "0".repeat(64) + "\", \"reason\": \"x\"}]}";

        assertThatThrownBy(() -> ScanExceptions.parse(json, "exceptions.json")).isInstanceOf(DocsException.class)
                .hasMessageContaining("only for BEARER_TOKEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"exceptions\": [{\"file\": \"a.html\", \"rule\": \"BEARER_TOKEN\", \"sha256\": \"abc\", \"reason\": \"x\"}]}",
        "{\"exceptions\": [{\"file\": \"a.html\", \"rule\": \"BEARER_TOKEN\", \"sha256\": \"%s\", \"reason\": \" \"}]}",
        "{\"exceptions\": [{\"file\": \"../a.html\", \"rule\": \"BEARER_TOKEN\", \"sha256\": \"%s\", \"reason\": \"x\"}]}",
        "{\"exceptions\": [{\"rule\": \"BEARER_TOKEN\", \"sha256\": \"%s\", \"reason\": \"x\"}]}",
        "{\"exception\": []}"})
    void aMalformedExceptionsFileFails(String json) {
        assertThatThrownBy(() -> ScanExceptions.parse(json.formatted("0".repeat(64)), "exceptions.json"))
                .isInstanceOf(DocsException.class).hasMessageContaining("exceptions.json");
    }

    @Test
    void theScanCoversTheDemonstrationsPagesAsBuilt() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence().withoutDemos();
        for (String kind : List.of("gate", "visual", "docs", "build", "feature")) {
            fixture.write(Demos.path(kind), Demos.text(kind));
        }
        ScanExceptions exceptions = ScanExceptions.parse(Sources.text(fixture.root(), PublicationScanner.EXCEPTIONS),
                PublicationScanner.EXCEPTIONS);

        assertThat(scanBuiltSite(fixture, exceptions).findings()).isEmpty();

        // Recorded output is published as it was pasted, so the scan is what keeps a leaked value off the site.
        fixture.edit(Demos.path("gate"), text -> text.replace("## Dependency allowlist: clean", "## Dependency "
                + "allowlist: clean on " + sample("PRIVATE_IP") + " for " + sample("DICTIONARY") + " in "
                + sample("LOCAL_PATH")));

        assertThat(scanBuiltSite(fixture, exceptions).findings()).containsExactly(
                new Finding("DICTIONARY", "demos/42-gate.html"), new Finding("LOCAL_PATH", "demos/42-gate.html"),
                new Finding("PRIVATE_IP", "demos/42-gate.html"));
    }

    @Test
    void theScanCoversTheDecksSlidesAndNotesAsBuilt() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence().withoutDemos()
                .write(Decks.PATH, Decks.SAMPLE);
        ScanExceptions exceptions = ScanExceptions.parse(Sources.text(fixture.root(), PublicationScanner.EXCEPTIONS),
                PublicationScanner.EXCEPTIONS);

        assertThat(scanBuiltSite(fixture, exceptions).findings()).isEmpty();

        // A slide and a note are published as written, so the scan is what keeps a leaked value off both pages.
        fixture.edit(Decks.PATH, text -> text.replace("Java 25 and strict builds.", "Java 25 on "
                + sample("PRIVATE_IP") + ".").replace("- The numbers come from the sources", "- The numbers from "
                + sample("DICTIONARY") + " in " + sample("LOCAL_PATH")));

        assertThat(scanBuiltSite(fixture, exceptions).findings()).containsExactly(
                new Finding("DICTIONARY", "demos/deck-milestone-notes.html"),
                new Finding("LOCAL_PATH", "demos/deck-milestone-notes.html"),
                new Finding("PRIVATE_IP", "demos/deck-milestone.html"));
    }

    @Test
    void theRepositorysOwnDeckPassesTheScanRules() {
        Fixture fixture = Fixture.copy(dir.resolve("copy")).passingEvidence();
        ScanExceptions exceptions = ScanExceptions.parse(Sources.text(fixture.root(), PublicationScanner.EXCEPTIONS),
                PublicationScanner.EXCEPTIONS);

        ScanResult result = scanBuiltSite(fixture, exceptions);

        assertThat(dir.resolve("site/demos/deck-milestone.html")).exists();
        assertThat(dir.resolve("site/demos/deck-milestone-notes.html")).exists();
        assertThat(result.findings()).isEmpty();
    }

    private ScanResult scanBuiltSite(Fixture fixture, ScanExceptions exceptions) {
        SiteBuilder.build(Sources.read(fixture.root(), List.of(fixture.reports())), dir.resolve("site"),
                "0123456789abcdef0123456789abcdef01234567");
        return PublicationScanner.scan(dir.resolve("site"), DICTIONARY, exceptions, "");
    }

    @Test
    void aSymbolicLinkInTheSiteIsAFinding() throws IOException {
        write("index.html", CLEAN);
        Files.createSymbolicLink(dir.resolve("site/link.html"), dir.resolve("site/index.html"));

        assertThat(scan().findings()).containsExactly(new Finding("NOT_A_REGULAR_FILE", "link.html"));
    }

    @Test
    void aFileThatIsNotUtf8IsAFinding() throws IOException {
        write("index.html", CLEAN);
        Files.write(dir.resolve("site/image.bin"), new byte[] {(byte) 0xC3, (byte) 0x28, 0, (byte) 0xFF});

        assertThat(scan().findings()).containsExactly(new Finding("UNREADABLE_TEXT", "image.bin"));
    }

    @Test
    void aMissingSiteFails() {
        assertThatThrownBy(() -> PublicationScanner.scan(dir.resolve("none"), DICTIONARY, ScanExceptions.none(), ""))
                .isInstanceOf(DocsException.class).hasMessageContaining("no site to scan");
    }

    @Test
    void theSummaryBindsTheResultToTheSiteDigestAndTheRevision() throws IOException {
        write("index.html", CLEAN);
        write("assets/site.css", "body {}\n");
        String manifest = Allowlist.sha256(utf8("body {}\n")) + "  ./assets/site.css\n"
                + Allowlist.sha256(utf8(CLEAN)) + "  ./index.html\n";

        ScanResult result = PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.none(),
                "0123456789abcdef0123456789abcdef01234567");

        assertThat(result.siteDigest()).isEqualTo(Allowlist.sha256(utf8(manifest)));
        assertThat(result.files()).isEqualTo(2);
        assertThat(result.summary()).contains("passed", "2 files", result.siteDigest(),
                "0123456789abcdef0123456789abcdef01234567", "Dictionary: present");
    }

    @Test
    void aLocalScanSaysTheRevisionIsNotSet() throws IOException {
        write("index.html", CLEAN);

        assertThat(scan().summary()).contains("Source revision: not set");
    }

    @Test
    void neitherTheSummaryNorTheFindingsContainAMatchedValueOrATerm() throws IOException {
        int n = 0;
        for (String sample : samples().values()) {
            write("page" + n++ + ".html", "<p>" + sample + "</p>");
        }

        ScanResult result = scan();

        assertThat(result.findings()).hasSize(samples().size());
        String everything = result.summary() + result.findings();
        for (String sample : samples().values()) {
            assertThat(everything).doesNotContain(sample);
        }
        assertThat(everything).doesNotContainIgnoringCase("synthetic-term");
    }

    private ScanResult scan() {
        return PublicationScanner.scan(dir.resolve("site"), DICTIONARY, ScanExceptions.none(), "");
    }

    private void write(String path, String text) throws IOException {
        Path file = dir.resolve("site").resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
