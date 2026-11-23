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

    /**
     * A file and a directory whose names differ only in letter case can stand side by side on a file system that keeps
     * case, as in CI; the inventory is built in memory, since a Mac cannot hold both.
     */
    @Test
    void aFileWhoseNameDiffersOnlyInLetterCaseNeverHidesADirectory() {
        PathReferences paths = new PathReferences(Set.of("DOCS", "docs/gaps.md", "docs/a", "docs/A/x.md"), Set.of(),
                Set.of());
        Problems problems = new Problems();

        paths.bare("docs/gaps.md", "README.md", "README.md:1", problems);
        paths.bare("docs/nowhere.md", "README.md", "README.md:2", problems);
        paths.marked("absent:path:docs/a/", "absent", "docs/a/", "README.md", "README.md:3", problems);
        paths.marked("planned:path:Docs/b.md", "planned", "Docs/b.md", "README.md", "README.md:4", problems);

        assertThat(problems.list()).containsExactly("README.md:2: `docs/nowhere.md` names no file or directory " + ROOT
                + "; if it is planned, write planned:path:docs/nowhere.md, and if it is meant to be absent, "
                + "absent:path:docs/nowhere.md (ADR 0023)", "README.md:3: `absent:path:docs/a/`: docs/a/ differs only "
                + "in letter case from docs/A/, which exists (ADR 0023)", "README.md:4: `planned:path:Docs/b.md`: Docs/b.md "
                + "writes the directory Docs/ in another letter case: docs/ exists (ADR 0023)");
    }

    /**
     * A document's own directory is on its paths' way: a skipped directory or a link that differs from it only in case
     * stops a marker read from there, as it stops the same marker read from the root. Built in memory, since a Mac
     * cannot hold both spellings.
     */
    @Test
    void aMarkerFromTheDocumentsDirectoryChecksThatDirectoryToo() {
        PathReferences skipped = new PathReferences(Set.of("tools/src/build/notes.md"), Set.of(),
                Set.of("tools/SRC/build"));
        PathReferences linked = new PathReferences(Set.of("docs/notes.md"), Set.of("DOCS"), Set.of());
        Problems problems = new Problems();

        skipped.marked("absent:path:./x.txt", "absent", "./x.txt", "tools/src/build/notes.md", "a:1", problems);
        skipped.marked("planned:path:./y.txt", "planned", "./y.txt", "tools/src/build/notes.md", "a:2", problems);
        skipped.bare("./z.txt", "tools/src/build/notes.md", "a:3", problems);
        linked.marked("absent:path:./x.md", "absent", "./x.md", "docs/notes.md", "b:1", problems);

        String output = " is build output or tool state, which no check reads; write it without a marker (ADR 0023)";
        assertThat(problems.list()).containsExactly("a:1: `absent:path:./x.txt`: ./x.txt" + output,
                "a:2: `planned:path:./y.txt`: ./y.txt" + output, "a:3: `./z.txt` names no file or directory from "
                        + "tools/src/build/, this document's directory, and no marker can say so: it lies in build "
                        + "output or tool state, or under a directory the walk skips, in some letter case (ADR 0023)",
                "b:1: `absent:path:./x.md`: ./x.md reaches DOCS, a link or special file, which the check does not "
                        + "follow (ADR 0023)");
    }

    /**
     * A real source path is resolved before any skipped directory in another case is consulted, so local build output
     * changes no bare path's outcome. Built in memory, since a Mac cannot hold both spellings.
     */
    @Test
    void aRealSourcePathComesBeforeASkippedDirectoryInAnotherCase() {
        PathReferences paths = new PathReferences(Set.of("tools/notes/src/build/x.txt"), Set.of(),
                Set.of("tools/notes/SRC/build"));
        Problems problems = new Problems();

        paths.bare("tools/notes/src/build/x.txt", "README.md", "r:1", problems);
        paths.bare("tools/notes/SRC/build/x.txt", "README.md", "r:2", problems);
        paths.bare("tools/notes/src/build/y.txt", "README.md", "r:3", problems);

        assertThat(problems.list()).containsExactly("r:2: `tools/notes/SRC/build/x.txt` differs only in letter case "
                + "from tools/notes/src/build/x.txt, which exists (ADR 0023)", "r:3: `tools/notes/src/build/y.txt` "
                + "names no file or directory " + ROOT + ", and no marker can say so: it lies in build output or tool "
                + "state, or under a directory the walk skips, in some letter case (ADR 0023)");
    }

    /**
     * Every spelling of a path counts, not only the first in order: output in another case, which the walk keeps for
     * dist, never hides a real path, so adding output turns no case error into a pass. Built in memory, since a Mac
     * cannot hold both spellings.
     */
    @Test
    void anOutputSpellingNeverHidesARealPathInAnotherCase() {
        Set<String> real = Set.of("README.md", "tools/notes/a/DIST/x.txt");
        Set<String> withOutput = Set.of("README.md", "tools/notes/a/DIST/x.txt", "tools/notes/A/dist/x.txt");
        List<String> expected = List.of("r:1: `tools/notes/a/dist/x.txt` differs only in letter case from "
                + "tools/notes/a/DIST/x.txt, which exists (ADR 0023)", "r:2: `tools/notes/a/dist/y.txt` writes the "
                + "directory tools/notes/a/dist/ in another letter case: tools/notes/a/DIST/ exists (ADR 0023)");

        for (Set<String> files : List.of(real, withOutput)) {
            PathReferences paths = new PathReferences(files, Set.of(), Set.of());
            Problems problems = new Problems();
            paths.bare("tools/notes/a/dist/x.txt", "README.md", "r:1", problems);
            paths.bare("tools/notes/a/dist/y.txt", "README.md", "r:2", problems);

            assertThat(problems.list()).as(files.toString()).isEqualTo(expected);
        }
    }

    /**
     * Adding or removing a file the walk collects turns no failure into a pass: an output directory written exactly
     * never hides a real one in another case, and a path that exists still meets a link on its way. In memory, since
     * a Mac cannot hold both spellings.
     */
    @Test
    void aCollectedFileNeverTurnsAFailureIntoAPass() {
        String directoryCase = "r:1: `tools/notes/a/dist/y.txt` writes the directory tools/notes/a/dist/ in another letter "
                + "case: tools/notes/a/DIST/ exists (ADR 0023)";
        String link = ", a link or special file, which the check does not follow (ADR 0023)";
        for (boolean added : List.of(false, true)) {
            Set<String> files = added ? Set.of("README.md", "tools/notes/a/DIST/x.txt", "tools/notes/a/dist/z.txt",
                    "docs/notes.md", "docs/x.md", "x/dir/a.txt", "x/Dir/dist/q") : Set.of("README.md",
                    "tools/notes/a/DIST/x.txt", "docs/notes.md", "x/dir/a.txt");
            PathReferences paths = new PathReferences(files, Set.of("DOCS"), Set.of());
            Problems problems = new Problems();
            paths.bare("tools/notes/a/dist/y.txt", "README.md", "r:1", problems);
            paths.bare("docs/x.md", "README.md", "r:2", problems);
            paths.bare("./x.md", "docs/notes.md", "r:3", problems);
            paths.bare("x/Dir/dist/y", "README.md", "r:4", problems);

            assertThat(problems.list()).as("with the files added: " + added).containsExactly(directoryCase,
                    "r:2: `docs/x.md` reaches DOCS" + link, "r:3: `./x.md` reaches DOCS" + link, "r:4: `x/Dir/dist/y` "
                            + "writes the directory x/Dir/ in another letter case: x/dir/ exists (ADR 0023)");
        }
    }

    /**
     * Collected output that is exactly the referenced file, in dist or .claude, never turns a case error into a pass:
     * the shortcut for a path that exists applies only outside output. In memory, since a Mac cannot hold both
     * spellings.
     */
    @Test
    void theReferencedFileItselfInOutputNeverTurnsAFailureIntoAPass() {
        Set<String> real = Set.of("README.md", "tools/notes/a/DIST/x.txt", "tools/notes/b/.CLAUDE/r.txt");
        Set<String> withOutput = Set.of("README.md", "tools/notes/a/DIST/x.txt", "tools/notes/b/.CLAUDE/r.txt",
                "tools/notes/a/dist/x.txt", "tools/notes/a/dist/y.txt", "tools/notes/b/.claude/r.txt");
        List<String> expected = List.of("r:1: `tools/notes/a/dist/x.txt` differs only in letter case from "
                + "tools/notes/a/DIST/x.txt, which exists (ADR 0023)", "r:2: `tools/notes/a/dist/y.txt` writes the "
                + "directory tools/notes/a/dist/ in another letter case: tools/notes/a/DIST/ exists (ADR 0023)",
                "r:3: `tools/notes/b/.claude/r.txt` differs only in letter case from tools/notes/b/.CLAUDE/r.txt, which "
                        + "exists (ADR 0023)");

        for (Set<String> files : List.of(real, withOutput)) {
            PathReferences paths = new PathReferences(files, Set.of(), Set.of());
            Problems problems = new Problems();
            paths.bare("tools/notes/a/dist/x.txt", "README.md", "r:1", problems);
            paths.bare("tools/notes/a/dist/y.txt", "README.md", "r:2", problems);
            paths.bare("tools/notes/b/.claude/r.txt", "README.md", "r:3", problems);

            assertThat(problems.list()).as(files.toString()).isEqualTo(expected);
        }
    }

    /**
     * The document's reading of a path still counts when a link stands on it: a link collected in output that differs
     * only in case never turns an ambiguous or missing path into a pass. In memory, since a Mac cannot hold both
     * spellings.
     */
    @Test
    void aLinkOnTheDocumentsReadingNeverTurnsAFailureIntoAPass() {
        Set<String> files = Set.of("docs/notes.md", "tools/Dist/x.txt", "docs/tools/Dist/x.txt");
        String link = " reaches docs/tools/dist/%s, a link or special file, which the check does not follow (ADR 0023)";
        for (boolean linked : List.of(false, true)) {
            Set<String> special = linked ? Set.of("docs/tools/dist/x.txt", "docs/tools/dist/z.txt") : Set.of();
            PathReferences paths = new PathReferences(files, special, Set.of());
            Problems problems = new Problems();
            paths.bare("tools/Dist/x.txt", "docs/notes.md", "d:1", problems);
            paths.bare("tools/Dist/z.txt", "docs/notes.md", "d:2", problems);

            assertThat(problems.list()).as("linked: " + linked).containsExactly(linked
                    ? "d:1: `tools/Dist/x.txt`" + link.formatted("x.txt")
                    : "d:1: `tools/Dist/x.txt` names a file or directory both " + ROOT + " and from docs/, this "
                            + "document's directory: write /tools/Dist/x.txt for the first or ./tools/Dist/x.txt for the "
                            + "second (ADR 0023)", linked
                    ? "d:2: `tools/Dist/z.txt`" + link.formatted("z.txt")
                    : "d:2: `tools/Dist/z.txt` names no file or directory " + ROOT + "; if it is planned, write "
                            + "planned:path:tools/Dist/z.txt, and if it is meant to be absent, "
                            + "absent:path:tools/Dist/z.txt (ADR 0023)");
        }
    }

    /**
     * A missing path is sent to a marker only when that marker would hold: otherwise the message says why none can. In
     * memory, since a Mac cannot hold both spellings.
     */
    @Test
    void aMissingPathIsSentToAMarkerOnlyWhenTheMarkerWouldHold() {
        PathReferences skipped = new PathReferences(Set.of("README.md", "x/BUILD/b.txt"), Set.of(), Set.of("x/build"));
        PathReferences other = new PathReferences(Set.of("docs/notes.md", "docs/Tools/x.txt", "tools/y.txt"), Set.of(),
                Set.of());
        Problems problems = new Problems();

        skipped.bare("x/BUILD/a.txt", "README.md", "m:1", problems);
        skipped.marked("absent:path:x/BUILD/a.txt", "absent", "x/BUILD/a.txt", "README.md", "m:2", problems);
        other.bare("tools/x.txt", "docs/notes.md", "m:3", problems);
        other.marked("planned:path:tools/x.txt", "planned", "tools/x.txt", "docs/notes.md", "m:4", problems);

        assertThat(problems.list()).containsExactly("m:1: `x/BUILD/a.txt` names no file or directory " + ROOT + ", and "
                + "no marker can say so: it lies in build output or tool state, or under a directory the walk skips, in "
                + "some letter case (ADR 0023)", "m:2: `absent:path:x/BUILD/a.txt`: x/BUILD/a.txt is build output or tool "
                + "state, which no check reads; write it without a marker (ADR 0023)", "m:3: `tools/x.txt` names no file "
                + "or directory " + ROOT + ", and no marker can say so: it differs only in letter case from "
                + "docs/Tools/x.txt, which exists (ADR 0023)", "m:4: `planned:path:tools/x.txt`: tools/x.txt differs only "
                + "in letter case from docs/Tools/x.txt, which exists (ADR 0023)");
    }

    /** A file named build or dist is no output directory: the directory that holds it exists. */
    @Test
    void aFileNamedLikeOutputKeepsItsDirectory() {
        PathReferences paths = new PathReferences(Set.of("README.md", "tools/helper/build", "tools/other/dist"), Set.of(),
                Set.of());
        Problems problems = new Problems();

        paths.bare("tools/helper/", "README.md", "f:1", problems);
        paths.bare("tools/other/", "README.md", "f:2", problems);
        paths.marked("absent:path:tools/helper/", "absent", "tools/helper/", "README.md", "f:3", problems);
        paths.marked("planned:path:tools/other/", "planned", "tools/other/", "README.md", "f:4", problems);
        paths.bare("tools/helper/build", "README.md", "f:5", problems);
        paths.marked("absent:path:tools/helper/build", "absent", "tools/helper/build", "README.md", "f:6", problems);
        paths.bare("tools/helper/Build", "README.md", "f:7", problems);

        assertThat(problems.list()).containsExactly("f:3: `absent:path:tools/helper/`: tools/helper/ exists " + ROOT
                + ", so it is not absent (ADR 0023)", "f:4: `planned:path:tools/other/`: tools/other/ exists " + ROOT
                + ", so name it without planned: (ADR 0023)", "f:6: `absent:path:tools/helper/build`: tools/helper/build "
                + "exists " + ROOT + ", so it is not absent (ADR 0023)", "f:7: `tools/helper/Build` differs only in letter "
                + "case from tools/helper/build, which exists (ADR 0023)");
    }

    /** A path that exists exactly is found first: a real directory named Dist is no output in another letter case. */
    @Test
    void aPathThatExistsExactlyIsFoundBeforeAnyOutputRule() {
        PathReferences paths = new PathReferences(Set.of("README.md", "apps/x/Dist/y.txt"), Set.of(), Set.of());
        Problems problems = new Problems();

        paths.bare("apps/x/Dist/y.txt", "README.md", "r:1", problems);
        paths.bare("apps/x/dist/y.txt", "README.md", "r:2", problems);
        paths.marked("absent:path:apps/x/Dist/y.txt", "absent", "apps/x/Dist/y.txt", "README.md", "r:3", problems);

        assertThat(problems.list()).containsExactly("r:2: `apps/x/dist/y.txt` differs only in letter case from "
                + "apps/x/Dist/y.txt, which exists (ADR 0023)", "r:3: `absent:path:apps/x/Dist/y.txt`: apps/x/Dist/y.txt "
                + "exists " + ROOT + ", so it is not absent (ADR 0023)");
    }

    @ParameterizedTest
    @CsvSource({"tools/docs/BUILD/site/, tools/docs/build/site/", "apps/shipping-web/Dist/assets, apps/shipping-web/dist/assets",
        "docs/.Claude/rules/, docs/.claude/rules/"})
    void aBarePathThroughBuildOutputInAnotherLetterCaseSaysHowToWriteIt(String path, String canonical) {
        assertThat(problemsFor(path)).containsExactly(NOTES + ":3: `" + path + "` names build output or tool state in "
                + "another letter case; write " + canonical + ", which no check reads (ADR 0023)");
    }

    @Test
    void aBarePathThroughABuildDirectoryInsideSourcesInAnyLetterCaseIsChecked() {
        String path = "tools/notes/src/main/java/io/example/BUILD/Gone.java";

        assertThat(problemsFor(path)).containsExactly(missing(path, ROOT));
    }

    @Test
    void aSourceTreeInAnotherLetterCaseKeepsItsBuildPackageChecked() {
        String existing = "build-logic/src/main/java/io/github/stigjohnny/build/ProviderIndexTask.java";
        String variant = existing.replace("/src/", "/SRC/");
        String missing = variant.replace("ProviderIndexTask", "NoSuchTask");
        Fixture fixture = Fixture.copy(dir).passingEvidence()
                .write(NOTES, "# Notes\n\n`" + variant + "` and `" + missing + "`\n");

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `" + missing + "` writes the directory "
                + "build-logic/SRC/ in another letter case: build-logic/src/ exists (ADR 0023)", NOTES + ":3: `" + variant
                + "` differs only in letter case from " + existing + ", which exists (ADR 0023)");
    }

    /**
     * A source directory written SRC is no source tree for the walk, as for git, so a build directory in it is output
     * that the inventory leaves out; a marker there can prove nothing and fails, while a mis-cased reference to a real
     * source file still fails on its case.
     */
    @Test
    void aBuildDirectoryInARealUppercaseSourceDirectoryIsOutputThatNoMarkerCanProveAbsent() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/SRC/README.txt", "x\n")
                .write("tools/notes/SRC/build/x.txt", "x\n")
                .write(NOTES, "# Notes\n\n`absent:path:tools/notes/SRC/build/x.txt`, "
                        + "`planned:path:tools/notes/SRC/build/y.txt`, `tools/notes/SRC/build/x.txt`, "
                        + "`absent:path:tools/notes/SRC/BUILD/x.txt`, `planned:path:tools/notes/SRC/Build/y.txt` and "
                        + "`tools/notes/SRC/BUILD/x.txt`\n");
        String output = " is build output or tool state, which no check reads; write it without a marker (ADR 0023)";

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `absent:path:tools/notes/SRC/BUILD/x.txt`: "
                + "tools/notes/SRC/BUILD/x.txt" + output, NOTES + ":3: `absent:path:tools/notes/SRC/build/x.txt`: "
                + "tools/notes/SRC/build/x.txt" + output, NOTES + ":3: `planned:path:tools/notes/SRC/Build/y.txt`: "
                + "tools/notes/SRC/Build/y.txt" + output, NOTES + ":3: `planned:path:tools/notes/SRC/build/y.txt`: "
                + "tools/notes/SRC/build/y.txt" + output, NOTES + ":3: `tools/notes/SRC/BUILD/x.txt` names build output "
                + "or tool state in another letter case; write tools/notes/SRC/build/x.txt, which no check reads "
                + "(ADR 0023)");
    }

    /** The walk skips a build directory in a tree named SRC; its place is kept, so a case variant cannot pass as absent. */
    @Test
    void aSkippedDirectoryKeepsItsCaseEvidenceWhenNothingElseIsBesideIt() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/notes/SRC/build/x.txt", "x\n")
                .write(NOTES, "# Notes\n\n`absent:path:tools/notes/src/build/x.txt`, "
                        + "`planned:path:tools/notes/src/build/y.txt` and `tools/notes/src/build/x.txt`\n");
        String output = " is build output or tool state, which no check reads; write it without a marker (ADR 0023)";

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `absent:path:tools/notes/src/build/x.txt`: "
                + "tools/notes/src/build/x.txt" + output, NOTES + ":3: `planned:path:tools/notes/src/build/y.txt`: "
                + "tools/notes/src/build/y.txt" + output, NOTES + ":3: `tools/notes/src/build/x.txt` names build output "
                + "or tool state in another letter case; write tools/notes/SRC/build/x.txt, which no check reads "
                + "(ADR 0023)");
    }

    @Test
    void aDirectoryThatHoldsOnlyALinkExists() throws IOException {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write(NOTES, "# Notes\n\n`absent:path:tools/notes/links/` "
                + "and `absent:path:tools/notes/LINKS/`\n");
        Files.createDirectories(fixture.root().resolve("tools/notes/links"));
        Files.createSymbolicLink(fixture.root().resolve("tools/notes/links/item"), fixture.root().resolve("docs"));

        assertThat(problems(fixture)).containsExactly(NOTES + ":3: `absent:path:tools/notes/LINKS/`: tools/notes/LINKS/ "
                + "differs only in letter case from tools/notes/links/, which exists (ADR 0023)", NOTES + ":3: "
                + "`absent:path:tools/notes/links/`: tools/notes/links/ exists " + ROOT + ", so it is not absent "
                + "(ADR 0023)");
    }

    /**
     * A document's own directory holds the document, so it is a real directory even when its name is an output name in
     * another case, such as BUILD: a missing path there is missing, and the marker that the message offers holds.
     */
    @Test
    void aDocumentsOwnDirectoryIsRealWhateverTheCaseOfItsName() {
        Fixture fixture = Fixture.copy(dir).passingEvidence().write("tools/BUILD/notes.md",
                "# Notes\n\nSee `./x.md` and `planned:path:./x.md`.\n");

        assertThat(problems(fixture)).containsExactly("tools/BUILD/notes.md:3: `./x.md` names no file or directory from "
                + "tools/BUILD/, this document's directory; if it is planned, write planned:path:./x.md, and if it is "
                + "meant to be absent, absent:path:./x.md (ADR 0023)");
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
