package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.sources.Allowlist;
import io.github.stigjohnny.c4contractkit.sources.AppConfiguration;
import io.github.stigjohnny.c4contractkit.sources.SourceSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the web import check reads besides the model: the web root, the test and public roots, the app's
 * configuration and the dependency allowlist.
 *
 * @param sources the files and imports of {@code web.root}
 * @param testRoots the files and imports of each test root
 * @param publicRoots the files of each public root
 * @param configuration the app's configuration
 * @param allowlist the dependency allowlist
 */
public record WebInputs(SourceSet sources, List<SourceSet> testRoots, List<SourceSet> publicRoots,
                        AppConfiguration configuration, Allowlist allowlist) {

    public WebInputs {
        testRoots = List.copyOf(testRoots);
        publicRoots = List.copyOf(publicRoots);
    }

    /** A web root alone: no other roots, no configuration and an allowlist that approves nothing. */
    public static WebInputs of(SourceSet sources) {
        return new WebInputs(sources, List.of(), List.of(),
                new AppConfiguration("", Map.of(), Map.of(), Optional.empty(), List.of()),
                Allowlist.unavailable("no allowlist was read"));
    }
}
