package io.github.stigjohnny.c4contractkit.bytecode;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the class files under explicit class roots with ArchUnit (security/reviews/maven--archunit--1.5.1.md).
 *
 * <p>It imports only the given file paths, never the class path, packages or URLs. Classes are parsed from their
 * bytes and never loaded or initialised. Missing dependencies are not resolved from the class path, and no class
 * resolver is used, so the result depends only on the given roots, whatever class path the check runs on.
 */
public final class ClassRootImporter {

    private static final String CLASS_SUFFIX = ".class";
    private static final String MODULE_DESCRIPTOR = "module-info.class";

    private ClassRootImporter() {
    }

    /**
     * Imports every class under the roots.
     *
     * @param roots directories of class files; their order does not matter, and a root given twice counts once
     * @return the classes by name, each with its references
     * @throws IllegalArgumentException if a root is not a directory, if a root is or holds a symbolic link to a
     *     directory, or if a class file under the roots could not be imported: a corrupt file, a file that is not a
     *     class file, or a second definition of a class
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
        requireNoDirectoryLinks(paths);
        JavaClasses classes = ArchConfiguration.withThreadLocalScope(configuration -> {
            // The scope starts as a copy of the shared configuration, which archunit.properties may have set.
            configuration.unsetClassResolver();
            configuration.setResolveMissingDependenciesFromClassPath(false);
            refuseSystemPropertyOverrides(configuration);
            return new ClassFileImporter().importPaths(paths);
        });
        requireEveryClassFileImported(paths, classes);
        return classes.stream()
                .map(DependencyEvidence::of)
                .sorted(Comparator.comparing(ImportedClass::name))
                .toList();
    }

    /**
     * ArchUnit logs a class file it cannot parse and goes on without it, and keeps only the first of two definitions
     * of a class. Every class file under the roots must have become a class, or the comparison would miss it without a
     * word. A file's class is the one its path names, as compilers lay out their output. A module descriptor,
     * {@code module-info.class}, declares a module, not a class, and ArchUnit does not import it.
     */
    private static void requireEveryClassFileImported(List<Path> roots, JavaClasses classes) {
        Set<String> imported = classes.stream().map(JavaClass::getName).collect(Collectors.toSet());
        Set<String> seen = new HashSet<>();
        List<Path> missed = new ArrayList<>();
        for (Path root : roots) {
            for (Path file : classFiles(root)) {
                String relative = root.relativize(file).toString();
                String name = relative.substring(0, relative.length() - CLASS_SUFFIX.length())
                        .replace(root.getFileSystem().getSeparator(), ".");
                if (!seen.add(name) || !imported.contains(name)) {
                    missed.add(file);
                }
            }
        }
        if (!missed.isEmpty()) {
            throw new IllegalArgumentException("ArchUnit could not import these class files, so the check would miss"
                    + " their classes: " + missed + ". A corrupt or truncated file, a file that is not a class file, or"
                    + " a second definition of a class in another root is not a supported input.");
        }
    }

    /**
     * ArchUnit walks a class root without following symbolic links, and so does the inventory above: a root that is a
     * link to a directory, or a link to a directory inside a root, would hide its classes without a word. Both are
     * refused. A link to a class file is read by both and is accepted.
     */
    private static void requireNoDirectoryLinks(List<Path> roots) {
        List<Path> links = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> entries = Files.walk(root)) {
                entries.filter(Files::isSymbolicLink).filter(Files::isDirectory).sorted().forEach(links::add);
            } catch (IOException e) {
                throw new UncheckedIOException("The entries under " + root + " could not be listed", e);
            }
        }
        if (!links.isEmpty()) {
            throw new IllegalArgumentException("ArchUnit does not follow a symbolic link to a directory, so the check"
                    + " would miss the classes behind these links: " + links + ". Give the class roots as real"
                    + " directories that hold no such link.");
        }
    }

    private static List<Path> classFiles(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(CLASS_SUFFIX))
                    .filter(file -> !file.getFileName().toString().equals(MODULE_DESCRIPTOR))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("The class files under " + root + " could not be listed", e);
        }
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
