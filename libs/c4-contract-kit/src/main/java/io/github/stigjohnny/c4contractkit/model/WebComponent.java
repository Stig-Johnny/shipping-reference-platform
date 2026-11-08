package io.github.stigjohnny.c4contractkit.model;

import java.util.List;
import java.util.Optional;

/**
 * A component of the web container.
 *
 * @param id its {@code web.id}
 * @param name its name in the model
 * @param status its {@code web.status}
 * @param paths its directories, relative to {@code web.root}, sorted
 * @param entry its {@code web.entry}, relative to {@code web.root}: the only file other components may import
 * @param packages the npm packages its files may import by a bare specifier, sorted
 */
public record WebComponent(String id, String name, ComponentStatus status, List<String> paths, Optional<String> entry,
                           List<String> packages) {

    public WebComponent {
        paths = paths.stream().sorted().toList();
        packages = packages.stream().sorted().toList();
    }

    /** Whether a file, relative to {@code web.root}, lies in one of the component's directories. */
    public boolean owns(String file) {
        return paths.stream().anyMatch(path -> file.startsWith(path + "/"));
    }
}
