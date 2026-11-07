// The shipping API: a Spring Boot modular monolith (Spring Modulith modules, hexagonal inside each module).
// Every dependency below is reviewed in security/reviews/ and listed in security/allowlist.json, with the exact
// starters those reviews approved: webmvc (never the deprecated -web), spring-modulith-test (never -starter-test,
// which pulls the rejected -docs), spring-security-test with spring-boot-security-test (never the security test
// starter). No Spring Boot Gradle plugin: the BOMs are plain Gradle platforms.
plugins {
    application
    id("srp.java-conventions")
}

application {
    mainClass = "io.github.stigjohnny.shipping.ShippingApplication"
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation(libs.jspecify)
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(project(":libs:c4-contract-kit"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.modulith:spring-modulith-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-security-test")
    testImplementation(libs.json.schema.validator)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    constraints {
        // Spring Modulith 2.1.2 asks for ArchUnit 1.4.2; the repository reviews one ArchUnit, the library's 1.5.1.
        implementation(libs.archunit) { because("one reviewed ArchUnit version (security/reviews/maven--archunit--1.5.1.md)") }
        testImplementation(libs.archunit) { because("one reviewed ArchUnit version (security/reviews/maven--archunit--1.5.1.md)") }
        // Jackson 3.x: Spring Boot 4.1.2 manages 3.1.7, which is inside GHSA-m8xf-2cr7-77ph / CVE-2026-110879
        // (critical; FasterXML, 2026-10-20; patched in 3.1.8). Every configuration, main and test, uses 3.1.8, which
        // also keeps json-schema-validator's condition that tests run production's Jackson. Boot's jackson-bom.version
        // property has no effect with plain Gradle platforms, so the override is this constraint.
        for (module in listOf("tools.jackson.core:jackson-core", "tools.jackson.core:jackson-databind",
                "tools.jackson.dataformat:jackson-dataformat-yaml", "tools.jackson:jackson-bom")) {
            for (configuration in listOf("implementation", "testImplementation")) {
                add(configuration, module) {
                    version { strictly("3.1.8") }
                    because("GHSA-m8xf-2cr7-77ph: security/reviews/maven--jackson--3.1.8.md")
                }
            }
        }
    }
}

configurations.configureEach {
    // Rejected (security/reviews/maven--logging--…): it duplicates commons-logging's classes, which Spring Framework 7
    // already routes to SLF4J. spring-modulith-events-core still asks for it.
    exclude(group = "org.slf4j", module = "jcl-over-slf4j")
}

tasks.withType<Test>().configureEach {
    // Ryuk (Testcontainers' cleanup container) is rejected: it needs a privileged container holding the Docker socket.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
    // Testcontainers' startup checks would run another image (alpine) with a bind mount; the runner review turns
    // them off. The database harness, src/test/java/.../database/ReviewedPostgres.java, refuses to start without both.
    environment("TESTCONTAINERS_CHECKS_DISABLE", "true")
}

// The database tests, tagged "database", start the reviewed PostgreSQL through ReviewedPostgres, which refuses to
// start it on an engine that no review approves. They run in their own task, databaseTest, which CI's java check and
// the pages workflow's test job run explicitly; check does not, so check needs only the reviewed JDK. Missing engine
// prerequisites fail databaseTest; a test is never skipped for want of an engine.
tasks.test {
    useJUnitPlatform { excludeTags("database") }
}
tasks.register<Test>("databaseTest") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("database") }
}

// The architecture tests read the model and the compiled classes as files. ArchitectureConformanceTest checks every
// main class-output directory against architecture/workspace.dsl, and HexagonalLayeringTest imports the same
// directories, which reach them as the system property shipping.mainClassDirectories. Declared as an input, a change
// to the model reruns the tests instead of reusing their earlier result, as in libs/c4-contract-kit.
tasks.test {
    inputs.files(rootDir.resolve("architecture/workspace.dsl")).withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("model")
    val mainClassDirectories: FileCollection = sourceSets.main.get().output.classesDirs
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("-Dshipping.mainClassDirectories=" + mainClassDirectories.files.joinToString(File.pathSeparator))
    })
}
