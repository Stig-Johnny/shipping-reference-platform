package io.github.stigjohnny.c4contractkit.sources;

/**
 * A construct that the restricted import grammar does not cover. A refused construct fails the check: it is never
 * skipped, because what it would make the file depend on cannot be known without running the code.
 *
 * @param line where it is, from 1
 * @param reason what was refused and why, in one sentence
 */
public record Refusal(int line, String reason) {
}
