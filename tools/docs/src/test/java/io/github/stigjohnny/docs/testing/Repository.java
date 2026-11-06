package io.github.stigjohnny.docs.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The repository this test runs in, found by walking up from the working directory to settings.gradle.kts. */
public final class Repository {

    private Repository() {
    }

    public static Path root() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts")) && Files.isDirectory(dir.resolve("tools"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("no repository root above the working directory");
    }

    public static Path file(String relative) {
        return root().resolve(relative);
    }

    public static String read(String relative) {
        try {
            return Files.readString(file(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
