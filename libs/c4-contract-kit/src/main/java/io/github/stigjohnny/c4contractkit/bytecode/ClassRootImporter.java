package io.github.stigjohnny.c4contractkit.bytecode;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Reads the class files under explicit class roots with ArchUnit (security/reviews/maven--archunit--1.5.1.md).
 *
 * <p>It imports only the given file paths, never the class path, packages or URLs. Classes are parsed from their
 * bytes and never loaded or initialised. Missing dependencies are not resolved from the class path, so the result
 * depends only on the given roots, whatever class path the check runs on.
 */
public final class ClassRootImporter {

    private ClassRootImporter() {
    }

    /**
     * Imports every class under the roots.
     *
     * @param roots directories or JAR files; their order and duplicates do not matter
     * @return the classes by name, each with its references
     * @throws IllegalArgumentException if a root does not exist
     */
    public static List<ImportedClass> importRoots(List<Path> roots) {
        List<Path> paths = roots.stream().map(root -> root.toAbsolutePath().normalize()).distinct().sorted().toList();
        for (Path path : paths) {
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("class root does not exist: " + path);
            }
        }
        JavaClasses classes = ArchConfiguration.withThreadLocalScope(configuration -> {
            configuration.setResolveMissingDependenciesFromClassPath(false);
            return new ClassFileImporter().importPaths(paths);
        });
        return classes.stream()
                .map(DependencyEvidence::of)
                .sorted(Comparator.comparing(ImportedClass::name))
                .toList();
    }
}
