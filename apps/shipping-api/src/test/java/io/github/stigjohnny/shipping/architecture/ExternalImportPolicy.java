package io.github.stigjohnny.shipping.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import io.github.stigjohnny.shipping.architecture.ExternalImportWhitelists.Allowed;
import io.github.stigjohnny.shipping.architecture.ExternalImportWhitelists.Area;
import io.github.stigjohnny.shipping.architecture.ReviewedArtifacts.Approved;
import io.github.stigjohnny.shipping.architecture.ReviewedArtifacts.Refused;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The external-import policy: a class outside the API that an API class uses must come from a provider reviewed for
 * that use, and be on the whitelist of the caller's area.
 *
 * <p>The uses are ArchUnit's direct dependencies of each class, read from the class files of explicit directories:
 * dependencies reported by ArchUnit 1.5.1 (docs/gaps.md). Each target outside the actual API outputs is
 * decided once, in this order, and the first rule that fails is its denial:
 * <ol>
 *   <li>{@code jdk-internal}: a class whose name starts with {@code sun.}, {@code com.sun.} or {@code jdk.internal.}.
 *   <li>{@code jdk-module}: a class of a package in one of the JDK's system modules, by the modules' descriptors,
 *       must be exported by its module to every module, and no artifact may provide it as well.
 *   <li>{@code provider}: any other class has exactly one provider in the provider index, on the scope's
 *       configuration: compileClasspath for main classes, testCompileClasspath for test classes.
 *   <li>{@code allowlist}: a Maven provider is approved by security/allowlist.json for :apps:shipping-api on that
 *       configuration, unexpired, for its exact file and, where the review records one, its SHA-256. A verified
 *       repository-owned project output needs no Maven review; any other kind of provider is refused.
 *   <li>{@code whitelist}: the class is on the whitelist of the caller's area (ExternalImportWhitelists).
 * </ol>
 */
final class ExternalImportPolicy {

    static final String API = "io.github.stigjohnny.shipping";
    private static final List<String> JDK_INTERNALS = List.of("sun.", "com.sun.", "jdk.internal.");

    /** Main classes are checked against what they compile against, and so are test classes. */
    enum Scope {
        MAIN("compileClasspath"),
        TEST("testCompileClasspath");

        private final String configuration;

        Scope(String configuration) {
            this.configuration = configuration;
        }

        String configuration() {
            return configuration;
        }

        String label() {
            return name().toLowerCase(Locale.ROOT) + ": " + configuration;
        }
    }

    /** One class outside the API that one class uses, and the decision. */
    record Use(String caller, String area, String target, String provider, String scope, String rule, String review,
               String outcome, String message) {

        static final Comparator<Use> ORDER = Comparator.comparing(Use::caller).thenComparing(Use::target);

        boolean denied() {
            return outcome.equals("denied");
        }
    }

    /** Every use in one set of class directories, with the whitelist entries that allowed some use. */
    record Result(String name, Scope scope, List<Use> uses, Set<Allowed> entriesUsed) {

        List<Use> denied() {
            return uses.stream().filter(Use::denied).toList();
        }
    }

    /** Where a class outside the API comes from, and why it may not be used, if it may not. */
    private record Origin(String provider, String review, String rule, String denial) {
    }

    private static final Map<Scope, Set<String>> API_OUTPUTS = apiOutputs();

    private final ProviderIndex index;
    private final ReviewedArtifacts reviewed;
    private final JdkModules jdk;
    private final Map<String, Origin> origins = new HashMap<>();

    ExternalImportPolicy(ProviderIndex index, ReviewedArtifacts reviewed, JdkModules jdk) {
        this.index = index;
        this.reviewed = reviewed;
        this.jdk = jdk;
    }

    /**
     * Checks every class under the directories. Their areas are taken relative to {@code root}: the API's base package,
     * or the base package of a fixture that stands for it.
     */
    Result check(String name, Scope scope, String root, List<Path> directories) {
        JavaClasses classes = ClassDirectories.read(directories);
        requireEveryClassFile(classes, directories);
        Set<String> internal = new LinkedHashSet<>(API_OUTPUTS.get(scope));
        classes.forEach(javaClass -> internal.add(javaClass.getName()));
        Set<String> allOutputs = new LinkedHashSet<>(API_OUTPUTS.get(Scope.TEST));
        allOutputs.addAll(internal);
        for (ProviderIndex.Provider provider : index.providers()) {
            for (String target : provider.classes()) {
                if (allOutputs.contains(target)) {
                    throw new IllegalStateException(provider.label() + " collides with API output " + target
                            + " on " + provider.configurations().stream().sorted().toList());
                }
            }
        }
        Map<String, Use> uses = new HashMap<>();
        Set<Allowed> entriesUsed = new LinkedHashSet<>();
        for (JavaClass caller : classes) {
            Optional<Area> area = ExternalImportWhitelists.areaOfClass(scope, root, caller.getName());
            for (Dependency dependency : caller.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().isArray()
                        ? dependency.getTargetClass().getBaseComponentType() : dependency.getTargetClass();
                if (target.isPrimitive() || internal.contains(target.getName())) {
                    continue;
                }
                uses.computeIfAbsent(caller.getName() + " " + target.getName(),
                        key -> decide(scope, caller.getName(), area, target.getName(), entriesUsed));
            }
        }
        return new Result(name, scope, uses.values().stream().sorted(Use.ORDER).toList(), Set.copyOf(entriesUsed));
    }

    private Use decide(Scope scope, String caller, Optional<Area> area, String target, Set<Allowed> entriesUsed) {
        Origin origin = origins.computeIfAbsent(scope.name() + " " + target, key -> origin(scope, target));
        String areaName = area.map(Area::label).orElse("none");
        if (!origin.denial().isEmpty()) {
            return new Use(caller, areaName, target, origin.provider(), scope.label(), origin.rule(), origin.review(),
                    "denied", origin.denial());
        }
        if (area.isEmpty()) {
            return new Use(caller, areaName, target, origin.provider(), scope.label(), "whitelist", origin.review(),
                    "denied", caller + " is in no area that has a whitelist: " + scope.name().toLowerCase(Locale.ROOT)
                            + " classes have none outside the module layers listed in ExternalImportWhitelists");
        }
        Optional<Allowed> allowed = ExternalImportWhitelists.whitelist(area.get()).stream()
                .filter(entry -> entry.allows(target)).findFirst();
        if (allowed.isEmpty()) {
            return new Use(caller, areaName, target, origin.provider(), scope.label(), "whitelist", origin.review(),
                    "denied", target + " is not on the " + areaName + " whitelist");
        }
        entriesUsed.add(allowed.get());
        return new Use(caller, areaName, target, origin.provider(), scope.label(),
                areaName + ": " + allowed.get().name(), origin.review(), "allowed", allowed.get().reason());
    }

    private Origin origin(Scope scope, String target) {
        String packageName = packageOf(target);
        Optional<String> module = jdk.module(packageName);
        String jdkProvider = module.map(name -> "jdk " + name).orElse("none");
        for (String internal : JDK_INTERNALS) {
            if (target.startsWith(internal)) {
                return new Origin(jdkProvider, "", "jdk-internal", target + " is a JDK internal (" + internal
                        + "*): refused, whatever its module exports");
            }
        }
        List<ProviderIndex.Provider> providers = index.providers(scope.configuration(), target);
        if (module.isPresent()) {
            if (!providers.isEmpty()) {
                return new Origin(jdkProvider, "", "jdk-module", target + " is in the JDK module " + module.get()
                        + " and also in " + labels(providers) + ": two providers");
            }
            if (!jdk.exportsToAll(packageName)) {
                return new Origin(jdkProvider, "", "jdk-module", target + ": the JDK module " + module.get()
                        + " does not export " + packageName);
            }
            List<String> reviews = reviewed.jdkReviews(jdk.version());
            return new Origin(jdkProvider, reviews.isEmpty() ? "no jdk entry for " + jdk.version()
                    : String.join(", ", reviews), "", "");
        }
        if (providers.isEmpty()) {
            return new Origin("none", "", "provider", missing(scope, target));
        }
        if (providers.size() > 1) {
            return new Origin(labels(providers), "", "provider", target + " is ambiguous on " + scope.configuration()
                    + ": " + labels(providers) + " each provide it");
        }
        ProviderIndex.Provider provider = providers.getFirst();
        return switch (provider.kind()) {
            case "project" -> provider.verifiedRepositoryProject()
                    ? new Origin(provider.label(), "first-party: verified repository-owned output", "", "")
                    : new Origin(provider.label(), "", "allowlist", provider.label()
                            + " is not a verified repository-owned project: no review covers it");
            case "maven" -> switch (reviewed.decide(provider, scope.configuration())) {
                case Approved approved -> new Origin(provider.label(), String.join(", ", approved.reviewRecords()),
                        "", "");
                case Refused refused -> new Origin(provider.label(), "", "allowlist", refused.reason());
            };
            default -> new Origin(provider.label(), "", "allowlist", provider.label() + " is neither a Maven "
                    + "artifact nor a project of this build: no review covers it");
        };
    }

    /** Why a class has no provider on the scope's configuration, naming where else it is, if anywhere. */
    private String missing(Scope scope, String target) {
        List<String> elsewhere = index.configurationsProviding(target);
        if (elsewhere.isEmpty()) {
            return target + " has no provider: no artifact on any of the four classpaths provides it, and it is not "
                    + "in a JDK module";
        }
        Set<String> labels = new TreeSet<>();
        elsewhere.forEach(configuration -> index.providers(configuration, target)
                .forEach(provider -> labels.add(provider.label())));
        boolean testOnly = scope == Scope.MAIN && elsewhere.stream().allMatch(name -> name.startsWith("test"));
        return target + " has no provider on " + scope.configuration() + ": " + String.join(", ", labels)
                + " provides it on " + String.join(", ", elsewhere) + " only" + (testOnly ? ", a test-only artifact"
                : "");
    }

    /**
     * Every class file under the directories must be the source of a class ArchUnit imported: it skips a file it
     * cannot parse, and a second definition of a class, without failing, and their uses would be missed.
     */
    private static void requireEveryClassFile(JavaClasses classes, List<Path> directories) {
        Set<Path> imported = classes.stream().flatMap(javaClass -> javaClass.getSource().stream())
                .map(source -> Path.of(source.getUri()).toAbsolutePath().normalize()).collect(Collectors.toSet());
        List<Path> skipped = new ArrayList<>();
        for (Path directory : directories) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.filter(file -> file.toString().endsWith(".class") && Files.isRegularFile(file))
                        .filter(file -> !file.getFileName().toString().equals("module-info.class"))
                        .map(file -> file.toAbsolutePath().normalize()).filter(file -> !imported.contains(file))
                        .forEach(skipped::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (!skipped.isEmpty()) {
            throw new IllegalStateException("ArchUnit did not import these class files, so their uses would be "
                    + "missed: " + skipped.stream().sorted().toList());
        }
    }

    private static Map<Scope, Set<String>> apiOutputs() {
        Set<String> main = outputNames(ClassDirectories.main());
        Set<String> test = new LinkedHashSet<>(main);
        test.addAll(outputNames(ClassDirectories.test()));
        return Map.of(Scope.MAIN, main, Scope.TEST, Set.copyOf(test));
    }

    private static Set<String> outputNames(List<Path> directories) {
        JavaClasses classes = ClassDirectories.read(directories);
        requireEveryClassFile(classes, directories);
        return classes.stream().map(JavaClass::getName).collect(Collectors.toUnmodifiableSet());
    }

    static String packageOf(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }

    private static String labels(List<ProviderIndex.Provider> providers) {
        return providers.stream().map(ProviderIndex.Provider::label).sorted().collect(Collectors.joining(" and "));
    }
}
