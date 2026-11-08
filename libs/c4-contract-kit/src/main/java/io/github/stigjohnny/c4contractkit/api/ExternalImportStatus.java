package io.github.stigjohnny.c4contractkit.api;

/** How the whitelists decided an import of an npm package or a Node.js built-in. */
public enum ExternalImportStatus {
    /** Listed in the model and approved by an entry of {@code security/allowlist.json}. */
    APPROVED,
    /** Not listed by the model property that would whitelist it. A violation. */
    UNDECLARED,
    /** Listed, but package.json names no exact version, so no reviewed release is known. A violation. */
    UNPINNED,
    /** Listed and pinned, but no approving entry of the allowlist covers it, or the allowlist is unavailable. */
    UNAPPROVED,
    /** Never allowed where it is: a Node.js built-in in browser code, or one a test names without {@code node:}. */
    REFUSED
}
