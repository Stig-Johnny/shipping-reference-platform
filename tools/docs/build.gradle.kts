// tools/docs: generates the architecture pages, BRAIN.md and the public documentation site from the model, the API
// contract and the records in this repository. It runs at build time only: none of its dependencies is published or
// shipped. They were reviewed for this scope (security/reviews/maven--docs-tool-scope-extension.md,
// maven--snakeyaml-engine--3.2.md, maven--docs-tool-test-scope-extension.md). The site's one browser script is the
// vendored Mermaid bundle under vendor/ (vendored-js--mermaid--11.17.2.md); the build never runs it.
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
