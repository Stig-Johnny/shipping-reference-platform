plugins {
    id("java-gradle-plugin")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

gradlePlugin {
    plugins {
        create("javaConventions") {
            id = "srp.java-conventions"
            implementationClass = "io.github.stigjohnny.build.JavaConventionsPlugin"
        }
        create("mutationTesting") {
            id = "srp.mutation-testing"
            implementationClass = "io.github.stigjohnny.build.MutationTestingPlugin"
        }
    }
}

// This harness uses only the JDK and the Gradle API already used by the plugin.
val providerIdentityTests = sourceSets.create("providerIdentityTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += compileClasspath
}
val providerIdentityTest = tasks.register<JavaExec>("providerIdentityTest") {
    classpath = providerIdentityTests.runtimeClasspath
    mainClass = "io.github.stigjohnny.build.ProviderIdentityTest"
}
tasks.check { dependsOn(providerIdentityTest) }
