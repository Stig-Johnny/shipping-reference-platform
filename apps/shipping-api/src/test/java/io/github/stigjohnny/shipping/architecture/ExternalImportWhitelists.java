package io.github.stigjohnny.shipping.architecture;

import io.github.stigjohnny.shipping.architecture.ExternalImportPolicy.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Which classes outside the API each area of it may use, each with its reason. The lists come from what the code uses
 * today, and ExternalImportPolicyTest fails on an entry that no class uses; a class that no list names is a failure.
 * The production layers and the JDK are listed class by class. A whole package is listed only for a test library
 * whose types vary with the value under test (AssertJ's assertions, ArchUnit's rule API, the contract kit's api).
 */
final class ExternalImportWhitelists {

    /** A part of the API's main or test classes that has its own whitelist. */
    enum Area {
        ROOT("root"),
        MODULE_ROOT_AND_API("module root and api"),
        DOMAIN("internal.domain"),
        APPLICATION("internal.application"),
        ADAPTER_IN_WEB("internal.adapter.in.web"),
        ADAPTER_OUT_JDBC("internal.adapter.out.jdbc"),
        MODULE_TESTS("module tests"),
        ARCHITECTURE_TESTS("architecture tests"),
        FIXTURES("architecture fixtures");

        private final String label;

        Area(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /** One class, or every class of one package (not its sub-packages), with the reason an area may use it. */
    record Allowed(Area area, String name, boolean wholePackage, String reason) {

        boolean allows(String className) {
            return wholePackage ? ExternalImportPolicy.packageOf(className).equals(name) : className.equals(name);
        }
    }

    private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";
    private static final Map<Area, List<Allowed>> WHITELISTS = whitelists(List.of(
            // The root package: ShippingApplication and the package's package-info (the model's contract.isolated).
            types(Area.ROOT, "the Spring Boot entry point", "org.springframework.boot.SpringApplication",
                    "org.springframework.boot.autoconfigure.SpringBootApplication"),
            types(Area.ROOT, "JSpecify: every package is @NullMarked (HexagonalLayeringTest)", NULL_MARKED),
            types(Area.ROOT, "the main method's types", "java.lang.Object", "java.lang.String"),

            // A module's root package (package-info only) and its api package: the values, identifiers, lookups and
            // events other modules see. JDK value types, JSpecify and Spring Modulith's two markers only.
            types(Area.MODULE_ROOT_AND_API, "Spring Modulith's markers in package-info: @ApplicationModule on the "
                    + "module, @NamedInterface on api", "org.springframework.modulith.ApplicationModule",
                    "org.springframework.modulith.NamedInterface"),
            types(Area.MODULE_ROOT_AND_API, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.MODULE_ROOT_AND_API, "records, enums and the checks in their constructors",
                    "java.lang.Object", "java.lang.Record", "java.lang.Enum", "java.lang.String",
                    "java.util.Objects", "java.lang.IllegalArgumentException", "java.lang.ArithmeticException",
                    "java.lang.RuntimeException"),
            types(Area.MODULE_ROOT_AND_API, "money: an amount in a currency, rounded once (Money)",
                    "java.math.BigDecimal", "java.math.RoundingMode", "java.util.Currency"),
            types(Area.MODULE_ROOT_AND_API, "identifiers and times", "java.util.UUID", "java.time.Instant"),
            types(Area.MODULE_ROOT_AND_API, "postal code formats (Place)", "java.util.regex.Pattern",
                    "java.util.regex.Matcher"),

            // internal.domain: the JDK's value, collection and time types and JSpecify, nothing else. No Spring,
            // Jakarta or JDBC, as HexagonalLayeringTest also says, and no I/O, threads, reflection or clock reads.
            types(Area.DOMAIN, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.DOMAIN, "records, enums, sealed outcomes and their checks", "java.lang.Object",
                    "java.lang.Record", "java.lang.Enum", "java.lang.String", "java.util.Objects",
                    "java.lang.IllegalArgumentException", "java.lang.IllegalStateException",
                    "java.lang.MatchException", "java.lang.FunctionalInterface", "java.lang.Math"),
            types(Area.DOMAIN, "the synthetic tariff's money arithmetic", "java.math.BigDecimal",
                    "java.math.RoundingMode", "java.util.Currency"),
            types(Area.DOMAIN, "quote lifetimes, as instants and durations passed in", "java.time.Instant",
                    "java.time.Duration", "java.time.temporal.ChronoUnit"),
            types(Area.DOMAIN, "values and collections", "java.util.List", "java.util.Set", "java.util.Optional",
                    "java.util.UUID", "java.util.stream.Stream"),
            types(Area.DOMAIN, "formats of idempotency keys, references, tracking numbers and fingerprints",
                    "java.util.regex.Pattern", "java.util.regex.Matcher"),
            types(Area.DOMAIN, "recipient text checked and counted in code points (Recipient)", "java.lang.Character",
                    "java.util.stream.IntStream"),
            types(Area.DOMAIN, "the booking request's fingerprint: SHA-256 over strictly encoded UTF-8, in memory "
                    + "(RequestFingerprint)", "java.security.MessageDigest", "java.security.NoSuchAlgorithmException",
                    "java.util.HexFormat", "java.nio.ByteBuffer", "java.nio.CharBuffer", "java.nio.charset.Charset",
                    "java.nio.charset.CharsetEncoder", "java.nio.charset.CharacterCodingException",
                    "java.nio.charset.CodingErrorAction", "java.nio.charset.StandardCharsets"),

            // internal.application: the domain's kinds of types, the injected clock, the tenant scope, and the two
            // Spring APIs the services use: events published in the transaction that changes state (ADR 0006).
            types(Area.APPLICATION, "Spring: publishing module events, inside the state change's transaction",
                    "org.springframework.context.ApplicationEventPublisher",
                    "org.springframework.transaction.annotation.Transactional"),
            types(Area.APPLICATION, "JSpecify: every package is @NullMarked", NULL_MARKED),
            types(Area.APPLICATION, "objects, checks, outcomes and one-method ports", "java.lang.Object",
                    "java.lang.String", "java.util.Objects", "java.util.Optional", "java.lang.MatchException",
                    "java.lang.Throwable", "java.lang.FunctionalInterface"),
            types(Area.APPLICATION, "the injected clock (never Instant.now())", "java.time.Clock",
                    "java.time.Instant"),
            types(Area.APPLICATION, "the caller's tenant, bound to a scope (ThreadTenantContext)",
                    "java.lang.ScopedValue", "java.lang.ScopedValue$Carrier", "java.lang.Runnable",
                    "java.util.function.Supplier"),

            // internal.adapter.in.web and internal.adapter.out.jdbc: no adapter exists yet, so their lists are
            // empty. The first adapter adds the classes it uses, each with its reason.

            // Tests in a module's packages: JUnit's annotations by name (so @Disabled is not among them), AssertJ,
            // and the JDK types the tests use.
            types(Area.MODULE_TESTS, "JUnit Jupiter: the test and parameterised-test annotations in use",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.params.ParameterizedTest",
                    "org.junit.jupiter.params.provider.CsvSource", "org.junit.jupiter.params.provider.MethodSource",
                    "org.junit.jupiter.params.provider.NullSource", "org.junit.jupiter.params.provider.ValueSource",
                    "org.junit.jupiter.params.provider.Arguments"),
            packages(Area.MODULE_TESTS, "AssertJ's assertions: assertThat returns an assertion type per value",
                    "org.assertj.core.api"),
            types(Area.MODULE_TESTS, "the language's types and exceptions", "java.lang.Object", "java.lang.Record",
                    "java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Throwable",
                    "java.lang.AssertionError", "java.lang.IllegalArgumentException",
                    "java.lang.IllegalStateException", "java.lang.NullPointerException", "java.lang.MatchException",
                    "java.lang.InterruptedException", "java.lang.Character"),
            types(Area.MODULE_TESTS, "values, money and fixed times", "java.math.BigDecimal",
                    "java.math.RoundingMode", "java.util.Currency", "java.util.UUID", "java.util.Objects",
                    "java.util.Optional", "java.time.Clock", "java.time.Duration", "java.time.Instant",
                    "java.time.ZoneOffset"),
            types(Area.MODULE_TESTS, "collections, streams and deterministic ID sequences", "java.util.List",
                    "java.util.ArrayList", "java.util.Map", "java.util.LinkedHashMap", "java.util.Iterator",
                    "java.util.stream.Stream", "java.util.stream.IntStream",
                    "java.util.concurrent.atomic.AtomicLong", "java.util.concurrent.atomic.AtomicReference",
                    "java.util.function.Predicate"),
            types(Area.MODULE_TESTS, "the expected SHA-256 values of RequestFingerprintTest",
                    "java.security.MessageDigest", "java.security.NoSuchAlgorithmException", "java.util.HexFormat",
                    "java.nio.charset.StandardCharsets"),
            types(Area.MODULE_TESTS, "fixed-seed samples of the property tests", "java.util.SplittableRandom",
                    "java.util.random.RandomGenerator"),
            types(Area.MODULE_TESTS, "a second thread, for ThreadTenantContextTest", "java.lang.Thread",
                    "java.lang.Thread$Builder$OfPlatform"),

            // The architecture tests: they read class files, models, the provider index and the allowlist.
            packages(Area.ARCHITECTURE_TESTS, "ArchUnit: importing explicit class directories and the rule API "
                    + "(not the library or freeze packages, which its review excludes)",
                    "com.tngtech.archunit.core.domain", "com.tngtech.archunit.core.importer",
                    "com.tngtech.archunit.lang", "com.tngtech.archunit.lang.syntax",
                    "com.tngtech.archunit.lang.syntax.elements"),
            types(Area.ARCHITECTURE_TESTS, "ArchUnit: a thread-local configuration without classpath resolution",
                    "com.tngtech.archunit.ArchConfiguration"),
            packages(Area.ARCHITECTURE_TESTS, "c4-contract-kit's public API, its only exported package (ADR 0002)",
                    "io.github.stigjohnny.c4contractkit.api"),
            types(Area.ARCHITECTURE_TESTS, "Spring Modulith's module model, verified by ModularityTest",
                    "org.springframework.modulith.core.ApplicationModule",
                    "org.springframework.modulith.core.ApplicationModuleDependencies",
                    "org.springframework.modulith.core.ApplicationModuleIdentifier",
                    "org.springframework.modulith.core.ApplicationModules",
                    "org.springframework.modulith.core.Violations"),
            types(Area.ARCHITECTURE_TESTS, "Jackson's tree model: reading the provider index and the allowlist",
                    "tools.jackson.databind.JsonNode", "tools.jackson.databind.json.JsonMapper",
                    "tools.jackson.databind.json.JsonMapper$Builder", "tools.jackson.core.StreamReadFeature"),
            types(Area.ARCHITECTURE_TESTS, "JSpecify: HexagonalLayeringTest checks @NullMarked", NULL_MARKED),
            types(Area.ARCHITECTURE_TESTS, "JUnit Jupiter: tests and temporary directories",
                    "org.junit.jupiter.api.Test", "org.junit.jupiter.api.io.TempDir"),
            packages(Area.ARCHITECTURE_TESTS, "AssertJ's assertions: assertThat returns an assertion type per value",
                    "org.assertj.core.api"),
            types(Area.ARCHITECTURE_TESTS, "the JDK's compiler, for fixtures the build's compiler refuses",
                    "javax.tools.ToolProvider", "javax.tools.JavaCompiler"),
            types(Area.ARCHITECTURE_TESTS, "the JDK's module descriptors: which module holds and exports a package",
                    "java.lang.module.ModuleFinder", "java.lang.module.ModuleReference",
                    "java.lang.module.ModuleDescriptor", "java.lang.module.ModuleDescriptor$Exports",
                    "java.lang.Runtime", "java.lang.Runtime$Version"),
            types(Area.ARCHITECTURE_TESTS, "reading class directories, models and reports as files",
                    "java.io.File", "java.io.IOException", "java.io.UncheckedIOException",
                    "java.io.ByteArrayOutputStream", "java.nio.file.Files", "java.nio.file.Path",
                    "java.nio.charset.StandardCharsets", "java.net.URL", "java.net.URISyntaxException",
                    "java.lang.ClassLoader", "java.lang.Class", "java.lang.System"),
            types(Area.ARCHITECTURE_TESTS, "SHA-256 digests of the reports' inputs", "java.security.MessageDigest",
                    "java.security.NoSuchAlgorithmException", "java.util.HexFormat"),
            types(Area.ARCHITECTURE_TESTS, "the language's types and exceptions", "java.lang.Object",
                    "java.lang.Record", "java.lang.Enum", "java.lang.String", "java.lang.StringBuilder",
                    "java.lang.Integer", "java.lang.Throwable", "java.lang.AssertionError",
                    "java.lang.IllegalStateException", "java.lang.MatchException"),
            types(Area.ARCHITECTURE_TESTS, "collections, streams, text and dates", "java.util.Collection",
                    "java.util.List", "java.util.ArrayList", "java.util.Set", "java.util.HashSet",
                    "java.util.LinkedHashSet", "java.util.TreeSet", "java.util.Map", "java.util.Map$Entry",
                    "java.util.HashMap", "java.util.TreeMap", "java.util.EnumMap", "java.util.Collections",
                    "java.util.Iterator", "java.util.Comparator",
                    "java.util.Objects", "java.util.Optional", "java.util.Locale", "java.util.function.Predicate",
                    "java.util.function.UnaryOperator", "java.util.stream.Stream", "java.util.stream.Collectors",
                    "java.util.regex.Pattern", "java.util.regex.Matcher", "java.time.LocalDate",
                    "java.time.ZoneOffset"),

            // The architecture tests' fixtures. Each class here is what one fixture needs to break one rule, and the
            // fixtures' own tests check them as the main classes they stand for.
            types(Area.FIXTURES, "JacksonTypingTest's fixtures: @JsonTypeInfo with a class id",
                    "com.fasterxml.jackson.annotation.JsonTypeInfo",
                    "com.fasterxml.jackson.annotation.JsonTypeInfo$Id"),
            types(Area.FIXTURES, "HexagonalLayeringTest's fixture: a Spring stereotype on a domain class",
                    "org.springframework.stereotype.Component"),
            types(Area.FIXTURES, "this test's fixtures: Spring in the domain, @Transactional on an api type, and "
                    + "the legitimate application service", "org.springframework.context.ApplicationEventPublisher",
                    "org.springframework.transaction.annotation.Transactional", "java.time.Clock"),
            types(Area.FIXTURES, "this test's fixture of production code that uses a test-only artifact",
                    "org.assertj.core.api.Assertions", "org.assertj.core.api.AbstractIntegerAssert"),
            types(Area.FIXTURES, "records, objects and checks", "java.lang.Object", "java.lang.Record",
                    "java.lang.String", "java.util.Objects")));

    private ExternalImportWhitelists() {
    }

    private static List<Allowed> types(Area area, String reason, String... names) {
        return Stream.of(names).map(name -> new Allowed(area, name, false, reason)).toList();
    }

    private static List<Allowed> packages(Area area, String reason, String... names) {
        return Stream.of(names).map(name -> new Allowed(area, name, true, reason)).toList();
    }

    private static Map<Area, List<Allowed>> whitelists(List<List<Allowed>> lists) {
        Map<Area, List<Allowed>> whitelists = new EnumMap<>(Area.class);
        lists.stream().flatMap(List::stream).forEach(entry -> whitelists
                .computeIfAbsent(entry.area(), area -> new ArrayList<>()).add(entry));
        whitelists.replaceAll((area, entries) -> List.copyOf(entries));
        return Collections.unmodifiableMap(whitelists);
    }

    static List<Allowed> whitelist(Area area) {
        return WHITELISTS.getOrDefault(area, List.of());
    }

    /** Every entry of every area. */
    static List<Allowed> all() {
        return WHITELISTS.values().stream().flatMap(List::stream).toList();
    }

    /**
     * The area of a package below {@code root}. Main classes: the root package, a module's root or {@code api}
     * package, and the four internal layers. Test classes: the architecture tests, their fixtures, and the tests in a
     * module's packages. Any other package has no area, and every class it uses fails.
     */
    static Optional<Area> area(Scope scope, String root, String packageName) {
        if (scope == Scope.TEST) {
            if (packageName.equals(root + ".architecture")) {
                return Optional.of(Area.ARCHITECTURE_TESTS);
            }
            if (packageName.startsWith(root + ".architecture.fixtures.")) {
                return Optional.of(Area.FIXTURES);
            }
            return layer(root, packageName).map(layer -> Area.MODULE_TESTS);
        }
        if (packageName.equals(root)) {
            return Optional.of(Area.ROOT);
        }
        return layer(root, packageName);
    }

    private static Optional<Area> layer(String root, String packageName) {
        if (!packageName.startsWith(root + ".")) {
            return Optional.empty();
        }
        String inModule = packageName.substring(root.length() + 1);
        int dot = inModule.indexOf('.');
        return switch (dot < 0 ? "" : inModule.substring(dot + 1)) {
            case "", "api" -> Optional.of(Area.MODULE_ROOT_AND_API);
            case "internal.domain" -> Optional.of(Area.DOMAIN);
            case "internal.application" -> Optional.of(Area.APPLICATION);
            case "internal.adapter.in.web" -> Optional.of(Area.ADAPTER_IN_WEB);
            case "internal.adapter.out.jdbc" -> Optional.of(Area.ADAPTER_OUT_JDBC);
            default -> Optional.empty();
        };
    }
}
