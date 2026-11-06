package io.github.stigjohnny.c4contractkit.testing;

import java.nio.file.Files;
import java.nio.file.Path;

/** Paths of files in this repository, found from the working directory that Gradle and PIT both set. */
public final class RepositoryFiles {

    private RepositoryFiles() {
    }

    /** The repository root: the nearest directory, from the working directory upwards, with settings.gradle.kts. */
    public static Path root() {
        Path start = Path.of("").toAbsolutePath();
        for (Path candidate = start; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle.kts"))
                    && Files.isDirectory(candidate.resolve("libs/c4-contract-kit"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("no repository root above " + start);
    }

    /** The project directory of :libs:c4-contract-kit. */
    public static Path library() {
        return root().resolve("libs/c4-contract-kit");
    }
}
