package io.github.stigjohnny.c4contractkit.bytecode;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads the class files under explicit class roots with ArchUnit (security/reviews/maven--archunit--1.5.1.md).
 *
 * <p>It imports only the given file paths, never the class path, packages or URLs. Classes are parsed from their
 * bytes and never loaded or initialised. Missing dependencies are not resolved from the class path, and no class
 * resolver is used, so the result depends only on the given roots, whatever class path the check runs on.
 */
public final class ClassRootImporter {

    private ClassRootImporter() {
    }

    /**
     * Imports every class under the roots.
     *
     * @param roots directories of class files; their order and duplicates do not matter
     * @return the classes by name, each with its references
     * @throws IllegalArgumentException if a root is not a directory
     * @throws IllegalStateException if an archunit.* system property sets a class resolver or resolution from the
     *     class path
     */
    public static List<ImportedClass> importRoots(List<Path> roots) {
        List<Path> paths = roots.stream().map(root -> root.toAbsolutePath().normalize()).distinct().sorted().toList();
        for (Path path : paths) {
            // ArchUnit reads a JAR given as a path as one non-class file, so only directories are accepted.
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("class root is not a directory: " + path);
            }
        }
        JavaClasses classes = ArchConfiguration.withThreadLocalScope(configuration -> {
            // The scope starts as a copy of the shared configuration, which archunit.properties may have set.
            configuration.unsetClassResolver();
            configuration.setResolveMissingDependenciesFromClassPath(false);
            refuseSystemPropertyOverrides(configuration);
            return new ClassFileImporter().importPaths(paths);
        });
        return classes.stream()
                .map(DependencyEvidence::of)
                .sorted(Comparator.comparing(ImportedClass::name))
                .toList();
    }

    /**
     * An archunit.* system property overrides any value set in a configuration, so what the scope set above may not
     * be in effect. ArchUnit would load and instantiate a configured class resolver, and read missing classes from
     * the class path; both are refused rather than silently tolerated.
     */
    private static void refuseSystemPropertyOverrides(ArchConfiguration configuration) {
        Optional<String> resolver = configuration.getClassResolver();
        if (resolver.isPresent()) {
            throw new IllegalStateException("ArchUnit is configured to resolve classes with " + resolver.get()
                    + " (system property archunit.classResolver). c4-contract-kit reads class files only from the given"
                    + " class roots and never loads a class, so it does not import with a class resolver.");
        }
        if (configuration.resolveMissingDependenciesFromClassPath()) {
            throw new IllegalStateException("ArchUnit is configured to resolve missing classes from the class path"
                    + " (system property archunit.resolveMissingDependenciesFromClassPath). c4-contract-kit reads class"
                    + " files only from the given class roots, so it does not import with this setting.");
        }
    }
}
