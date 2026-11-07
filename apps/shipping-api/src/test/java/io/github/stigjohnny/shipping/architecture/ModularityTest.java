package io.github.stigjohnny.shipping.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import io.github.stigjohnny.shipping.ShippingApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.core.Violations;

/**
 * Spring Modulith's verification of the API's modules (ADR 0003): no module uses a type that another module does not
 * expose, and no modules form a cycle. Spring Modulith finds the modules itself, as the direct sub-packages of
 * ShippingApplication's package, from the class path without the test classes. Which module may use which is stated
 * once, in architecture/workspace.dsl, and ArchitectureConformanceTest checks it. Each failure is also shown on a
 * fixture under fixtures/modulith.
 */
class ModularityTest {

    private static final String FIXTURES = ModularityTest.class.getPackageName() + ".fixtures.modulith.";

    @Test
    void theModulesVerify() {
        ApplicationModules modules = ApplicationModules.of(ShippingApplication.class);

        modules.verify();
        assertThat(modules.stream().map(ModularityTest::name)).containsExactlyInAnyOrder("quotes", "tenants");
        assertThat(dependencies(modules, "quotes")).containsExactly("tenants");
        assertThat(dependencies(modules, "tenants")).isEmpty();
    }

    @Test
    void aModuleThatUsesAnotherModulesInternalTypeFails() {
        Violations violations = fixture("internals").detectViolations();

        // One violation per reference: the record's field, accessor and constructor parameter.
        assertThat(violations.getMessages()).isNotEmpty().allSatisfy(message -> assertThat(message).startsWith(
                "Module 'shipments' depends on non-exposed type " + FIXTURES
                        + "internals.quotes.internal.domain.Quote within module 'quotes'!"));
    }

    @Test
    void aCycleBetweenModulesFails() {
        Violations violations = fixture("cycle").detectViolations();

        assertThat(violations.getMessages()).singleElement().asString().contains("Cycle detected", "quotes",
                "tenants");
    }

    /** A fixture's modules, read from the test classes only. */
    private static ApplicationModules fixture(String name) {
        return ApplicationModules.of(FIXTURES + name, new ImportOption.OnlyIncludeTests());
    }

    private static List<String> dependencies(ApplicationModules modules, String module) {
        return modules.getModuleByName(module).orElseThrow().getDirectDependencies(modules).uniqueModules()
                .map(ModularityTest::name).sorted().toList();
    }

    private static String name(ApplicationModule module) {
        return module.getIdentifier().toString();
    }
}
