package io.github.stigjohnny.c4contractkit.rules;

import java.util.Comparator;

/**
 * One import of an npm package or a Node.js built-in, and how the whitelists decided it.
 *
 * @param file the importing file, relative to the repository root
 * @param target the package, or the built-in module
 * @param version the exact version package.json declares for the package, or that engines.node names for a built-in;
 *     empty when it names none
 * @param rule the model property that whitelists the import, or the rule that refuses it
 * @param allowlistEntry the approving entry of the allowlist, in one line; empty when none approves the import
 */
public record ExternalImportItem(String file, int line, String specifier, String target, String version, String rule,
                                 String allowlistEntry, ExternalImportVerdict verdict) {

    static final Comparator<ExternalImportItem> ORDER = Comparator.comparing(ExternalImportItem::file)
            .thenComparingInt(ExternalImportItem::line)
            .thenComparing(ExternalImportItem::specifier);
}
