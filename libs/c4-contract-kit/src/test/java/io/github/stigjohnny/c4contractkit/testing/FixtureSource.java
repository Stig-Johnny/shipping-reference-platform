package io.github.stigjohnny.c4contractkit.testing;

import java.util.ArrayList;
import java.util.List;

/**
 * One fixture source file for {@link FixtureCompiler}.
 *
 * @param path the path relative to the source root, such as {@code fx/a/A.java}
 * @param code the compilation unit
 */
public record FixtureSource(String path, String code) {

    /** The 1-based number of the only line that ends with {@code // marker}. */
    public int lineOf(String marker) {
        List<String> lines = code.lines().toList();
        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().endsWith("// " + marker)) {
                matches.add(i + 1);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("marker " + marker + " is on lines " + matches + " of " + path);
        }
        return matches.get(0);
    }
}
