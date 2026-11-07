package io.github.stigjohnny.docs.scan;

import java.util.Comparator;

/** A rule that fired in a file of the site, which is a path relative to the site. It never holds the matched value. */
public record Finding(String rule, String file) implements Comparable<Finding> {

    private static final Comparator<Finding> ORDER = Comparator.comparing(Finding::file)
            .thenComparing(Finding::rule);

    @Override
    public int compareTo(Finding other) {
        return ORDER.compare(this, other);
    }
}
