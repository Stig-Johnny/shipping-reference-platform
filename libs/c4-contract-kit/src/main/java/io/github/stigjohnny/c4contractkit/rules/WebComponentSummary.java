package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.model.ComponentStatus;
import java.util.List;
import java.util.Optional;

/** A component of the web container and how many code files belong to it. */
public record WebComponentSummary(String id, String name, ComponentStatus status, List<String> paths,
                                  Optional<String> entry, List<String> packages, int fileCount) {

    public WebComponentSummary {
        paths = paths.stream().sorted().toList();
        packages = packages.stream().sorted().toList();
    }
}
