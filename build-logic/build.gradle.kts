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
