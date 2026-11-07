package io.github.stigjohnny.c4contractkit.api;

/**
 * One import of an npm package or a Node.js built-in, and how the whitelists decided it: the model property that lists
 * it and the entry of {@code security/allowlist.json} that approves it. Every such import of every root is reported,
 * approved or not.
 *
 * @param file the importing file, relative to the repository root, with {@code /} as separator
 * @param line the line of the specifier, from 1
 * @param specifier the module specifier as written
 * @param target the package (without a subpath), or the built-in module as written
 * @param version the exact version package.json declares for the package, or that {@code engines.node} names for a
 *     built-in; empty when it names none
 * @param rule the model property that whitelists the import ({@code web.packages of cart}, {@code web.testPackages},
 *     {@code web.testBuiltins}), or the rule that refuses it
 * @param allowlistEntry the approving entry of the allowlist in one line: kind, coordinate, version, project,
 *     configuration, verdict and review record; empty when none approves the import
 * @param status {@link ExternalImportStatus#APPROVED}, or why not
 */
public record WebExternalImport(String file, int line, String specifier, String target, String version, String rule,
                                String allowlistEntry, ExternalImportStatus status) {
}
