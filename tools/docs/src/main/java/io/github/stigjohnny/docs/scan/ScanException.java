package io.github.stigjohnny.docs.scan;

/**
 * One exact exception: the value whose SHA-256 is {@code sha256}, matched by {@code rule} in {@code file}, is a
 * documented public demo token and may be published.
 */
public record ScanException(String file, String rule, String sha256, String reason) {
}
