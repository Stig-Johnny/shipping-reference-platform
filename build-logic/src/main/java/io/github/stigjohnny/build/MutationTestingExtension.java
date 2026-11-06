package io.github.stigjohnny.build;

import org.gradle.api.provider.Property;

/** What {@link MutationTestingPlugin} mutates, and the share of mutants the tests must kill. */
public abstract class MutationTestingExtension {

    /** The production classes PIT mutates, as a PIT glob such as {@code io.github.stigjohnny.c4contractkit.*}. */
    public abstract Property<String> getTargetClasses();

    /** The minimum percentage of killed mutants; the build fails below it. Defaults to 90. */
    public abstract Property<Integer> getMutationThreshold();
}
