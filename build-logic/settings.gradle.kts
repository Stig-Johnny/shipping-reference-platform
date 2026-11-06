// The included build that holds this repository's convention plugins, written in plain Java against the Gradle API.
// It needs no plugin and no library from outside the Gradle distribution.
dependencyResolutionManagement {
    repositories {
        // Nothing to resolve: java-gradle-plugin and gradleApi() ship with Gradle.
    }
}

rootProject.name = "build-logic"
