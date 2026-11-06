// c4-contract-kit: compares a Structurizr C4 model with the code's static dependencies, in both directions.
// It has no Spring dependency. Its own tests are mutation-tested (PIT), and it checks its own architecture.
plugins {
    `java-library`
    id("srp.mutation-testing")
}

dependencies {
    // The parser and the bytecode importer are implementation details: no type of theirs is in the public API.
    implementation(libs.structurizr.dsl)
    implementation(libs.archunit)
    // JSpecify's annotations are part of the API contract (@NullMarked packages).
    api(libs.jspecify)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)

    constraints {
        // Structurizr brings Guava 33.6.0-jre, which has an advisory (GHSA-xxph-c9ww-hj94). The fixed release is
        // reviewed; the lockfile and security/allowlist.json record the result.
        implementation(libs.guava) {
            because("GHSA-xxph-c9ww-hj94 affects the Guava 33.6.0-jre that Structurizr 6.2.4 depends on")
        }
    }
}

mutationTesting {
    targetClasses = "io.github.stigjohnny.c4contractkit.*"
}
