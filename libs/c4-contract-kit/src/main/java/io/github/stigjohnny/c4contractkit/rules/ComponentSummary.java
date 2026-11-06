package io.github.stigjohnny.c4contractkit.rules;

import io.github.stigjohnny.c4contractkit.model.ComponentStatus;
import java.util.List;

/** A component of the checked container and how many classes belong to it. */
public record ComponentSummary(String id, String name, ComponentStatus status, List<String> packages,
                               int classCount) {

    public ComponentSummary {
        packages = packages.stream().sorted().toList();
    }
}
