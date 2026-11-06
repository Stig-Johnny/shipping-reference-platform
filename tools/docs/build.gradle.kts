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

// The docs tool runs as a plain JavaExec on its own runtime classpath: no new configuration, nothing else resolved.
// Each run has a timeout (the tool also stops itself after ten minutes), and any exception or error fails the task.
val repositoryRoot: String = rootDir.absolutePath
val testReports: String = layout.buildDirectory.dir("test-results/test").get().asFile.absolutePath

fun JavaExec.docsTool(command: String) {
    group = "documentation"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "io.github.stigjohnny.docs.DocsTool"
    timeout = Duration.ofMinutes(15)
    // The tool's tests are evidence for the capabilities it reports (docs/capabilities/capabilities.yaml), so their
    // JUnit reports must be from this build.
    dependsOn(tasks.test)
    args(command, "--repo", repositoryRoot, "--test-results", testReports)
}

tasks.register<JavaExec>("generate") {
    description = "Regenerates docs/architecture/, BRAIN.md and docs/brain/ from the repository's sources."
    docsTool("generate")
}

val checkGenerated = tasks.register<JavaExec>("checkGenerated") {
    description = "Regenerates the committed generated files in memory and fails on any byte difference."
    docsTool("check-generated")
}

// The site: build/site, relative links only, rebuilt from scratch on every run. The revision is GITHUB_SHA in CI.
val siteDirectory: String = layout.buildDirectory.dir("site").get().asFile.absolutePath
val sourceRevision: String = providers.environmentVariable("GITHUB_SHA").orElse("").get()

val site = tasks.register<JavaExec>("site") {
    description = "Builds the public documentation site into build/site."
    docsTool("site")
    args("--out", siteDirectory, "--revision", sourceRevision)
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
