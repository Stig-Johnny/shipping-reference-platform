package io.github.stigjohnny.c4contractkit.rules;

/** How the whitelists decided an import of a package or a Node.js built-in. */
public enum ExternalImportVerdict {
    APPROVED,
    UNDECLARED,
    UNPINNED,
    UNAPPROVED,
    REFUSED
}
