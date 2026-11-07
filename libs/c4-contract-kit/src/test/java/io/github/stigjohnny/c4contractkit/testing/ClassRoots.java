package io.github.stigjohnny.c4contractkit.testing;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;

/** Finds class roots on the test class path by a class file they contain, without loading the class. */
public final class ClassRoots {

    private ClassRoots() {
    }

    /** The directory on the test class path that holds the class file of {@code className}. */
    public static Path of(String className) {
        String resource = className.replace('.', '/') + ".class";
        URL url = Objects.requireNonNull(ClassLoader.getSystemResource(resource), resource);
        try {
            Path classFile = Path.of(url.toURI());
            return classFile.getRoot().resolve(
                    classFile.subpath(0, classFile.getNameCount() - Path.of(resource).getNameCount()));
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
