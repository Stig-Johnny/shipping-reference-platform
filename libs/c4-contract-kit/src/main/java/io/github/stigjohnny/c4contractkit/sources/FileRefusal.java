package io.github.stigjohnny.c4contractkit.sources;

/**
 * A construct of a file that the restricted import grammar refuses.
 *
 * @param file relative to the web root
 * @param line from 1
 * @param reason what was refused and why
 */
public record FileRefusal(String file, int line, String reason) {
}
