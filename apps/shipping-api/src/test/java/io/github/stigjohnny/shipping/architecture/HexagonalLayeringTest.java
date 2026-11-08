package io.github.stigjohnny.shipping.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;

/**
 * The layers inside each module (ADR 0003), with ArchUnit on the API's main class-output directories: only the five
 * packages exist in a module, domain code uses neither Spring, Jakarta and JDBC nor the application layer and the
 * adapters, application code uses no adapter, adapter.in does not use adapter.out, and every package is
 * {@code @NullMarked}. Each rule also fails on a fixture under fixtures/layering that breaks it.
 */
class HexagonalLayeringTest {

    private static final String API = "io.github.stigjohnny.shipping";
    private static final String FIXTURES = HexagonalLayeringTest.class.getPackageName() + ".fixtures.layering.";
    private static final Set<String> MODULE_PACKAGES = Set.of("api", "internal.domain", "internal.application",
            "internal.adapter.in.web", "internal.adapter.out.jdbc");
    private static final String PACKAGE_INFO = "package-info";

    @Test
    void theApiKeepsItsLayers() {
        JavaClasses api = ClassDirectories.read(ClassDirectories.main());

        CompositeArchRule.of(rules(API)).check(api);
        // The rules see the web adapters of both built modules, and the adapter rule has classes to check.
        assertThat(api.stream().map(JavaClass::getPackageName).distinct()).contains(
                API + ".quotes.internal.adapter.in.web", API + ".tenants.internal.adapter.in.web");
    }

    @Test
    void aClassInAnyOtherPackageOfAModuleFails() {
        assertThat(violations(onlyTheFivePackagesInAModule(FIXTURES + "extrapackage"), "extrapackage"))
                .singleElement().asString().contains(FIXTURES + "extrapackage.quotes.internal.util.Strings");
    }

    @Test
    void aSpringStereotypeOnADomainClassFails() {
        assertThat(violations(domainUsesNoFrameworkApplicationOrAdapter(FIXTURES + "stereotype"), "stereotype"))
                .singleElement().asString().contains(FIXTURES + "stereotype.quotes.internal.domain.Tariff",
                        "org.springframework.stereotype.Component");
    }

    @Test
    void domainCodeThatUsesTheApplicationLayerFails() {
        assertThat(violations(domainUsesNoFrameworkApplicationOrAdapter(FIXTURES + "layers"), "layers"))
                .isNotEmpty().allSatisfy(violation -> assertThat(violation).contains(
                        FIXTURES + "layers.quotes.internal.domain.PricingRule",
                        FIXTURES + "layers.quotes.internal.application.IssueQuote"));
    }

    @Test
    void applicationCodeThatUsesAnAdapterFails() {
        assertThat(violations(applicationUsesNoAdapter(FIXTURES + "layers"), "layers"))
                .isNotEmpty().allSatisfy(violation -> assertThat(violation).contains(
                        FIXTURES + "layers.quotes.internal.application.IssueQuote",
                        FIXTURES + "layers.quotes.internal.adapter.out.jdbc.QuoteRows"));
    }

    @Test
    void anInboundAdapterThatUsesAnOutboundAdapterFails() {
        assertThat(violations(adapterInDoesNotUseAdapterOut(FIXTURES + "layers"), "layers"))
                .isNotEmpty().allSatisfy(violation -> assertThat(violation).contains(
                        FIXTURES + "layers.quotes.internal.adapter.in.web.QuoteController",
                        FIXTURES + "layers.quotes.internal.adapter.out.jdbc.QuoteRows"));
    }

    @Test
    void aPackageWithoutNullMarkedFails() {
        assertThat(violations(everyPackageIsNullMarked(FIXTURES + "unmarked"), "unmarked"))
                .singleElement().asString().contains(FIXTURES + "unmarked.quotes.api.Amount");
    }

    /** The rules for the modules below {@code root}: the API's own, or a fixture's. */
    static List<ArchRule> rules(String root) {
        return List.of(onlyTheFivePackagesInAModule(root), domainUsesNoFrameworkApplicationOrAdapter(root),
                applicationUsesNoAdapter(root), adapterInDoesNotUseAdapterOut(root), everyPackageIsNullMarked(root));
    }

    /** A module's root package holds only its package-info; its classes are in its five packages. */
    static ArchRule onlyTheFivePackagesInAModule(String root) {
        return classes().that().resideInAPackage(root + ".*..").should(holds(
                "be in a module's api, internal.domain, internal.application, internal.adapter.in.web or "
                        + "internal.adapter.out.jdbc package, or be its root package's package-info",
                javaClass -> inModulePackage(root, javaClass), "is in a package that is none of its module's five"));
    }

    static ArchRule domainUsesNoFrameworkApplicationOrAdapter(String root) {
        return noClasses().that().resideInAPackage(root + ".*.internal.domain..").should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta..", "java.sql..", "javax.sql..",
                        root + ".*.internal.application..", root + ".*.internal.adapter..");
    }

    static ArchRule applicationUsesNoAdapter(String root) {
        return noClasses().that().resideInAPackage(root + ".*.internal.application..").should().dependOnClassesThat()
                .resideInAPackage(root + ".*.internal.adapter..");
    }

    /**
     * The web adapters of quotes and tenants give this rule the API's classes to check, so it no longer allows an empty
     * set: without any inbound adapter it fails.
     */
    static ArchRule adapterInDoesNotUseAdapterOut(String root) {
        return noClasses().that().resideInAPackage(root + ".*.internal.adapter.in..").should().dependOnClassesThat()
                .resideInAPackage(root + ".*.internal.adapter.out..");
    }

    static ArchRule everyPackageIsNullMarked(String root) {
        return classes().that().resideInAPackage(root + "..").should(holds(
                "be in a package whose package-info.java is annotated @NullMarked",
                javaClass -> javaClass.getPackage().isAnnotatedWith(NullMarked.class),
                "is in a package without a package-info.java annotated @NullMarked"));
    }

    private static boolean inModulePackage(String root, JavaClass javaClass) {
        String module = javaClass.getPackageName().substring(root.length() + 1);
        int dot = module.indexOf('.');
        return dot < 0 ? javaClass.getSimpleName().equals(PACKAGE_INFO)
                : MODULE_PACKAGES.contains(module.substring(dot + 1));
    }

    private static ArchCondition<JavaClass> holds(String description, Predicate<JavaClass> test, String failure) {
        return new ArchCondition<>(description) {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!test.test(javaClass)) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + " " + failure));
                }
            }
        };
    }

    /** The rule's violations on a fixture; the fixture must have some. */
    private static List<String> violations(ArchRule rule, String fixture) {
        EvaluationResult result = rule.evaluate(ClassDirectories.read(List.of(ClassDirectories.fixture(FIXTURES
                + fixture))));
        assertThat(result.hasViolation()).as(rule.getDescription()).isTrue();
        return result.getFailureReport().getDetails();
    }
}
