package io.github.stigjohnny.c4contractkit.testing;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation that is on the test class path but never in a fixture's class root. Its default member value is a
 * class, so a dependency on that class appears only if an importer reads the annotation type from the class path.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ClassPathLabel {

    /** A class only the class path knows. */
    Class<?> value() default RepositoryFiles.class;
}
