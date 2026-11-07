package io.github.stigjohnny.c4contractkit.testing;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;
import java.util.Optional;

/**
 * An ArchUnit class resolver that records each time it is constructed, in a system property, so a test can show that
 * the library never let ArchUnit load and instantiate it. A class is initialised only once per JVM, so construction,
 * not initialisation, is what a test can observe again and again. Tests name this class by string, and
 * {@link #CONSTRUCTED} is a constant, so reading it does not load the class.
 */
public final class SentinelClassResolver implements ClassResolver {

    /** Set to "true" whenever an instance is constructed. */
    public static final String CONSTRUCTED = "c4-contract-kit.test.sentinel-class-resolver.constructed";

    public SentinelClassResolver() {
        System.setProperty(CONSTRUCTED, "true");
    }

    @Override
    public void setClassUriImporter(ClassResolver.ClassUriImporter classUriImporter) {
        // Resolves nothing, so it needs no importer.
    }

    @Override
    public Optional<JavaClass> tryResolve(String typeName) {
        return Optional.empty();
    }
}
