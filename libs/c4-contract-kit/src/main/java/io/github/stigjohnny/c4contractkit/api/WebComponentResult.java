package io.github.stigjohnny.c4contractkit.api;

import java.util.List;
import java.util.Optional;

/**
 * A component of the web container and how many code files belong to it.
 *
 * @param id its {@code web.id}
 * @param name its name in the model
 * @param status its {@code web.status}
 * @param paths its directories, relative to {@code web.root}, sorted
 * @param entry its {@code web.entry}, relative to {@code web.root}, when it has one
 * @param packages the npm packages it may import, sorted
 * @param fileCount its {@code .ts} and {@code .tsx} files, test files excluded
 */
public record WebComponentResult(String id, String name, ComponentStatus status, List<String> paths,
                                 Optional<String> entry, List<String> packages, int fileCount) {

    /** Copies the lists, so a result never changes after it is created. */
    public WebComponentResult {
        paths = List.copyOf(paths);
        packages = List.copyOf(packages);
    }
}
