package io.github.stigjohnny.shipping.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Result;
import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Scope;
import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Use;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * External dependencies reported by ArchUnit 1.5.1 from API outputs have verified providers and are on the
 * whitelist of the part of the API that uses it (security/allowlist.json and ADR 0007; the layers of ADR 0003).
 *
 * <p>The provider index, which build-logic's ProviderIndexTask writes from the four classpaths, names the artifact
 * behind each class; the JDK's module descriptors name its module. Main and test classes are read from their own
 * class-output directories, and their reports go to build/reports/external-imports/ before any assertion. Each rule
 * also fails on a fixture: the compiled classes under fixtures/externalimports, and classes this test compiles itself,
 * where the build's compiler ({@code --release 25 -Werror}) refuses the source or the reference would need an
 * artifact that is not reviewed for this project.
 */
class ExternalImportPolicyTest {

    private static final String FIXTURES = ExternalImportPolicyTest.class.getPackageName()
            + ".fixtures.externalimports.";
    private static final String QUOTE_CHECKS = FIXTURES + "testonly.quotes.internal.application.QuoteChecks";
    private static final String ISSUE_QUOTE = FIXTURES + "legitimate.quotes.internal.application.IssueQuote";
    private static final String SPRING_CONTEXT = "org.springframework:spring-context";
    private static final Path FIXTURE_REPORTS = ExternalImportReport.DIRECTORY.resolve("fixtures");
    private static final ProviderIndex INDEX = ProviderIndex.current();
    private static final JdkModules JDK = JdkModules.system();

    // Read on use so alias fixtures can test refusal without a failing real-record initializer masking assertions.
    private static ReviewedArtifacts reviewed() {
        return ReviewedArtifacts.read(ReviewedArtifacts.ALLOWLIST, LocalDate.now(ZoneOffset.UTC));
    }

    private static ExternalImportPolicy policy() {
        return new ExternalImportPolicy(INDEX, reviewed(), JDK);
    }

    @Test
    void theMainClassesUseOnlyReviewedWhitelistedClasses() {
        Result result = policy().check("main", Scope.MAIN, ExternalImportPolicy.API, ClassDirectories.main());

        ExternalImportReport.write(result, ExternalImportReport.DIRECTORY, inputs());
        assertThat(lines(result.denied())).isEmpty();
        assertThat(result.uses()).extracting(Use::provider).contains("jdk java.base",
                "maven org.springframework:spring-tx:7.0.10", "maven org.jspecify:jspecify:1.0.1");
    }

    @Test
    void theTestClassesUseOnlyReviewedWhitelistedClasses() {
        Result result = policy().check("test", Scope.TEST, ExternalImportPolicy.API, ClassDirectories.test());

        ExternalImportReport.write(result, ExternalImportReport.DIRECTORY, inputs());
        assertThat(lines(result.denied())).isEmpty();
        assertThat(result.uses()).extracting(Use::area).contains("module tests", "architecture tests",
                "architecture fixtures", "http tests", "scenario tests", "scenario fixtures");
    }

    @Test
    void theHttpTestAreaCoversOnlyTheExactTestPackage() {
        String http = ExternalImportPolicy.API + ".http";
        assertThat(ExternalImportWhitelists.area(Scope.TEST, ExternalImportPolicy.API, http)
                .map(ExternalImportWhitelists.Area::label)).contains("http tests");
        assertThat(ExternalImportWhitelists.area(Scope.TEST, ExternalImportPolicy.API, http + ".extra")).isEmpty();
        assertThat(ExternalImportWhitelists.area(Scope.MAIN, ExternalImportPolicy.API, http)
                .map(ExternalImportWhitelists.Area::label)).contains("module root and api");
    }

    @Test
    void theScenarioTestAreaCoversOnlyTheTwoExactTestPackages() {
        String scenarios = ExternalImportPolicy.API + ".scenarios";
        for (String packageName : List.of(scenarios, scenarios + ".support")) {
            assertThat(ExternalImportWhitelists.area(Scope.TEST, ExternalImportPolicy.API, packageName)
                    .map(ExternalImportWhitelists.Area::label)).contains("scenario tests");
            assertThat(ExternalImportWhitelists.area(Scope.TEST, ExternalImportPolicy.API, packageName + ".extra"))
                    .isEmpty();
        }
        assertThat(ExternalImportWhitelists.area(Scope.MAIN, ExternalImportPolicy.API, scenarios)
                .map(ExternalImportWhitelists.Area::label)).contains("module root and api");
        assertThat(ExternalImportWhitelists.area(Scope.MAIN, ExternalImportPolicy.API, scenarios + ".support"))
                .isEmpty();
    }

    @Test
    void scenarioFixturesHaveTheirOwnTestAreaWithoutGrantingTheirModulePackages() {
        for (String fixture : List.of("quotes.internal.application.ScenarioAlterations",
                "quotes.internal.application.GatedQuoteStore", "tenants.api.TenantBadge",
                "tenants.internal.application.ScenarioReference")) {
            String name = ExternalImportPolicy.API + "." + fixture;
            for (String className : List.of(name, name + "$Nested")) {
                assertThat(ExternalImportWhitelists.areaOfClass(Scope.TEST, ExternalImportPolicy.API, className)
                        .map(ExternalImportWhitelists.Area::label)).contains("scenario fixtures");
                assertThat(ExternalImportWhitelists.areaOfClass(Scope.MAIN, ExternalImportPolicy.API, className))
                        .isEqualTo(ExternalImportWhitelists.area(Scope.MAIN, ExternalImportPolicy.API,
                                ExternalImportPolicy.packageOf(className)));
            }
            assertThat(ExternalImportWhitelists.areaOfClass(Scope.TEST, ExternalImportPolicy.API, name + "Extra")
                    .map(ExternalImportWhitelists.Area::label)).contains("module tests");
            assertThat(ExternalImportWhitelists.areaOfClass(Scope.TEST, ExternalImportPolicy.API,
                    name + ".Extra")).isEmpty();
        }
    }

    @Test
    void everyWhitelistEntryIsUsedByTodaysCode() {
        Set<ExternalImportWhitelists.Allowed> used = new HashSet<>(policy().check("main", Scope.MAIN,
                ExternalImportPolicy.API, ClassDirectories.main()).entriesUsed());
        used.addAll(policy().check("test", Scope.TEST, ExternalImportPolicy.API, ClassDirectories.test())
                .entriesUsed());

        assertThat(ExternalImportWhitelists.all()).allSatisfy(entry -> assertThat(used).as("the %s entry %s is "
                + "used by no class: remove it", entry.area().label(), entry.name()).contains(entry));
    }

    @Test
    void noClassHasTwoProvidersOnOneClasspath() {
        assertThat(INDEX.duplicates()).isEmpty();
        assertThat(INDEX.providers()).extracting(ProviderIndex.Provider::configurations)
                .allSatisfy(configurations -> assertThat(configurations).isNotEmpty().isSubsetOf("compileClasspath",
                        "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"));
    }

    @Test
    void aMultiReleaseJarProvidesItsVersionedClassesAndAJarWithoutTheAttributeDoesNot() {
        // spring-core 7.0.10 says Multi-Release: true and has ClassFileMetadataReader under META-INF/versions/24/ only.
        String versioned = "org.springframework.core.type.classreading.ClassFileMetadataReader";
        // testcontainers 2.0.5 has this class under META-INF/versions/11/ only, without the attribute: the JDK never
        // loads it, so nothing provides it.
        String inert = "org.testcontainers.shaded.org.bouncycastle.jcajce.provider.asymmetric.edec.BC11XDHPrivateKey";

        assertThat(INDEX.providers("compileClasspath", versioned)).extracting(ProviderIndex.Provider::label)
                .containsExactly("maven org.springframework:spring-core:7.0.10");
        assertThat(INDEX.configurationsProviding(inert)).isEmpty();
        assertThat(INDEX.providers("testCompileClasspath", "org.testcontainers.containers.GenericContainer"))
                .extracting(ProviderIndex.Provider::label)
                .containsExactly("maven org.testcontainers:testcontainers:2.0.5");
    }

    @Test
    void theReviewsFirstDesignNameIsTheApiAsTheOwnerConfirmed() {
        // ReviewedArtifacts reads :example-app as :apps:shipping-api only because the owner's acceptance says so.
        assertThat(reviewed().projectPathConfirmed()).isEqualTo(ReviewedArtifacts.CONFIRMED_ALIAS);
        assertThat(List.of(ReviewedArtifacts.project("apps/shipping-api"), ReviewedArtifacts.project(":example-app"),
                ReviewedArtifacts.project(":libs:c4-contract-kit"))).containsExactly(ReviewedArtifacts.API,
                ReviewedArtifacts.API, ":libs:c4-contract-kit");
        assertThat(ReviewedArtifacts.grants(List.of(":core", ":example-app"),
                List.of(":example-app:testCompileClasspath", "runtimeClasspath"))).containsExactlyInAnyOrder(
                new ReviewedArtifacts.Grant(ReviewedArtifacts.API, "testCompileClasspath"),
                new ReviewedArtifacts.Grant(":core", "runtimeClasspath"),
                new ReviewedArtifacts.Grant(ReviewedArtifacts.API, "runtimeClasspath"));
    }

    @Test
    void aMissingAliasRecordFails(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, Files.readString(ReviewedArtifacts.ALLOWLIST)
                .replace("projectPathConfirmed", "missingConfirmation"));
    }

    @Test
    void aChangedAliasRecordFails(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, Files.readString(ReviewedArtifacts.ALLOWLIST)
                .replace(ReviewedArtifacts.CONFIRMED_ALIAS, ReviewedArtifacts.CONFIRMED_ALIAS + " Changed."));
    }

    @Test
    void aWithdrawnAliasRecordFails(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, Files.readString(ReviewedArtifacts.ALLOWLIST)
                .replace(ReviewedArtifacts.CONFIRMED_ALIAS, ReviewedArtifacts.CONFIRMED_ALIAS.replace(" is ", " is not ")));
    }

    @Test
    void aWithdrawnAcceptanceFailsEvenWithTheAffirmativeAliasText(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, Files.readString(ReviewedArtifacts.ALLOWLIST)
                .replace("\"status\": \"accepted\"", "\"status\": \"withdrawn\""));
    }

    @Test
    void theAliasSurvivesTheRealAcceptanceRollover() {
        var acceptance = ProviderIndex.JSON.readTree(ReviewedArtifacts.ALLOWLIST).path("acceptance");
        assertThat(acceptance.has("projectPathConfirmed")).isFalse();
        var preserved = acceptance.path("previous").get(0);
        assertThat(preserved.has("status")).isFalse();
        assertThat(preserved.path("projectPathConfirmed").asString()).isEqualTo(ReviewedArtifacts.CONFIRMED_ALIAS);
        assertThat(reviewed().projectPathConfirmed()).isEqualTo(ReviewedArtifacts.CONFIRMED_ALIAS);
    }

    @Test
    void theAliasCanStillBeConfirmedByTheCurrentAcceptance(@TempDir Path dir) throws IOException {
        assertAliasAccepted(dir, aliasAcceptance("\"status\":\"accepted\"," + aliasConfirmation(), ""));
    }

    @Test
    void anExplicitlyAcceptedHistoricalAliasRecordPasses(@TempDir Path dir) throws IOException {
        assertAliasAccepted(dir, aliasAcceptance("\"status\":\"accepted\"",
                "{\"status\":\"accepted\"," + aliasConfirmation() + "}"));
    }

    @Test
    void aMissingCurrentStatusCannotBeInferredFromHistory(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, aliasAcceptance("\"how\":\"no current status\"", "{" + aliasConfirmation() + "}"));
    }

    @Test
    void anExplicitNonAcceptedHistoricalStatusFails(@TempDir Path dir) throws IOException {
        for (String status : List.of("\"withdrawn\"", "\"pending\"", "\"\"", "null")) {
            assertAliasRefused(dir, aliasAcceptance("\"status\":\"accepted\"",
                    "{\"status\":" + status + "," + aliasConfirmation() + "}"));
        }
    }

    @Test
    void aNewerCurrentWithdrawalOverridesTheHistoricalAffirmation(@TempDir Path dir) throws IOException {
        for (String confirmation : List.of("null", "\"Changed\"", "\"" + ReviewedArtifacts.CONFIRMED_ALIAS
                .replace(" is ", " is not ") + "\"")) {
            assertAliasRefused(dir, aliasAcceptance("\"status\":\"accepted\",\"projectPathConfirmed\":"
                    + confirmation, "{" + aliasConfirmation() + "}"));
        }
    }

    @Test
    void aNewerHistoricalWithdrawalOverridesTheOlderAffirmation(@TempDir Path dir) throws IOException {
        for (String withdrawal : List.of("\"status\":\"withdrawn\"",
                "\"status\":\"withdrawn\"," + aliasConfirmation(), "\"projectPathConfirmed\":null",
                "\"projectPathConfirmed\":\"Changed\"", "\"projectPathConfirmed\":\""
                        + ReviewedArtifacts.CONFIRMED_ALIAS.replace(" is ", " is not ") + "\"")) {
            assertAliasRefused(dir, aliasAcceptance("\"status\":\"accepted\"", "{" + withdrawal + "},{"
                    + aliasConfirmation() + "}"));
        }
    }

    @Test
    void unrelatedHistoryDoesNotHideThePreservedAlias(@TempDir Path dir) throws IOException {
        assertAliasAccepted(dir, aliasAcceptance("\"status\":\"accepted\"",
                "{\"status\":\"accepted\"},{\"how\":\"unrelated acceptance\"},{" + aliasConfirmation() + "}"));
    }

    @Test
    void historyWithoutAnAliasConfirmationFails(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, aliasAcceptance("\"status\":\"accepted\"", "{\"how\":\"unrelated acceptance\"}"));
    }

    @Test
    void aStatuslessCurrentAffirmationIsNotAHistoricalAcceptance(@TempDir Path dir) throws IOException {
        assertAliasRefused(dir, aliasAcceptance(aliasConfirmation(), ""));
    }

    private static String aliasConfirmation() {
        return "\"projectPathConfirmed\":\"" + ReviewedArtifacts.CONFIRMED_ALIAS + "\"";
    }

    // acceptance.previous is stored newest first. Fixtures preserve that order and never edit security/.
    private static String aliasAcceptance(String current, String previous) {
        return "{\"acceptance\":{" + current + ",\"previous\":[" + previous + "]},\"entries\":[]}";
    }

    private static void assertAliasAccepted(Path dir, String contents) throws IOException {
        Path changed = Files.writeString(dir.resolve("allowlist.json"), contents);
        assertThat(ReviewedArtifacts.read(changed, LocalDate.parse("2026-11-08")).projectPathConfirmed())
                .isEqualTo(ReviewedArtifacts.CONFIRMED_ALIAS);
    }

    private static void assertAliasRefused(Path dir, String contents) throws IOException {
        Path changed = Files.writeString(dir.resolve("allowlist.json"), contents);
        assertThatThrownBy(() -> ReviewedArtifacts.read(changed, LocalDate.parse("2026-11-08")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(":example-app requires the exact accepted projectPathConfirmed record");
    }

    @Test
    void theIndexKeepsTheContractKitsBuildIdentityAndVerifiedOutput() {
        var json = ProviderIndex.JSON.readTree(Path.of(System.getProperty(ProviderIndex.PROPERTY)));
        var projects = json.get("providers").values().stream()
                .filter(node -> node.get("kind").asString().equals("project")).toList();
        assertThat(projects).isNotEmpty().allSatisfy(node -> {
            assertThat(node.path("buildPath").asString("")).isEqualTo(":");
            assertThat(node.path("repositoryOwned").asBoolean(false)).isTrue();
            assertThat(node.get("coordinate").asString()).isEqualTo(":libs:c4-contract-kit");
        });
    }

    @Test
    void anIncludedBuildCannotImpersonateTheContractKit(@TempDir Path dir) throws IOException {
        assertUnreviewedProject(dir, ":shadow", ":libs:c4-contract-kit", true);
    }

    @Test
    void anUnverifiedRootBuildProjectIsNotExempt(@TempDir Path dir) throws IOException {
        assertUnreviewedProject(dir, ":", ":shadow-lib", false);
    }

    @Test
    void aContractKitProjectWithAnUnverifiedOutputIsNotExempt(@TempDir Path dir) throws IOException {
        assertUnreviewedProject(dir, ":", ":libs:c4-contract-kit", false);
    }

    private void assertUnreviewedProject(Path dir, String buildPath, String project, boolean repositoryOwned)
            throws IOException {
        Path indexFile = Files.writeString(dir.resolve("index.json"), """
                {"schema":"provider-index/1", "providers":[{
                  "kind":"project", "coordinate":"%s", "version":"", "file":"shadow.jar", "sha256":null,
                  "buildPath":"%s", "repositoryOwned":%s,
                  "configurations":["compileClasspath"],
                  "classes":["org.springframework.context.ApplicationEventPublisher"]
                }], "duplicates":[]}
                """.formatted(project, buildPath, repositoryOwned ? "true" : "false"));
        ExternalImportPolicy unreviewed = new ExternalImportPolicy(ProviderIndex.read(indexFile), reviewed(), JDK);
        Result result = check(unreviewed, "project-provider", "legitimate", Scope.MAIN, fixture("legitimate"));
        assertThat(result.denied()).filteredOn(use -> use.target().equals(
                "org.springframework.context.ApplicationEventPublisher"))
                .extracting(Use::rule, Use::message).containsExactly(tuple("allowlist",
                        "project " + buildPath + " " + project + " is not a verified repository-owned project: "
                                + "no review covers it"));
    }

    @Test
    void aLegitimateUsePasses() {
        Result result = check("legitimate", Scope.MAIN, fixture("legitimate"));

        assertThat(result.denied()).isEmpty();
        assertThat(result.uses()).extracting(Use::target, Use::provider, Use::rule, Use::review).contains(
                tuple("org.springframework.context.ApplicationEventPublisher",
                        "maven org.springframework:spring-context:7.0.10",
                        "internal.application: org.springframework.context.ApplicationEventPublisher",
                        "security/reviews/maven--spring-framework--7.0.10.md"),
                tuple("org.springframework.transaction.annotation.Transactional",
                        "maven org.springframework:spring-tx:7.0.10",
                        "internal.application: org.springframework.transaction.annotation.Transactional",
                        "security/reviews/maven--spring-framework--7.0.10.md"));
        // A known limit (docs/gaps.md): ArchUnit 1.5.1 reports no type-use annotation, such as JSpecify's @Nullable on
        // the parameter. If this fails, it sees more than before: add Nullable to the lists and update docs/gaps.md.
        assertThat(result.uses()).extracting(Use::target).doesNotContain("org.jspecify.annotations.Nullable");
    }

    @Test
    void aDomainClassThatUsesSpringFails() {
        Result result = check("springdomain", Scope.MAIN, fixture("springdomain"));

        assertThat(lines(result.denied())).containsExactly(FIXTURES + "springdomain.quotes.internal.domain."
                + "PricedEvents uses org.springframework.context.ApplicationEventPublisher [whitelist] "
                + "org.springframework.context.ApplicationEventPublisher is not on the internal.domain whitelist");
    }

    @Test
    void aProductionClassThatUsesATestOnlyArtifactFails() {
        Result result = check("testonly", Scope.MAIN, fixture("testonly"));

        assertThat(lines(result.denied())).containsExactly(
                QUOTE_CHECKS + " uses org.assertj.core.api.AbstractIntegerAssert [provider] org.assertj.core.api."
                        + "AbstractIntegerAssert has no provider on compileClasspath: maven org.assertj:assertj-core:"
                        + "3.27.7 provides it on testCompileClasspath, testRuntimeClasspath only, a test-only artifact",
                QUOTE_CHECKS + " uses org.assertj.core.api.Assertions [provider] org.assertj.core.api.Assertions has "
                        + "no provider on compileClasspath: maven org.assertj:assertj-core:3.27.7 provides it on "
                        + "testCompileClasspath, testRuntimeClasspath only, a test-only artifact");
    }

    @Test
    void aTestOnlyArtifactOnTheMainClasspathFailsItsReviewedScope() {
        // As if assertj-core were moved to implementation and the locks regenerated: it would be on compileClasspath,
        // where its review does not approve it.
        ProviderIndex moved = INDEX.changing("org.assertj:assertj-core", provider -> new ProviderIndex.Provider(
                provider.kind(), provider.coordinate(), provider.version(), provider.file(), provider.sha256(),
                Set.of("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath"),
                provider.classes()));

        Result result = check(new ExternalImportPolicy(moved, reviewed(), JDK), "testonly-on-main", "testonly",
                Scope.MAIN, fixture("testonly"));

        String scope = " [allowlist] maven org.assertj:assertj-core:3.27.7 is not approved for :apps:shipping-api "
                + "compileClasspath: its entries approve it for :apps:shipping-api (testCompileClasspath, "
                + "testRuntimeClasspath); :core (testCompileClasspath, testRuntimeClasspath); :junit5 "
                + "(testCompileClasspath, testRuntimeClasspath)";
        assertThat(lines(result.denied())).containsExactly(
                QUOTE_CHECKS + " uses org.assertj.core.api.AbstractIntegerAssert" + scope,
                QUOTE_CHECKS + " uses org.assertj.core.api.Assertions" + scope);
    }

    @Test
    void aJdkInternalOrAPackageItsModuleDoesNotExportFails(@TempDir Path dir) throws IOException {
        // sun.misc is exported by jdk.unsupported, so only the name rule refuses it; java.awt.peer is a java.* package
        // that java.desktop does not export, so only the module descriptor refuses it.
        assertThat(JDK.exportsToAll("sun.misc")).isTrue();
        assertThat(JDK.exportsToAll("java.awt.peer")).isFalse();
        String internals = FIXTURES + "jdk.quotes.internal.domain.Internals";
        Path classes = compile(dir, List.of(), List.of("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-exports", "java.desktop/java.awt.peer=ALL-UNNAMED"), Map.of(internals, """
                public final class Internals {
                    sun.misc.Unsafe unsafe;
                    jdk.internal.misc.Unsafe internalUnsafe;
                    java.awt.peer.ComponentPeer peer;
                }
                """));

        Result result = check("jdk", Scope.MAIN, classes);

        assertThat(lines(result.denied())).containsExactly(
                internals + " uses java.awt.peer.ComponentPeer [jdk-module] java.awt.peer.ComponentPeer: the JDK "
                        + "module java.desktop does not export java.awt.peer",
                internals + " uses jdk.internal.misc.Unsafe [jdk-internal] jdk.internal.misc.Unsafe is a JDK internal "
                        + "(jdk.internal.*): refused, whatever its module exports",
                internals + " uses sun.misc.Unsafe [jdk-internal] sun.misc.Unsafe is a JDK internal (sun.*): refused, "
                        + "whatever its module exports");
    }

    @Test
    void aClassFromAnArtifactThatIsNotReviewedForThisProjectFails(@TempDir Path dir) throws IOException {
        // commons-lang3 3.20.0 is on the API's testCompileClasspath, through Testcontainers, but its only entry is
        // for :libs:c4-contract-kit. The fixture is compiled against a stub of the class, never against the JAR.
        String slugs = FIXTURES + "otherproject.quotes.internal.application.Slugs";
        Path stubs = compile(dir.resolve("stubs"), List.of(), List.of(), Map.of("org.apache.commons.lang3.StringUtils",
                "public final class StringUtils { public static String lowerCase(String text) { return text; } }"));
        Path classes = compile(dir.resolve("fixture"), List.of(stubs), List.of(), Map.of(slugs, """
                public final class Slugs {
                    static String slug(String text) {
                        return org.apache.commons.lang3.StringUtils.lowerCase(text);
                    }
                }
                """));

        Result result = check("otherproject", Scope.TEST, classes);

        assertThat(lines(result.denied())).containsExactly(slugs + " uses org.apache.commons.lang3.StringUtils "
                + "[allowlist] maven org.apache.commons:commons-lang3:3.20.0 is not approved for :apps:shipping-api "
                + "testCompileClasspath: its entries approve it for :libs:c4-contract-kit (compileClasspath, "
                + "runtimeClasspath, testCompileClasspath, testRuntimeClasspath)");
    }

    @Test
    void aClassThatNoArtifactProvidesFails(@TempDir Path dir) throws IOException {
        String caller = FIXTURES + "missing.quotes.internal.application.UsesHelper";
        Path stubs = compile(dir.resolve("stubs"), List.of(), List.of(), Map.of("org.example.unreviewed.Helper",
                "public final class Helper { public static void help() { } }"));
        Path classes = compile(dir.resolve("fixture"), List.of(stubs), List.of(), Map.of(caller, """
                public final class UsesHelper {
                    void use() {
                        org.example.unreviewed.Helper.help();
                    }
                }
                """));

        Result result = check("missing", Scope.MAIN, classes);

        assertThat(lines(result.denied())).containsExactly(caller + " uses org.example.unreviewed.Helper [provider] "
                + "org.example.unreviewed.Helper has no provider: no artifact on any of the four classpaths provides "
                + "it, and it is not in a JDK module");
    }

    @Test
    void anExternalClassUnderTheApiNamespaceStillNeedsAProvider(@TempDir Path dir) throws IOException {
        String target = ExternalImportPolicy.API + ".quotes.api.UnreviewedHelper";
        String caller = FIXTURES + "namespace.quotes.internal.application.UsesHelper";
        Path external = compile(dir.resolve("external"), List.of(), List.of(), Map.of(target,
                "public final class UnreviewedHelper { public static void help() { } }"));
        Path classes = compile(dir.resolve("caller"), List.of(external), List.of(), Map.of(caller,
                "public final class UsesHelper { void use() { " + target + ".help(); } }"));

        Result missing = check("namespace", Scope.MAIN, classes);
        assertThat(missing.denied()).extracting(Use::target, Use::rule).containsExactly(tuple(target, "provider"));
    }

    @Test
    void anExternalFileUnderTheApiNamespaceStillNeedsReview(@TempDir Path dir) throws IOException {
        String target = ExternalImportPolicy.API + ".quotes.api.UnreviewedHelper";
        String caller = FIXTURES + "namespace.quotes.internal.application.UsesHelper";
        Path external = compile(dir.resolve("external"), List.of(), List.of(), Map.of(target,
                "public final class UnreviewedHelper { public static void help() { } }"));
        Path classes = compile(dir.resolve("caller"), List.of(external), List.of(), Map.of(caller,
                "public final class UsesHelper { void use() { " + target + ".help(); } }"));
        ProviderIndex added = INDEX.adding(new ProviderIndex.Provider("unknown", "unreviewed.jar", "",
                "unreviewed.jar", null, Set.of("compileClasspath"), Set.of(target)));

        Result result = check(new ExternalImportPolicy(added, reviewed(), JDK), "namespace-file", "namespace",
                Scope.MAIN, classes);
        assertThat(result.denied()).extracting(Use::target, Use::rule).containsExactly(tuple(target, "allowlist"));
    }

    @Test
    void anArtifactCollidingWithAnUnusedMainOutputFails() {
        assertOutputCollision("io.github.stigjohnny.shipping.tenants.api.TenantId", "runtimeClasspath");
    }

    @Test
    void anArtifactCollidingWithAnUnusedTestOutputFails() {
        assertOutputCollision(getClass().getName(), "testRuntimeClasspath");
    }

    private void assertOutputCollision(String target, String configuration) {
        ProviderIndex added = INDEX.adding(new ProviderIndex.Provider("unknown", "collision.jar", "",
                "collision.jar", null, Set.of(configuration), Set.of(target)));
        ExternalImportPolicy colliding = new ExternalImportPolicy(added, reviewed(), JDK);

        assertThatThrownBy(() -> check(colliding, "collision", "legitimate", Scope.MAIN, fixture("legitimate")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("collides with API output")
                .hasMessageContaining(target).hasMessageContaining(configuration);
    }

    @Test
    void aClassThatTwoArtifactsProvideFails() {
        // The real classpaths have no such class (noClassHasTwoProvidersOnOneClasspath), so an artifact is added that
        // copies one of Spring's classes and one of the JDK's.
        ProviderIndex shadowed = INDEX.adding(new ProviderIndex.Provider("maven", "org.example:shadow-context", "1.0",
                "shadow-context-1.0.jar", null, Set.of("compileClasspath"),
                Set.of("org.springframework.context.ApplicationEventPublisher", "java.time.Clock")));

        Result result = check(new ExternalImportPolicy(shadowed, reviewed(), JDK), "ambiguous", "legitimate",
                Scope.MAIN, fixture("legitimate"));

        assertThat(lines(result.denied())).containsExactly(
                ISSUE_QUOTE + " uses java.time.Clock [jdk-module] java.time.Clock is in the JDK module java.base and "
                        + "also in maven org.example:shadow-context:1.0: two providers",
                ISSUE_QUOTE + " uses org.springframework.context.ApplicationEventPublisher [provider] "
                        + "org.springframework.context.ApplicationEventPublisher is ambiguous on compileClasspath: "
                        + "maven org.example:shadow-context:1.0 and maven org.springframework:spring-context:7.0.10 "
                        + "each provide it");
    }

    @Test
    void aClassInAPackageWithoutAWhitelistFails() {
        // HexagonalLayeringTest's fixture: a class in quotes.internal.util, which is none of a module's five packages.
        String root = getClass().getPackageName() + ".fixtures.layering.extrapackage";

        Result result = policy().check("extrapackage", Scope.MAIN, root, List.of(ClassDirectories.fixture(root)));
        ExternalImportReport.write(result, FIXTURE_REPORTS, inputs());

        assertThat(result.denied()).extracting(Use::target).containsExactly("java.lang.Object", "java.lang.Record",
                "java.lang.String");
        assertThat(result.denied()).extracting(Use::rule, Use::message).containsOnly(tuple("whitelist", root
                + ".quotes.internal.util.Strings is in no area that has a whitelist: main classes have none outside "
                + "the module layers listed in ExternalImportWhitelists"));
    }

    @Test
    void aReviewedArtifactThatTheCallersLayerMayNotUseFails() {
        Result result = check("wronglayer", Scope.MAIN, fixture("wronglayer"));

        assertThat(result.uses()).filteredOn(Use::denied).extracting(Use::provider, Use::review)
                .containsExactly(tuple("maven org.springframework:spring-tx:7.0.10",
                        "security/reviews/maven--spring-framework--7.0.10.md"));
        assertThat(lines(result.denied())).containsExactly(FIXTURES + "wronglayer.quotes.api.QuoteCommands uses "
                + "org.springframework.transaction.annotation.Transactional [whitelist] "
                + "org.springframework.transaction.annotation.Transactional is not on the module root and api "
                + "whitelist");
    }

    @Test
    void aClassFromAJarWithoutACoordinateFails() {
        // A JAR added with files(...) has no coordinate, so no lockfile names it and PolicyCheck cannot see it.
        ProviderIndex withFile = INDEX.changing(SPRING_CONTEXT, provider -> new ProviderIndex.Provider("unknown",
                provider.file(), "", provider.file(), provider.sha256(), provider.configurations(),
                provider.classes()));

        Result result = check(new ExternalImportPolicy(withFile, reviewed(), JDK), "file-dependency", "legitimate",
                Scope.MAIN, fixture("legitimate"));

        assertThat(lines(result.denied())).containsExactly(ISSUE_QUOTE + " uses org.springframework.context."
                + "ApplicationEventPublisher [allowlist] unknown spring-context-7.0.10.jar is neither a Maven artifact "
                + "nor a project of this build: no review covers it");
    }

    @Test
    void anArtifactWhoseBytesDifferFromTheReviewedOnesFails() {
        String other = "0".repeat(64);
        ProviderIndex changed = INDEX.changing(SPRING_CONTEXT, provider -> new ProviderIndex.Provider(provider.kind(),
                provider.coordinate(), provider.version(), provider.file(), other, provider.configurations(),
                provider.classes()));

        Result result = check(new ExternalImportPolicy(changed, reviewed(), JDK), "changed-bytes", "legitimate",
                Scope.MAIN, fixture("legitimate"));

        assertThat(lines(result.denied())).containsExactly(ISSUE_QUOTE + " uses org.springframework.context."
                + "ApplicationEventPublisher [allowlist] maven org.springframework:spring-context:7.0.10: "
                + "spring-context-7.0.10.jar has the SHA-256 " + other + ", not the reviewed "
                + "f0065166b6a5101a871ab8702d6c4011cf39faed370a64e7ef14a3ad2bf2a99a");
    }

    @Test
    void anExpiredReviewFails() {
        ExternalImportPolicy later = new ExternalImportPolicy(INDEX, ReviewedArtifacts.read(ReviewedArtifacts.ALLOWLIST,
                LocalDate.parse("2027-05-07")), JDK);

        Result result = check(later, "expired", "legitimate", Scope.MAIN, fixture("legitimate"));

        String expired = ": its review security/reviews/maven--spring-framework--7.0.10.md expired on 2027-05-06";
        assertThat(lines(result.denied())).containsExactly(
                ISSUE_QUOTE + " uses org.springframework.context.ApplicationEventPublisher [allowlist] maven "
                        + "org.springframework:spring-context:7.0.10" + expired,
                ISSUE_QUOTE + " uses org.springframework.transaction.annotation.Transactional [allowlist] maven "
                        + "org.springframework:spring-tx:7.0.10" + expired);
    }

    @Test
    void aClassFileThatArchUnitCannotReadFailsTheCheck(@TempDir Path dir) throws IOException {
        // ArchUnit skips a class file it cannot parse without failing; its uses would then be missed.
        String fine = FIXTURES + "unreadable.quotes.internal.domain.Fine";
        Path classes = compile(dir, List.of(), List.of(), Map.of(fine, "public final class Fine {\n}\n"));
        Path broken = Files.write(classes.resolve(fine.replace('.', '/') + ".class").resolveSibling("Broken.class"),
                new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0});

        assertThatThrownBy(() -> policy().check("unreadable", Scope.MAIN, FIXTURES + "unreadable", List.of(classes)))
                .isInstanceOf(IllegalStateException.class).hasMessage("ArchUnit did not import these class files, so "
                        + "their uses would be missed: " + List.of(broken.toAbsolutePath().normalize()));
    }

    private Result check(String fixture, Scope scope, Path classes) {
        return check(policy(), fixture, fixture, scope, classes);
    }

    /**
     * Checks a fixture's classes as the API's main or test classes, with the fixture's base package standing for the
     * API's, and writes the report under the name.
     */
    private static Result check(ExternalImportPolicy policy, String name, String fixture, Scope scope, Path classes) {
        Result result = policy.check(name, scope, FIXTURES + fixture, List.of(classes));
        ExternalImportReport.write(result, FIXTURE_REPORTS, inputs());
        return result;
    }

    private static Path fixture(String name) {
        return ClassDirectories.fixture(FIXTURES + name);
    }

    /**
     * Compiles fixture sources with the JDK's compiler. Each source is named by its class in the fixture packages, or
     * by a stub's own name, and the class files go to {@code directory/classes}.
     */
    private static Path compile(Path directory, List<Path> classPath, List<String> options,
                                Map<String, String> sources) throws IOException {
        Path classes = directory.resolve("classes");
        List<String> arguments = new ArrayList<>(List.of("-d", classes.toString(), "-proc:none", "-Xlint:none"));
        if (!classPath.isEmpty()) {
            arguments.addAll(List.of("-cp", classPath.stream().map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator))));
        }
        arguments.addAll(options);
        for (Map.Entry<String, String> source : sources.entrySet()) {
            String className = source.getKey();
            Path file = directory.resolve("src").resolve(className.replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "package " + ExternalImportPolicy.packageOf(className) + ";\n\n"
                    + source.getValue(), StandardCharsets.UTF_8);
            arguments.add(file.toString());
        }
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit = ToolProvider.getSystemJavaCompiler().run(null, null, errors, arguments.toArray(String[]::new));
        assertThat(exit).as(errors.toString(StandardCharsets.UTF_8)).isZero();
        return classes;
    }

    private static Map<String, String> inputs() {
        return Map.of("allowlist", ExternalImportReport.sha256(ReviewedArtifacts.ALLOWLIST),
                "providerIndex", ExternalImportReport.sha256(Path.of(System.getProperty(ProviderIndex.PROPERTY))),
                "jdk", JDK.version());
    }

    private static List<String> lines(List<Use> uses) {
        return uses.stream().map(use -> use.caller() + " uses " + use.target() + " [" + use.rule() + "] "
                + use.message()).toList();
    }
}
