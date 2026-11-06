package io.github.stigjohnny.c4contractkit.bytecode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.ArchConfiguration;
import io.github.stigjohnny.c4contractkit.testing.ClassPathLabel;
import io.github.stigjohnny.c4contractkit.testing.FixtureCompiler;
import io.github.stigjohnny.c4contractkit.testing.FixtureSource;
import io.github.stigjohnny.c4contractkit.testing.SentinelClassResolver;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit takes configuration from archunit.properties and from archunit.* system properties. None of it may make
 * the import load a class or read classes from the class path (security/reviews/maven--archunit--1.5.1.md). What
 * the library can override in its own thread-local scope it overrides; what it cannot, a system property, it refuses.
 */
class ArchUnitConfigurationTest {

    private static final String RESOLVER = "io.github.stigjohnny.c4contractkit.testing.SentinelClassResolver";

    private static final FixtureSource LABELLED = new FixtureSource("fx/app/a/Labelled.java", """
            package fx.app.a;

            import io.github.stigjohnny.c4contractkit.testing.ClassPathLabel;

            @ClassPathLabel
            public class Labelled {
            }
            """);

    private final Path classes = FixtureCompiler.compileAgainstTestClassesOf(ClassPathLabel.class.getName(), LABELLED);

    @BeforeEach
    @AfterEach
    void clearSharedConfiguration() {
        System.clearProperty(SentinelClassResolver.CONSTRUCTED);
        System.clearProperty("archunit.classResolver");
        System.clearProperty("archunit.resolveMissingDependenciesFromClassPath");
        ArchConfiguration.get().reset();
    }

    @Test
    void aClassResolverInTheSharedConfigurationIsNeverLoaded() {
        // This is what an archunit.properties on the class path does.
        ArchConfiguration.get().setProperty("classResolver", RESOLVER);

        List<ImportedClass> imported = ClassRootImporter.importRoots(List.of(classes));

        assertThat(imported).extracting(ImportedClass::name).contains("fx.app.a.Labelled");
        assertThat(System.getProperty(SentinelClassResolver.CONSTRUCTED)).as("ArchUnit constructed the resolver").isNull();
    }

    @Test
    void resolvingFromTheClassPathInTheSharedConfigurationIsSwitchedOff() {
        ArchConfiguration.get().setResolveMissingDependenciesFromClassPath(true);

        assertThat(targetsOfLabelled()).containsExactly(ClassPathLabel.class.getName(), "java.lang.Object");
    }

    @Test
    void aClassResolverSetByASystemPropertyIsRefusedAndNeverLoaded() {
        System.setProperty("archunit.classResolver", RESOLVER);

        assertThatThrownBy(() -> ClassRootImporter.importRoots(List.of(classes)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ArchUnit is configured to resolve classes with " + RESOLVER + " (system property "
                        + "archunit.classResolver). c4-contract-kit reads class files only from the given class roots "
                        + "and never loads a class, so it does not import with a class resolver.");
        assertThat(System.getProperty(SentinelClassResolver.CONSTRUCTED)).as("ArchUnit constructed the resolver").isNull();
    }

    @Test
    void resolvingFromTheClassPathSetByASystemPropertyIsRefused() {
        System.setProperty("archunit.resolveMissingDependenciesFromClassPath", "true");

        assertThatThrownBy(() -> ClassRootImporter.importRoots(List.of(classes)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ArchUnit is configured to resolve missing classes from the class path (system property "
                        + "archunit.resolveMissingDependenciesFromClassPath). c4-contract-kit reads class files only "
                        + "from the given class roots, so it does not import with this setting.");
    }

    private List<String> targetsOfLabelled() {
        return ClassRootImporter.importRoots(List.of(classes)).stream()
                .filter(imported -> imported.name().equals("fx.app.a.Labelled"))
                .flatMap(imported -> imported.references().stream())
                .map(CodeReference::targetClass)
                .distinct()
                .toList();
    }
}
