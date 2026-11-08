// tools/docs: generates the architecture pages, BRAIN.md and the public documentation site from the model, the API
// contract and the records in this repository. It runs at build time only: none of its dependencies is published or
// shipped. They were reviewed for this scope (security/reviews/maven--docs-tool-scope-extension.md,
// maven--snakeyaml-engine--3.2.md, maven--docs-tool-test-scope-extension.md). The site's one browser script is the
// vendored Mermaid bundle under vendor/ (vendored-js--mermaid--11.17.2.md); the build never runs it.
import java.time.Duration

plugins {
    id("srp.java-conventions")
}

dependencies {
    implementation(libs.structurizr.dsl)
    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.snakeyaml.engine)
    implementation(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)

    constraints {
        // The same Guava lift as libs/c4-contract-kit: Structurizr brings 33.6.0-jre (GHSA-xxph-c9ww-hj94).
        implementation(libs.guava) {
            because("GHSA-xxph-c9ww-hj94 affects the Guava 33.6.0-jre that Structurizr 6.2.4 depends on")
        }
        // Without ArchUnit (which the kit has), httpclient5 would resolve slf4j-api 1.7.36, which has no review.
        implementation(libs.slf4j.api) {
            because("2.0.19 is the slf4j-api reviewed with Structurizr's transitives; 1.7.36 is not reviewed")
        }
    }
}

// The tests read the repository's own sources (the documents, the model, the contract, the policy, the allowlist, the
// workflows, the pull request template), so a change to any of them reruns the tests instead of reusing their earlier
// result.
val repositoryInputs = listOf(
    "README.md", "AGENTS.md", "BRAIN.md", "LICENSE", "NOTICE", "SECURITY.md", "THIRD-PARTY-NOTICES.md", "docs",
    "architecture", "contracts", "policy", "security", ".github/workflows", ".github/pull_request_template.md",
    "libs/c4-contract-kit/AGENTS.md", "apps/shipping-api/AGENTS.md", "apps/shipping-web/AGENTS.md",
    "tools/docs/vendor", "tools/docs/publication-scan-exceptions.json",
).map { rootDir.resolve(it) }

tasks.test {
    inputs.files(repositoryInputs).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("repositoryInputs")
}

// The docs tool runs as a plain JavaExec on its own runtime classpath: no new configuration, nothing else resolved.
// Each run has a timeout (the tool also stops itself after ten minutes), and any exception or error fails the task.
val repositoryRoot: String = rootDir.absolutePath

// Evidence is the JUnit reports of this build (docs/capabilities/capabilities.yaml): those of every project with test
// sources, the docs tool's own and, for example, the contract kit's. Their test tasks run first, so that no report is
// left over from an earlier build.
val testedProjects: List<Pair<String, String>> = rootProject.subprojects
    .filter { it.projectDir.resolve("src/test/java").isDirectory }
    .map { it.path to it.layout.buildDirectory.dir("test-results/test").get().asFile.absolutePath }

// The shipping API's database tests run in their own task, databaseTest, which only a reviewed container engine can
// pass, so check needs no engine (apps/shipping-api/build.gradle.kts). Full-evidence mode, -PdatabaseEvidence=true,
// is how CI's java check and the pages workflow's test job build: the docs tasks then depend on databaseTest too, read
// its reports and fail unless they show at least one test run and none failed, errored or skipped. Without it they
// never read those reports, so a database report left over from an earlier run cannot count as evidence.
val databaseEvidence: Boolean = when (val value = providers.gradleProperty("databaseEvidence").orNull) {
    null -> false
    "true" -> true
    else -> throw GradleException("databaseEvidence is true or absent, not '$value'")
}
val databaseTested: List<Pair<String, String>> = listOf(":apps:shipping-api").map {
    it to project(it).layout.buildDirectory.dir("test-results/databaseTest").get().asFile.absolutePath
}

fun JavaExec.docsTool(command: String) {
    group = "documentation"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.stigjohnny.docs.DocsTool"
    timeout = Duration.ofMinutes(15)
    dependsOn(testedProjects.map { (path, _) -> "$path:test" })
    args(command, "--repo", repositoryRoot)
    testedProjects.forEach { (_, reports) -> args("--test-results", reports) }
    if (databaseEvidence) {
        dependsOn(databaseTested.map { (path, _) -> "$path:databaseTest" })
        databaseTested.forEach { (_, reports) -> args("--required-results", reports) }
    }
}

tasks.register<JavaExec>("generate") {
    description = "Regenerates docs/architecture/, BRAIN.md and docs/brain/ from the repository's sources."
    docsTool("generate")
}

val checkGenerated = tasks.register<JavaExec>("checkGenerated") {
    description = "Regenerates the committed generated files in memory and fails on any byte difference."
    docsTool("check-generated")
}

// The site: build/site, relative links only, rebuilt from scratch on every run. Every page names the full commit SHA
// it was built from: GITHUB_SHA in CI, the checked-out commit locally. It is read when the site is built, and the
// tool fails without a full SHA, so a site never names a made-up revision.
val siteDirectory: String = layout.buildDirectory.dir("site").get().asFile.absolutePath
val sourceRevision: Provider<String> = providers.environmentVariable("GITHUB_SHA").orElse(
    providers.exec {
        // This project's directory is inside the same worktree; no computed command argument is needed.
        commandLine("git", "rev-parse", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() },
)

val site = tasks.register<JavaExec>("site") {
    description = "Builds the public documentation site into build/site."
    docsTool("site")
    args("--out", siteDirectory)
    val revision = sourceRevision
    argumentProviders.add(CommandLineArgumentProvider { listOf("--revision", revision.get()) })
}

// check also builds the site, so that a pull request with a broken link, anchor or diagram fails before main.
tasks.named("check") {
    dependsOn(checkGenerated, site)
}

// The publication scan reads build/site as data, as it is, and is not part of check: without its dictionary it fails
// closed. The tool reads PUBLIC_SCAN_DICTIONARY from its own environment when it runs; this build script never reads
// it, and the task is kept out of the configuration cache so that no task state from such a run is stored.
val scanSummary: String = layout.buildDirectory.file("publication-scan/summary.md").get().asFile.absolutePath

tasks.register<JavaExec>("scanSite") {
    group = "documentation"
    description = "Scans build/site before publication; needs PUBLIC_SCAN_DICTIONARY and fails closed without it."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.stigjohnny.docs.DocsTool"
    timeout = Duration.ofMinutes(15)
    args("scan", "--repo", repositoryRoot, "--site", siteDirectory, "--summary", scanSummary)
    mustRunAfter(site)
    notCompatibleWithConfigurationCache("the scan's dictionary must never be stored with the build's state")
}
