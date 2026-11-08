package io.github.stigjohnny.shipping.architecture;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Where the architecture tests read compiled classes from: explicit class-file directories, as the ArchUnit review
 * requires (security/reviews/maven--archunit--1.5.1.md), never the class path or a package scan.
 */
final class ClassDirectories {

    /** Set for the test task by apps/shipping-api/build.gradle.kts: the main source set's class-output directories. */
    static final String MAIN = "shipping.mainClassDirectories";

    /** Set for the test task by apps/shipping-api/build.gradle.kts: the test source set's class-output directories. */
    static final String TEST = "shipping.testClassDirectories";

    private ClassDirectories() {
    }

    /** Every class-output directory of the API's main source set, as Gradle passes them to the test task. */
    static List<Path> main() {
        return directories(MAIN);
    }

    /** Every class-output directory of the API's test source set, as Gradle passes them to the test task. */
    static List<Path> test() {
        return directories(TEST);
    }

    private static List<Path> directories(String property) {
        String directories = System.getProperty(property, "");
        if (directories.isBlank()) {
            throw new IllegalStateException("The system property " + property + " is not set: run the tests with "
                    + "Gradle.");
        }
        return Stream.of(directories.split(Pattern.quote(File.pathSeparator), -1)).map(Path::of).toList();
    }

    /**
     * The classes under the directories, read with ArchUnit from their class files: nothing is loaded, and nothing
     * missing is resolved from the class path, so the result depends only on the directories, as in c4-contract-kit.
     */
    static JavaClasses read(List<Path> directories) {
        return ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.unsetClassResolver();
            configuration.setResolveMissingDependenciesFromClassPath(false);
            return new ClassFileImporter().importPaths(directories);
        });
    }

    /** The directory of compiled test classes that holds a fixture package of this test package. */
    static Path fixture(String fixturePackage) {
        String resource = fixturePackage.replace('.', '/');
        URL url = ClassDirectories.class.getClassLoader().getResource(resource);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException("The fixture package " + fixturePackage + " is not a directory of "
                    + "compiled test classes.");
        }
        try {
            Path directory = Path.of(url.toURI());
            if (!Files.isDirectory(directory)) {
                throw new IllegalStateException(directory + " is not a directory.");
            }
            return directory;
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
