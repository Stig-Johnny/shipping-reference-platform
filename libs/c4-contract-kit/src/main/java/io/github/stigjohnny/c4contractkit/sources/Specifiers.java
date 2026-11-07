package io.github.stigjohnny.c4contractkit.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves a module specifier the way the restricted grammar allows, against the files that exist under the web root,
 * by exact name: a relative specifier without a file extension names {@code p.ts}, {@code p.tsx}, {@code p.d.ts} or
 * the {@code index} file of the directory {@code p}, and must match exactly one of them; a specifier of a style sheet,
 * image or font names that file. A bare specifier names a package. Everything else (absolute paths, schemes such as
 * {@code node:}, queries such as {@code ?url}, subpath imports, code extensions) is unsupported, so that what the check
 * resolves is what TypeScript and Vite resolve, whatever their configuration says.
 */
final class Specifiers {

    static final List<String> CODE_SUFFIXES = List.of(".ts", ".tsx", ".d.ts");
    static final Set<String> FILE_EXTENSIONS = Set.of("css", "svg", "png", "jpg", "jpeg", "gif", "webp", "avif",
            "ico", "woff", "woff2");
    private static final Set<String> CODE_EXTENSIONS = Set.of("ts", "tsx", "mts", "cts", "js", "jsx", "mjs", "cjs",
            "json");
    private static final Pattern PACKAGE_PART = Pattern.compile("[a-z0-9-][a-z0-9._-]*");

    private Specifiers() {
    }

    /**
     * Resolves one specifier.
     *
     * @param importer the importing file, relative to the web root
     * @param files every file under the web root, relative to it
     */
    static Resolution resolve(String importer, String specifier, Set<String> files) {
        if (specifier.isEmpty()) {
            return new Resolution.Unsupported("an empty module specifier is not supported");
        }
        if (specifier.contains("?") || specifier.contains("#")) {
            return new Resolution.Unsupported(
                    "a query or fragment in a module specifier (such as ?url, ?raw or #name) is not supported");
        }
        if (specifier.startsWith("/")) {
            return new Resolution.Unsupported(
                    "an absolute module specifier is not supported: write it relative to the importing file");
        }
        if (specifier.equals(".") || specifier.equals("..") || specifier.startsWith("./")
                || specifier.startsWith("../")) {
            return relative(importer, specifier, files);
        }
        if (specifier.contains(":")) {
            return new Resolution.Unsupported(
                    "a module specifier with a scheme (such as node:, https: or virtual:) is not supported");
        }
        return bare(specifier);
    }

    private static Resolution relative(String importer, String specifier, Set<String> files) {
        boolean directory = specifier.endsWith("/") || specifier.equals(".") || specifier.equals("..");
        String path = specifier.endsWith("/") ? specifier.substring(0, specifier.length() - 1) : specifier;
        List<String> segments = new ArrayList<>();
        int slash = importer.lastIndexOf('/');
        if (slash > 0) {
            segments.addAll(List.of(importer.substring(0, slash).split("/", -1)));
        }
        for (String segment : path.split("/", -1)) {
            switch (segment) {
                case "" -> {
                    return new Resolution.Unsupported("an empty path segment in a module specifier is not supported");
                }
                case "." -> {
                    // The same directory.
                }
                case ".." -> {
                    if (segments.isEmpty()) {
                        return new Resolution.Unresolved("leaves the web root");
                    }
                    segments.removeLast();
                }
                default -> segments.add(segment);
            }
        }
        String target = String.join("/", segments);
        if (!directory) {
            String name = segments.isEmpty() ? "" : segments.getLast();
            int dot = name.lastIndexOf('.');
            String extension = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (CODE_EXTENSIONS.contains(extension)) {
                return new Resolution.Unsupported("write the import without the file extension ." + extension);
            }
            if (FILE_EXTENSIONS.contains(extension)) {
                return files.contains(target) ? new Resolution.ToFile(target)
                        : new Resolution.Unresolved("matches no file: " + target + " does not exist");
            }
        }
        List<String> candidates = new ArrayList<>();
        if (!directory) {
            CODE_SUFFIXES.forEach(suffix -> candidates.add(target + suffix));
        }
        String index = target.isEmpty() ? "index" : target + "/index";
        CODE_SUFFIXES.forEach(suffix -> candidates.add(index + suffix));
        List<String> found = candidates.stream().filter(files::contains).toList();
        return switch (found.size()) {
            case 0 -> new Resolution.Unresolved("matches no file: tried " + String.join(", ", candidates));
            case 1 -> new Resolution.ToFile(found.getFirst());
            default -> new Resolution.Unresolved("is ambiguous: it matches " + String.join(" and ", found));
        };
    }

    /**
     * The package a bare specifier names, by the npm rules for new package names (lower case; letters, digits, '-',
     * '.' and '_'; not starting with '.' or '_'; at most 214 characters); a subpath may follow.
     */
    private static Resolution bare(String specifier) {
        String[] parts = specifier.split("/", -1);
        boolean scoped = specifier.startsWith("@");
        String name = scoped && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
        boolean valid = name.length() <= 214 && (scoped
                ? parts.length > 1 && PACKAGE_PART.matcher(parts[0].substring(1)).matches()
                        && PACKAGE_PART.matcher(parts[1]).matches()
                : PACKAGE_PART.matcher(name).matches());
        return valid ? new Resolution.ToPackage(name)
                : new Resolution.Unsupported("'" + name + "' is not a valid npm package name");
    }
}
