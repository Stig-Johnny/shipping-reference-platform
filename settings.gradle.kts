// The build reads its plugins only from build-logic/ (our own code) and its libraries only from Maven Central.
// Every resolved artifact is locked (*.lockfile) and checksum-verified (gradle/verification-metadata.xml), and every
// one has a review in security/reviews/ and an entry in security/allowlist.json. See docs/adr/0007.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        // No plugin portal: build-logic/ is the only plugin source.
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

rootProject.name = "shipping-reference-platform"

include(":libs:c4-contract-kit")
include(":tools:docs")
