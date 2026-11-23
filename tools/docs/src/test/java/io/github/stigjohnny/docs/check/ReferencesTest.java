package io.github.stigjohnny.docs.check;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.generate.GeneratedFiles;
import io.github.stigjohnny.docs.repo.Sources;
import io.github.stigjohnny.docs.testing.Fixture;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The inline references' negative fixtures (ADR 0023): each writes one reference into a copy of the repository and
 * expects the check to name the file, the line and the rule. A bare test name must name a test class; a planned one
 * carries the planned marker and is registered as planned; records, generated files and code blocks are not read.
 */
class ReferencesTest {

    private static final String NOTES = "tools/notes/README.md";

    @TempDir
    Path dir;

    @Test
    void theRepositoryPasses() {
        assertThat(problems(Fixture.copy(dir).passingEvidence())).isEmpty();
    }

    @Test
    void aBareNameOfNoTestClassFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `RenamedAwayTest`.\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `RenamedAwayTest` names no test class in the "
                + "repository; if it is planned, write planned:test:RenamedAwayTest (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"BrainCheckTest", "PolicyCheckTest", "LoopbackPortCheckTest", "ProviderIdentityTest"})
    void aBareNameOfAnExistingTestClassPasses(String test) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + test + "` exists.\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void aBareNameOfARegisteredPlannedTestFailsBecauseItReadsAsCurrent() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`CrossTenantAccessTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `CrossTenantAccessTest` names no test class in the "
                + "repository; if it is planned, write planned:test:CrossTenantAccessTest (ADR 0023)");
    }

    @Test
    void aClassIsKnownByItsDeclaredNameNotByItsFileName() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("tools/notes/src/test/java/io/example/OriginalTest.java", "class ReplacementTest {\n}\n")
                .write("tools/notes/EmptyTest.java", "")
                .write("tools/notes/src/test/java/io/example/CommentedTest.java", """
                        // class InCommentTest
                        /* record InBlockTest() */
                        class CommentedTest {
                            String text = "class InStringTest";
                        }
                        """)
                .write(NOTES, "# Notes\n\n`ReplacementTest` `OriginalTest` `EmptyTest` `CommentedTest` `InCommentTest` "
                        + "`InBlockTest` `InStringTest`\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":3: `EmptyTest`", NOTES + ":3: `InBlockTest`", NOTES + ":3: `InCommentTest`",
                        NOTES + ":3: `InStringTest`", NOTES + ":3: `OriginalTest`");
    }

    @Test
    void testClassesAreReadFromTestSourcesAndStandaloneTestFilesOnly() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write("tools/notes/src/test/java/io/example/Helpers.java", "class ExtraTest {\n}\n")
                .write("tools/notes/src/main/java/io/example/Main.java", "class MainOnlyTest {\n}\n")
                .write(NOTES, "# Notes\n\n`ExtraTest` `MainOnlyTest`\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":3: `MainOnlyTest`");
    }

    @Test
    void theFixtureCopiesEveryFileOutsideTheDirectoriesTheChecksSkip() throws IOException {
        Path repository = dir.resolve("repository");
        List<String> copied = List.of("x/src/test/java/io/example/Helpers.java",
                "y/src/providerIdentityTest/java/io/example/Other.java", "z/StandaloneTest.java",
                "w/src/main/java/io/example/Main.java", "w/src/main/java/io/example/build/Plan.java",
                "apps/web/src/ui/button.ts", "tools/ci/stage.sh");
        List<String> skipped = List.of("build/notes.md", "apps/web/build/out.js", "apps/web/node_modules/pkg/index.js",
                ".gradle/cache.bin", ".git/config", ".claude/worktrees/x/README.md");
        for (String file : copied) {
            write(repository.resolve(file), "class X {\n}\n");
        }
        for (String file : skipped) {
            write(repository.resolve(file), "x\n");
        }

        Fixture fixture = Fixture.copy(dir.resolve("copy"), repository);

        assertThat(copied).allSatisfy(file -> assertThat(fixture.root().resolve(file)).exists());
        assertThat(skipped).allSatisfy(file -> assertThat(fixture.root().resolve(file)).doesNotExist());
    }

    @Test
    void aShippedEntryRegistersNoPlannedTest() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/capabilities/capabilities.yaml", text -> text + """

                          - id: control.shipped-only
                            title: A shipped control.
                            where: tools/docs
                            check: java
                            status: shipped
                            target: v0.1
                            evidence: [ShippedOnlyTest]
                            evidenceLabel: none
                            acceptanceTests: [ShippedOnlyTest]
                            negativeFixture: none.
                            docs: docs/gaps.md
                        """)
                .write(NOTES, "# Notes\n\n`planned:test:ShippedOnlyTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:ShippedOnlyTest`: ShippedOnlyTest is "
                + "not registered as planned; list it in the acceptanceTests of a capability or control that is not "
                + "shipped, or in an ADR's plannedEnforcement (ADR 0023)");
    }

    @Test
    void frontmatterWithWindowsLineEndingsIsNotReadAndLinesStayPhysical() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES,
                "---\r\ntitle: \"The `GhostMetaTest`\"\r\n---\r\n\r\nSee `RenamedAwayTest`.\r\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":5: `RenamedAwayTest` names no test class in the "
                + "repository; if it is planned, write planned:test:RenamedAwayTest (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnATestThatExistsFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:BrainCheckTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:BrainCheckTest`: BrainCheckTest "
                + "exists, so name it without planned: (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnAnUnregisteredTestFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:NobodyPlannedTest`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:test:NobodyPlannedTest`: "
                + "NobodyPlannedTest is not registered as planned; list it in the acceptanceTests of a capability or "
                + "control that is not shipped, or in an ADR's plannedEnforcement (ADR 0023)");
    }

    @Test
    void aPlannedMarkerOnARegisteredTestPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`planned:test:CrossTenantAccessTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void anAdrsPlannedEnforcementRegistersAPlannedTest() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/adr/0022-agent-entry-point-and-documentation-lookup.md", text -> text.replace(
                        "plannedEnforcement:\n", "plannedEnforcement:\n  - \"java: FutureOnlyTest\"\n"))
                .write(NOTES, "# Notes\n\n`planned:test:FutureOnlyTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:test:", "planned:test:lowercaseTest", "planned:test:BrainCheckTest extra",
        "planned:tests:CrossTenantAccessTest", "planned:paths:policy/rulesets.json", "absent:test:GhostTest",
        "illustrative:test:SomeTest", "planned:", "planned:cap:quotes.create-quote", "absent:cap:quotes.create-quote"})
    void aMalformedOrNotYetDefinedMarkerFails(String marker) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + marker + "`\n");

        assertThat(problems(fixture)).singleElement().asString().startsWith(NOTES + ":3: `" + marker + "` ")
                .endsWith("(ADR 0023)");
    }

    @Test
    void anIllustrativeMarkerWithAPlaceholderPasses() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`illustrative:test:<TestClass>`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"cap:quotes.create-quote", "cap:web.accessibility", "cap:control.migrations",
        "cap:control.inline-references"})
    void aCapMarkerOnAKnownCapabilityOrControlPasses(String marker) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + marker + "`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"quotes.make-quote", "web.accessibilty", "invoices.make-invoice"})
    void aCapMarkerOnAnIdThatTheRegisterLacksFails(String id) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`cap:" + id + "`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `cap:" + id + "`: " + id + " is no capability or "
                + "control in docs/capabilities/capabilities.yaml (ADR 0023)");
    }

    @Test
    void aPrefixNewToTheRegisterMakesItsUnknownIdsFail() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .edit("docs/capabilities/capabilities.yaml", text -> text + """

                          - id: invoices.make-invoice
                            title: A control under a new prefix.
                            where: tools/docs
                            check: java
                            status: planned
                            target: v0.1
                            evidence: none yet
                            evidenceLabel: none
                            acceptanceTests: [InvoiceTest]
                            negativeFixture: none.
                            docs: docs/gaps.md
                        """)
                .write(NOTES, "# Notes\n\n`cap:invoices.make-invoice` `invoices.send-invoice`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `invoices.send-invoice` has the shape of a "
                + "capability or control id, but no entry in docs/capabilities/capabilities.yaml has it (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cap:", "cap:quotes.create-quote extra", "cap: quotes.create-quote"})
    void aMalformedCapMarkerFails(String marker) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + marker + "`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `" + marker + "` needs one capability or control "
                + "id, such as cap:control.inline-references (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"quotes.create-quote", "web.accessibility", "control.inline-references"})
    void aBareKnownIdFailsBecauseItMustBeMarked(String id) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + id + "`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `" + id + "` names a capability or control; write "
                + "cap:" + id + " (ADR 0023)");
    }

    @Test
    void aBareHyphenatedTokenShapedLikeAnUnknownIdFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`quotes.make-quote`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `quotes.make-quote` has the shape of a capability "
                + "or control id, but no entry in docs/capabilities/capabilities.yaml has it (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenants.tenant", "web.packages", "quotes.md", "orders.import-webshop-orders.yaml",
        "invoices.make-invoice"})
    void tokensThatAreNoCapabilityIdAreIgnored(String token) {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`" + token + "`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    private static final String ROOT = "from the repository's root";
    private static final String BESIDE = "from tools/notes/, this document's directory";

    private List<String> problemsFor(String span) {
        return problems(Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `" + span + "`.\n"));
    }

    private static String missing(String path, String from) {
        return NOTES + ":3: `" + path + "` names no file or directory " + from + "; if it is planned, write "
                + "planned:path:" + path + ", and if it is meant to be absent, absent:path:" + path + " (ADR 0023)";
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/gaps.md", "docs/", "docs/adr/", ".github/workflows/pages.yml",
        "apps/shipping-web/src/api/types.ts", "tools/docs/src/main/java/io/github/stigjohnny/docs/check/References.java",
        "apps/shipping-web/src", "/architecture/", "/policy/risk.json", "/docs/demos/how-to.md"})
    void aPathThatExistsPasses(String path) {
        assertThat(problemsFor(path)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"policy/rulesets.json", "tools/web-imports/check-imports.mjs",
        "/security/allowlist.schema.json", "apps/shipping-web/e2e"})
    void aPathThatDoesNotExistFailsAndNamesTheMarkers(String path) {
        assertThat(problemsFor(path)).containsExactly(missing(path, ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tools/web-imports/", "docs/gaps.md/"})
    void aTrailingSlashNamesADirectory(String path) {
        assertThat(problemsFor(path)).containsExactly(NOTES + ":3: `" + path + "` names no directory " + ROOT
                + "; if it is planned, write planned:path:" + path + ", and if it is meant to be absent, absent:path:"
                + path + " (ADR 0023)");
    }

    @Test
    void aDirectoryExistsOnlyWhenItHoldsAFile() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `tools/notes/empty/`.\n");
        Files.createDirectories(fixture.root().resolve("tools/notes/empty"));

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `tools/notes/empty/` names no directory " + ROOT
                + "; if it is planned, write planned:path:tools/notes/empty/, and if it is meant to be absent, "
                + "absent:path:tools/notes/empty/ (ADR 0023)");
    }

    @ParameterizedTest
    @CsvSource({"Docs/gaps.md, docs/gaps.md", "docs/ADR/, docs/adr/", "docs/GAPS.md, docs/gaps.md",
        "/Policy/risk.json, policy/risk.json", "DOCS/Brain/<module>.md, docs/brain/"})
    void aPathThatDiffersOnlyInLetterCaseFails(String path, String existing) {
        String pattern = path.contains("<") ? "is a pattern whose directory DOCS/Brain/ " : "";

        assertThat(problemsFor(path)).containsExactly(NOTES + ":3: `" + path + "` " + pattern + "differs only in "
                + "letter case from " + existing + ", which exists (ADR 0023)");
    }

    @Test
    void aPathWhoseFirstNameIsADirectoryBesideTheDocumentIsReadFromThere() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/src/main.ts", "x\n")
                .write(NOTES, "# Notes\n\n`src/main.ts`, `./src/main.ts`, `src/` and `src/other.ts`\n");

        assertThat(problems(fixture)).containsExactly(missing("src/other.ts", BESIDE));
    }

    @Test
    void aPathWithTheDocumentAnchorIsAlwaysRead() {
        assertThat(problemsFor("./docs-search")).containsExactly(missing("./docs-search", BESIDE));
    }

    @Test
    void aPathThatOnlyTheDocumentsDirectoryHasMustSaySo() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/architecture/self.dsl", "x\n")
                .write(NOTES, "# Notes\n\n`architecture/self.dsl` and `./architecture/self.dsl`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `architecture/self.dsl` names no file or directory "
                + ROOT + ", but does " + BESIDE + ": write ./architecture/self.dsl (ADR 0023)");
    }

    @Test
    void aPathThatBothReadingsFindIsAmbiguousAndFails() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/policy/risk.json", "{}\n")
                .write(NOTES, "# Notes\n\n`policy/risk.json`, `/policy/risk.json`, `./policy/risk.json` and "
                        + "`policy/*.json`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `policy/*.json` is a pattern whose directory policy/ "
                + "exists both " + ROOT + " and " + BESIDE + ": write /policy/*.json for the first or ./policy/*.json "
                + "for the second (ADR 0023)", NOTES + ":3: `policy/risk.json` names a file or directory both " + ROOT
                + " and " + BESIDE + ": write /policy/risk.json for the first or ./policy/risk.json for the second "
                + "(ADR 0023)");
    }

    @Test
    void aDocumentAtTheRootHasOneReading() {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        int line = appendLine(fixture, "README.md", "See `policy/risk.json`, `./policy/risk.json` and "
                + "`policy/rulesets.json`.");

        assertThat(problems(fixture)).containsExactly("README.md:" + line + ": `policy/rulesets.json` names no file or "
                + "directory " + ROOT + "; if it is planned, write planned:path:policy/rulesets.json, and if it is "
                + "meant to be absent, absent:path:policy/rulesets.json (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"apps/shipping-web/dist/assets/app.js", "tools/docs/build/site/", "build/reports/c4-contract/",
        "apps/shipping-web/node_modules/.bin", "tools/.gradle/caches", "docs/.claude/rules/", ".git/hooks/",
        "tools/docs/build/site/demos/<pr>-<slug>.html", "dist/assets"})
    void buildOutputAndToolStateAreNeverReadAsPathsWhetherOrNotTheyExist(String path) {
        assertThat(problemsFor(path)).isEmpty();
        Fixture built = Fixture.copy(dir.resolve("built")).passingEvidence()
                .write("apps/shipping-web/dist/assets/app.js", "x\n").write("docs/.claude/rules/x.txt", "x\n")
                .write(NOTES, "# Notes\n\nSee `" + path + "`.\n");
        assertThat(problems(built)).isEmpty();
    }

    @Test
    void aBuildDirectoryInsideSourcesIsNoOutput() {
        String sources = "tools/notes/src/main/java/io/example/build/";
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(sources + "Plan.java", "class Plan {\n}\n")
                .write(NOTES, "# Notes\n\n`" + sources + "Plan.java` and `" + sources + "Gone.java`\n");

        assertThat(problems(fixture)).containsExactly(missing(sources + "Gone.java", ROOT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs", "architecture", "/api/v1/quotes", "/quotes/new", "application/json",
        "demos/how-to.html", "actions/checkout", "security/allowlist.json:1:3: JSON expected", "docs/gaps.md#section",
        "docs/../README.md", "src/api", "packages/shipping-client/", "docs gaps.md", "docs//gaps.md", "./gradlew check",
        "io/github/stigjohnny", "/", "//", "docs/<a<b>>.md", "docs/<>.md", "docs/<open.md"})
    void spansThatAreNoPathsAreNotRead(String span) {
        assertThat(problemsFor(span)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/modules/<module>.md", "docs/brain/**", "docs/demos/*.md",
        "security/reviews/<kind>--<name>--<version>.md", "architecture/scenarios/<view key in kebab case>.json",
        "docs/adr/*.never"})
    void aPatternWhoseDirectoryExistsPassesWhetherOrNotAFileMatches(String pattern) {
        assertThat(problemsFor(pattern)).isEmpty();
    }

    @Test
    void aPatternWhoseDirectoryIsMissingFails() {
        assertThat(problemsFor("docs/nothing/<module>.md")).containsExactly(NOTES + ":3: `docs/nothing/<module>.md` is "
                + "a pattern whose directory docs/nothing/ does not exist " + ROOT + "; if it is planned, write "
                + "planned:path:docs/nothing/<module>.md, and if it is meant to be absent, "
                + "absent:path:docs/nothing/<module>.md (ADR 0023)");
    }

    @Test
    void pathsAreReadInTablesHeadingsAndLinkTextWithTheLinesOfTheFile() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, """
                # Notes `docs/one.md`

                | Path |
                |---|
                | `docs/two.md` |

                [`docs/three.md`](https://example.com/)
                """);

        assertThat(problems(fixture)).containsExactly(missing("docs/one.md", ROOT).replace(":3:", ":1:"),
                missing("docs/two.md", ROOT).replace(":3:", ":5:"), missing("docs/three.md", ROOT).replace(":3:", ":7:"));
    }

    @Test
    void aLinkOnThePathFailsInsteadOfCountingAsAbsent() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`tools/notes/linked/gaps.md` and `planned:path:tools/notes/linked/x.md`\n");
        Files.createSymbolicLink(fixture.root().resolve("tools/notes/linked"), fixture.root().resolve("docs"));

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `planned:path:tools/notes/linked/x.md`: "
                + "tools/notes/linked/x.md reaches tools/notes/linked, a link or special file, which the check does not "
                + "follow (ADR 0023)", NOTES + ":3: `tools/notes/linked/gaps.md` reaches tools/notes/linked, a link or "
                + "special file, which the check does not follow (ADR 0023)");
    }

    @Test
    void aLinkInAnotherLetterCaseFailsAsALinkAndNeverCountsAsAbsent() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`absent:path:tools/notes/LINKED`, "
                + "`planned:path:tools/notes/Linked/x.md` and `tools/notes/LINKED/gaps.md`\n");
        Files.createSymbolicLink(fixture.root().resolve("tools/notes/linked"), fixture.root().resolve("docs"));
        String link = " reaches tools/notes/linked, a link or special file, which the check does not follow (ADR 0023)";

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `absent:path:tools/notes/LINKED`: tools/notes/LINKED"
                + link, NOTES + ":3: `planned:path:tools/notes/Linked/x.md`: tools/notes/Linked/x.md" + link,
                NOTES + ":3: `tools/notes/LINKED/gaps.md`" + link);
    }

    @Test
    void aDirectoryThatCannotBeReadStopsTheCheck() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/locked/x.md", "x\n")
                .write(NOTES, "# Notes\n\nSee `planned:path:tools/notes/locked/y.md`.\n");
        Path locked = fixture.root().resolve("tools/notes/locked");
        Set<PosixFilePermission> before = Files.getPosixFilePermissions(locked);
        Files.setPosixFilePermissions(locked, Set.of());
        try {
            // Whichever walk meets it first stops the check: it never counts as absent.
            assertThatThrownBy(() -> problems(fixture)).isInstanceOfAny(DocsException.class,
                    UncheckedIOException.class);
        } finally {
            Files.setPosixFilePermissions(locked, before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:path:policy/rulesets.json", "planned:path:/policy/rulesets.json",
        "absent:path:tools/web-imports/", "absent:path:security/allowlist.schema.json", "planned:path:CHANGELOG.md",
        "planned:path:apps/shipping-api/src/main/java/io/github/stigjohnny/shipping/audit/**",
        "planned:path:packages/shipping-client/", "planned:path:./e2e/", "planned:path:./src/",
        "illustrative:path:docs/adr/<NNNN>-<title>.md", "illustrative:path:packages/<name>/src/",
        "illustrative:path:<module>/README.md"})
    void aPathMarkerThatHoldsPasses(String marker) {
        assertThat(problemsFor(marker)).isEmpty();
    }

    @Test
    void aPlannedPathThatExistsFails() {
        assertThat(problemsFor("planned:path:docs/gaps.md")).containsExactly(NOTES + ":3: `planned:path:docs/gaps.md`: "
                + "docs/gaps.md exists " + ROOT + ", so name it without planned: (ADR 0023)");
    }

    @Test
    void anAbsentPatternWhoseDirectoryExistsFailsAndNamesTheDirectory() {
        assertThat(problemsFor("absent:path:docs/brain/**")).containsExactly(NOTES + ":3: `absent:path:docs/brain/**`: "
                + "its directory docs/brain/ exists " + ROOT + ", so it is not absent (ADR 0023)");
    }

    @Test
    void aMarkedPathMustBeMissingFromBothReadingsAndInEveryLetterCase() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/architecture/self.dsl", "x\n")
                .write(NOTES, "# Notes\n\n`planned:path:architecture/self.dsl`, `planned:path:/architecture/self.dsl` "
                        + "and `absent:path:docs/GAPS.md`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `absent:path:docs/GAPS.md`: docs/GAPS.md differs "
                + "only in letter case from docs/gaps.md, which exists (ADR 0023)", NOTES + ":3: "
                + "`planned:path:architecture/self.dsl`: architecture/self.dsl exists " + BESIDE + ", so name it without "
                + "planned: (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:path:apps/shipping-web/dist/index.html", "absent:path:tools/docs/build/site/",
        "planned:path:.claude/rules/", "absent:path:build/reports/"})
    void aMarkerOnBuildOutputOrToolStateFails(String marker) {
        String path = marker.substring(marker.indexOf(":path:") + ":path:".length());

        assertThat(problemsFor(marker)).containsExactly(NOTES + ":3: `" + marker + "`: " + path + " is build output or "
                + "tool state, which no check reads; write it without a marker (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent:path:tools/docs/BUILD/site/", "planned:path:apps/shipping-web/Dist/index.html",
        "absent:path:docs/.Claude/rules/", "planned:path:Node_Modules/x/"})
    void aMarkerOnBuildOutputOrToolStateInAnotherLetterCaseFails(String marker) {
        String path = marker.substring(marker.indexOf(":path:") + ":path:".length());

        assertThat(problemsFor(marker)).containsExactly(NOTES + ":3: `" + marker + "`: " + path + " is build output or "
                + "tool state, which no check reads; write it without a marker (ADR 0023)");
    }

    @Test
    void aBuildDirectoryInsideSourcesInAnyLetterCaseIsNoOutputForAMarker() {
        assertThat(problemsFor("planned:path:tools/notes/src/main/java/io/example/BUILD/Next.java")).isEmpty();
    }

    @Test
    void anIllustrativePathNeedsAPlaceholder() {
        assertThat(problemsFor("illustrative:path:docs/adr/0001.md")).containsExactly(NOTES + ":3: "
                + "`illustrative:path:docs/adr/0001.md` needs a placeholder in angle brackets, such as "
                + "illustrative:path:docs/adr/<NNNN>-<title>.md (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"planned:path:", "planned:path:two words", "absent:path:../outside", "planned:path:docs//x",
        "illustrative:path:", "absent:path:docs/gaps.md#top", "planned:path:/", "planned:path:.//x",
        "illustrative:path:docs/<a<b>>.md", "illustrative:path:docs/<>.md", "illustrative:path:docs/< >.md",
        "illustrative:path:docs/<open.md", "illustrative:path:docs/<a/b>.md", "planned:path:**/x.md",
        "absent:path:<module>/x.md", "planned:path:docs/a+b.md"})
    void aMalformedPathMarkerFails(String marker) {
        assertThat(problemsFor(marker)).containsExactly(NOTES + ":3: `" + marker + "` needs one path, such as "
                + "planned:path:policy/rulesets.json (ADR 0023)");
    }

    @Test
    void pathsInCodeBlocksAndRecordsAreNotRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n```text\n`policy/rulesets.json`\n```\n");
        appendLine(fixture, "docs/contributions/history.md", "See `policy/rulesets.json` and `planned:path:docs/gaps.md`.");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void codeBlocksAreNotRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n```text\n`GhostTest`\n```\n\n    `IndentedGhostTest`\n");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void tablesDoubleBackticksAndSeveralSpansOnALineAreRead() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES,
                "# Notes\n\n| Test |\n|---|\n| `TableGhostTest` |\n\n``DoubleGhostTest`` and `SecondGhostTest`.\n");

        assertThat(problems(fixture)).extracting(problem -> problem.substring(0, problem.indexOf(" names")))
                .containsExactly(NOTES + ":5: `TableGhostTest`", NOTES + ":7: `DoubleGhostTest`",
                        NOTES + ":7: `SecondGhostTest`");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/demos/how-to.md", "libs/c4-contract-kit/README.md", "apps/shipping-web/README.md",
        "README.md", "AGENTS.md", "docs/adr/0022-agent-entry-point-and-documentation-lookup.md"})
    void aCurrentDocumentAnywhereIsReadWithTheLinesOfItsFile(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        int line = appendLine(fixture, path, "See `GhostTest`.");

        assertThat(problems(fixture)).containsExactly(path + ":" + line + ": `GhostTest` names no test class in the "
                + "repository; if it is planned, write planned:test:GhostTest (ADR 0023)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"docs/contributions/history.md", "docs/demos/142-claude-md-pairs-are-checked.md",
        "security/reviews/maven--pgjdbc--42.7.14.md", "security/verifications/2026-11-06-toolchain.md",
        "docs/adr/0010-documentation-as-code-with-a-generated-brain.md", "BRAIN.md", "docs/brain/quotes.md",
        "docs/architecture/containers.md", "build/notes.md", "apps/shipping-web/node_modules/pkg/README.md"})
    void recordsSupersededDecisionsGeneratedFilesAndSkippedDirectoriesAreNotRead(String path) {
        Fixture fixture = Fixture.copy(dir).passingEvidence();
        appendLine(fixture, path, "See `GhostTest`.");

        assertThat(problems(fixture)).isEmpty();
    }

    @Test
    void generationStopsOnAnUnknownTestName() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\nSee `RenamedAwayTest`.\n");

        assertThatThrownBy(() -> GeneratedFiles.generate(Sources.read(fixture.root(), List.of(fixture.reports()))))
                .isInstanceOf(DocsException.class)
                .hasMessageContaining(NOTES + ":3: `RenamedAwayTest` names no test class");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static List<String> problems(Fixture fixture) {
        Problems problems = new Problems();
        References.check(Sources.read(fixture.root(), List.of(fixture.reports())), problems);
        return problems.list();
    }

    /** Appends a paragraph and returns its line number, so that a message can be checked against the whole file. */
    private static int appendLine(Fixture fixture, String path, String line) {
        String text = fixture.root().resolve(path).toFile().exists() ? fixture.read(path) : "";
        String base = text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
        fixture.write(path, base + "\n" + line + "\n");
        return (int) base.chars().filter(c -> c == '\n').count() + 2;
    }
}
