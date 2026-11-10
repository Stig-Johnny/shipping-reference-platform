package io.github.stigjohnny.docs.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stigjohnny.docs.DocsException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Current declarations, independently of review prose, permission, and runtime execution. */
class DependencyUsageTest {

    private static final String LOCK = "tools/docs/gradle.lockfile";
    private static final String SHA = "1234567890abcdef1234567890abcdef12345678";
    private static final String NPM_ARCHIVE = "https://registry.npmjs.org/playwright/-/playwright-1.65.0.tgz";

    @TempDir
    Path repo;

    @Test
    void locksKeepExactVersionsConfigurationsAndAllProjectsInStableOrder() throws Exception {
        write(LOCK, "# org.example:thing:9.0=ignored\norg.example:thing:1.2=runtimeClasspath,testRuntimeClasspath\n");
        write("apps/shipping-api/gradle.lockfile", "org.example:thing:1.2=compileClasspath\n");
        AllowlistEntry entry = entry("maven", "org.example:thing", "1.2", "thing-1.2.jar", "");

        List<Seen> found = locations(read(), entry);

        assertThat(found).extracting(Seen::path)
                .containsExactly("apps/shipping-api/gradle.lockfile", LOCK);
        assertThat(found.get(1).line()).isEqualTo(2);
        assertThat(found.get(1).context()).contains(":tools:docs", "runtimeClasspath", "testRuntimeClasspath",
                "artifact variant/bytes not established");
        assertThat(found.get(1).excerpt()).isEqualTo("org.example:thing:1.2=runtimeClasspath,testRuntimeClasspath");
        assertThat(locations(read(), entry("maven", "org.example:thing", "9.0", "thing-9.0.jar", ""))).isEmpty();
        assertThat(locations(read(), entry("maven", "org.example:thing-extra", "1.2", "thing-extra-1.2.jar", "")))
                .isEmpty();
        assertThat(locations(read(), entry)).isEqualTo(found);
    }

    @Test
    void scopeExtensionsShareDeclarationsWithoutTurningThemIntoArtifactProof() throws Exception {
        write(LOCK, "org.example:thing:1.2=runtimeClasspath\n");
        AllowlistEntry jar = entry("maven", "org.example:thing", "1.2", "thing-1.2.jar", "");
        AllowlistEntry metadata = entry("maven", "org.example:thing", "1.2", "thing-1.2.module", "");

        assertThat(locations(read(), jar)).isEqualTo(locations(read(), metadata));
        assertThat(locations(read(), metadata).getFirst().context()).contains("artifact variant/bytes not established");
    }

    @Test
    void rereadingAfterVersionChangeAndRemovalCannotKeepOldUsage() throws Exception {
        AllowlistEntry entry = entry("maven", "org.example:thing", "1.2", "thing-1.2.jar", "");
        write(LOCK, "org.example:thing:1.2=runtimeClasspath\n");
        DependencyUsage snapshot = read();
        assertThat(locations(snapshot, entry)).hasSize(1);
        write(LOCK, "org.example:thing:1.3=runtimeClasspath\n");
        assertThat(locations(read(), entry)).isEmpty();
        Files.delete(repo.resolve(LOCK));
        assertThat(locations(read(), entry)).isEmpty();
        assertThat(locations(snapshot, entry)).hasSize(1);
        assertThatThrownBy(() -> rawLocations(snapshot, entry).clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reviewProseCommentsAndUnknownFilesAreNotDeclarations() throws Exception {
        write("security/reviews/sample.md", "org.example:thing:1.2=runtimeClasspath\n");
        write("README.md", "uses: actions/checkout@" + SHA + "\n");
        write("unscanned/gradle.lockfile", "org.example:thing:1.2=runtimeClasspath\n");
        write(LOCK, "# org.example:thing:1.2=runtimeClasspath\nempty=runtimeClasspath\n");

        assertThat(locations(read(), entry("maven", "org.example:thing", "1.2", "thing-1.2.jar", ""))).isEmpty();
        assertThat(locations(read(), entry("github-action", "actions/checkout", "7.0.1", SHA, ""))).isEmpty();
    }

    @Test
    void npmUsesStructuredPackageIdentityVersionAndArchiveIncludingNestedInstalls() throws Exception {
        write("package-lock.json", """
                {
                  "lockfileVersion": 3,
                  "description": "playwright 1.65.0",
                  "packages": {
                    "node_modules/playwright": {
                      "version": "1.65.0",
                      "resolved": "%s",
                      "dev": true
                    },
                    "node_modules/parent/node_modules/playwright": {"version": "1.65.0", "resolved": "%s"},
                    "node_modules/playwright-core": {"version": "1.65.0", "resolved": "%s"},
                    "apps/web": {"dependencies": {"playwright": "1.65.0"}}
                  }
                }
                """.formatted(NPM_ARCHIVE, NPM_ARCHIVE, NPM_ARCHIVE));
        AllowlistEntry entry = entry("npm", "playwright", "1.65.0", NPM_ARCHIVE, "");

        List<Seen> found = locations(read(), entry);

        assertThat(found).hasSize(2);
        assertThat(found).allSatisfy(location -> assertThat(location.line()).isZero());
        assertThat(found).extracting(Seen::context).anySatisfy(context ->
                assertThat(context).contains("packages[\"node_modules/playwright\"].version", "dev"));
        assertThat(found.getFirst().excerpt()).isEqualTo("\"version\": \"1.65.0\"");
        assertThat(locations(read(), entry("npm", "playwright", "1.65.1", NPM_ARCHIVE, ""))).isEmpty();
        assertThat(locations(read(), entry("npm", "playwright", "1.65.0", NPM_ARCHIVE + "-different", ""))).isEmpty();
    }

    @Test
    void compactJsonAndEscapedPackageKeysKeepThePreciseStructuralPointer() throws Exception {
        write("package-lock.json", "{\"lockfileVersion\":3,\"packages\":{\"node_modules\\/playwright\":{\"version\":\"1.65.0\","
                + "\"resolved\":\"" + NPM_ARCHIVE + "\"}}}");

        assertThat(locations(read(), entry("npm", "playwright", "1.65.0", NPM_ARCHIVE, "")))
                .singleElement().satisfies(location -> {
                    assertThat(location.line()).isZero();
                    assertThat(location.context()).contains("packages[\"node_modules/playwright\"].version");
                });
    }

    @Test
    void actionsRequireARealStructuredUsesAndTheReviewedFullCommit() throws Exception {
        write(".github/workflows/example.yml", """
                jobs:
                  build:
                    runs-on: ubuntu-24.04
                    steps:
                      - run: |
                          uses: actions/checkout@%s
                      - uses: 'actions/checkout@%s' # reviewed
                      - uses: actions/checkout@v7.0.1
                      - uses: actions/checkout@1234567
                """.formatted(SHA, SHA));

        List<Seen> found = locations(read(), entry("github-action", "actions/checkout", "7.0.1", SHA, ""));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().line()).isZero();
        assertThat(found.getFirst().context()).contains("jobs.build.steps[1].uses", "step 2");
        assertThat(found.getFirst().excerpt()).isEqualTo("uses: actions/checkout@" + SHA);
        assertThat(locations(read(), entry("github-action", "actions/checkout", "7.0.1", "v7.0.1", ""))).isEmpty();
    }

    @Test
    void bundledEvidenceIsOnlyAnExactReviewedCarrierAndDisappearsWithIt() throws Exception {
        write("package-lock.json", npm("1.65.0"));
        AllowlistEntry bundle = entry("bundled", "@babel/core", "7.29.7",
                "inlined in playwright-1.65.0.tgz: lib/transform/babelBundle.js", "");

        assertThat(locations(read(), bundle)).singleElement().satisfies(location ->
                assertThat(location.context()).contains("Reviewed bundle carrier playwright 1.65.0",
                        "embedded contents not re-inspected"));
        assertThat(locations(read(), entry("npm", "@babel/core", "7.29.7", bundle.artifact(), ""))).isEmpty();
        assertThat(locations(read(), entry("bundled", "@babel/core", "7.29.7",
                "mentions playwright-1.65.0.tgz without a carrier declaration", ""))).isEmpty();
        write("package-lock.json", npm("1.66.0"));
        assertThat(locations(read(), bundle)).isEmpty();
        Files.delete(repo.resolve("package-lock.json"));
        assertThat(locations(read(), bundle)).isEmpty();
    }

    @Test
    void distributionRequiresTheExactArchiveAndDigestNotTheSameVersionOnAnotherArtifact() throws Exception {
        String digest = "a".repeat(64);
        write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.2-bin.zip\n"
                + "distributionSha256Sum=" + digest + "\n");

        assertThat(locations(read(), entry("gradle-distribution", "gradle (services.gradle.org/distributions)",
                "9.8.2", "gradle-9.8.2-bin.zip", digest))).hasSize(1);
        assertThat(locations(read(), entry("gradle-distribution", "gradle (services.gradle.org/distributions)",
                "9.8.2", "gradle/wrapper/gradle-wrapper.jar (gradle-9.8.2-wrapper.jar)", digest))).isEmpty();
        assertThat(locations(read(), entry("gradle-distribution", "gradle (services.gradle.org/distributions)",
                "9.8.2", "gradle-9.8.2-bin.zip", "b".repeat(64)))).isEmpty();
    }

    @Test
    void vendoredMermaidRequiresTheExactBytesAndDoesNotPublishTheScriptAsAnExcerpt() throws Exception {
        byte[] bytes = "/* test fixture */\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        write("tools/docs/vendor/mermaid/11.17.2/mermaid.min.js", new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        AllowlistEntry entry = entry("vendored-js", "mermaid", "11.17.2", "dist/mermaid.min.js", Allowlist.sha256(bytes));

        assertThat(locations(read(), entry)).singleElement().satisfies(location ->
                assertThat(location.excerpt()).contains("SHA-256", entry.sha256()).doesNotContain("test fixture"));
        write("tools/docs/vendor/mermaid/11.17.2/mermaid.min.js", "changed\n");
        assertThat(locations(read(), entry)).isEmpty();
    }

    @Test
    void malformedSupportedInputsFailInsteadOfSilentlyReportingNoUse() throws Exception {
        write(LOCK, "not a lock entry\n");
        assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining(LOCK);
        Files.delete(repo.resolve(LOCK));
        write("package-lock.json", "{\"packages\": []}");
        assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining("package-lock.json");
        Files.delete(repo.resolve("package-lock.json"));
        write(".github/workflows/bad.yml", "jobs: [not-a-map]\n");
        assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining(".github/workflows/bad.yml");
    }

    @Test
    void unsafeAndUnreadableInputsFailWithoutLeakingAbsolutePaths() throws Exception {
        Path outside = Files.createTempFile(repo.getParent(), "usage-outside-", ".lock");
        try {
            Files.createDirectories(repo.resolve("tools/docs"));
            Files.createSymbolicLink(repo.resolve(LOCK), outside);
            assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining(LOCK)
                    .hasMessageNotContaining(outside.toString());
            Files.delete(repo.resolve(LOCK));
            Files.createDirectory(repo.resolve(LOCK));
            assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining(LOCK);
        } finally {
            Files.delete(outside);
        }
    }

    @Test
    void scopedPackagesAndAliasesMatchTheResolvedNameWithoutInventingAStandaloneBundle() throws Exception {
        String archive = "https://registry.npmjs.org/@scope/tool/-/tool-1.2.tgz";
        write("package-lock.json", """
                {"lockfileVersion":3,"packages":{
                  "node_modules/alias":{"name":"@scope/tool","version":"1.2","resolved":"%s"},
                  "node_modules/@scope/tool":{"version":"1.3","resolved":"%s"},
                  "node_modules/workspace":{"link":true,"resolved":"apps/web"}
                }}
                """.formatted(archive, archive));

        assertThat(locations(read(), entry("npm", "@scope/tool", "1.2", archive, ""))).singleElement()
                .satisfies(location -> assertThat(location.context()).contains("packages[\"node_modules/alias\"].version"));
        assertThat(locations(read(), entry("npm", "alias", "1.2", archive, ""))).isEmpty();
        assertThat(locations(read(), entry("bundled", "@scope/tool", "1.2", archive, ""))).isEmpty();
    }

    @Test
    void yamlExtensionsReusableWorkflowsAndActionSubpathsRetainTheirIdentity() throws Exception {
        write(".github/workflows/reuse.yaml", """
                jobs:
                  shared:
                    uses: owner/repo/.github/workflows/build.yml@%s
                  local:
                    steps:
                      - uses: ./local-action
                      - uses: owner/repo/subpath@%s
                """.formatted(SHA, SHA));

        List<Seen> found = locations(read(), entry("github-action", "owner/repo", "1", SHA, ""));
        assertThat(found).hasSize(2);
        assertThat(found).extracting(Seen::excerpt).containsExactlyInAnyOrder(
                "uses: owner/repo/.github/workflows/build.yml@" + SHA, "uses: owner/repo/subpath@" + SHA);
        assertThat(found).extracting(Seen::context).anySatisfy(context -> assertThat(context).contains("jobs.shared.uses"));
        assertThat(locations(read(), entry("github-action", "owner/repo/subpath", "1", SHA, ""))).isEmpty();
        assertThat(locations(read(), entry("github-action", "owner/repo", "1", "f".repeat(40), ""))).isEmpty();
    }

    @Test
    void unsupportedKindsHaveNoInferredDeclarationsAndExplicitCoverageLimits() throws Exception {
        write("package-lock.json", npm("1.65.0"));
        AllowlistEntry browser = entry("browser-binary", "playwright", "1.65.0", NPM_ARCHIVE, "");

        assertThat(read().locations(browser)).isEmpty();
        assertThat(DependencyUsage.limitation(browser.kind())).contains("Evidence not collected for this kind.")
                .doesNotContain("unused");
        assertThat(DependencyUsage.limitation("bundled")).contains("not re-inspected", "standalone");
    }

    @Test
    void duplicateWrapperKeysCannotMakeAnEarlierLineLookLikeTheEffectiveDeclaration() throws Exception {
        write("gradle/wrapper/gradle-wrapper.properties", "distributionUrl=https\\://services.gradle.org/distributions/gradle-9.8.2-bin.zip\n"
                + "distributionUrl: https\\://services.gradle.org/distributions/gradle-9.9.0-bin.zip\n"
                + "distributionSha256Sum=" + "a".repeat(64) + "\n");

        assertThatThrownBy(this::read).isInstanceOf(DocsException.class)
                .hasMessageContaining("gradle/wrapper/gradle-wrapper.properties");
    }

    @Test
    void invalidUtf8AndUnsupportedLockSchemasFailRatherThanBeingRepairedSilently() throws Exception {
        write("package-lock.json", "{\"lockfileVersion\":2,\"packages\":{}}");
        assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining("package-lock.json");
        byte[] prefix = "{\"lockfileVersion\":3,\"description\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] suffix = "\",\"packages\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] invalid = new byte[prefix.length + 1 + suffix.length];
        System.arraycopy(prefix, 0, invalid, 0, prefix.length);
        invalid[prefix.length] = (byte) 0xff;
        System.arraycopy(suffix, 0, invalid, prefix.length + 1, suffix.length);
        Files.write(repo.resolve("package-lock.json"), invalid);

        assertThatThrownBy(this::read).isInstanceOf(DocsException.class).hasMessageContaining("package-lock.json");
    }

    @Test
    void locationCannotCarryTraversalOrControlCharacters() {
        assertThatThrownBy(() -> new DependencyUsage.Location("../outside", 0, "pointer", "declaration"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DependencyUsage.Location("package-lock.json", 0, "pointer\nnew line", "declaration"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void write(String relative, String content) throws IOException {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static AllowlistEntry entry(String kind, String coordinate, String version, String artifact, String hash) {
        return new AllowlistEntry(kind, coordinate, version, artifact, hash, "APPROVE", "reviewed scope, not usage",
                List.of(), "security/reviews/example.md", "2026-11-10", "2027-05-10");
    }

    private static String npm(String version) {
        return "{\"lockfileVersion\":3,\"packages\":{\"node_modules/playwright\":{\"version\":\"" + version
                + "\",\"resolved\":\"https://registry.npmjs.org/playwright/-/playwright-" + version + ".tgz\"}}}";
    }

    private DependencyUsage read() {
        return DependencyUsage.read(repo);
    }

    private static List<DependencyUsage.Location> rawLocations(DependencyUsage usage, AllowlistEntry entry) {
        return usage.locations(entry);
    }

    private static List<Seen> locations(DependencyUsage usage, AllowlistEntry entry) {
        return usage.locations(entry).stream()
                .map(location -> new Seen(location.path(), location.line(), location.context(), location.excerpt()))
                .toList();
    }

    private record Seen(String path, int line, String context, String excerpt) {
    }
}
