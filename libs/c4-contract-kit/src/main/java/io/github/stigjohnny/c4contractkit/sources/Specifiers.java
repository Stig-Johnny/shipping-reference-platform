package io.github.stigjohnny.c4contractkit.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves a module specifier the way the restricted grammar allows, against the files that exist under the web root,
 * by exact name: a relative specifier that ends in {@code .ts} names exactly that file (TypeScript's
 * {@code rewriteRelativeImportExtensions} emits it as {@code .js}), and no other file is tried in its place; one
 * without a file extension names {@code p.ts}, {@code p.tsx}, {@code p.d.ts} or the {@code index} file of the
 * directory {@code p}, and must match exactly one of them; a specifier of an image or font names that file. A bare
 * specifier names a package, whose subpath may hold no empty, {@code .} or {@code ..} segment and no percent sign, or a
 * built-in module of Node.js, as does {@code node:} followed by the name of one. Everything else (absolute paths, other
 * schemes, queries such as {@code ?url}, subpath imports, style sheets, other code extensions) is unsupported, so that
 * what the check resolves is what TypeScript resolves, whatever its configuration says.
 */
final class Specifiers {

    static final List<String> CODE_SUFFIXES = List.of(".ts", ".tsx", ".d.ts");
    /** The one code extension a relative specifier may carry: it then names exactly that file. */
    static final String TS = ".ts";
    /** Images and fonts: a relative specifier with one of these extensions names that file. */
    static final Set<String> FILE_EXTENSIONS = Set.of("svg", "png", "jpg", "jpeg", "gif", "webp", "avif", "ico", "woff",
            "woff2");
    static final String STYLE_SHEET = "a TypeScript import of a style sheet is not supported: link style sheets from "
            + "index.html";
    static final String SUBPATH = "a package subpath with an empty, '.' or '..' segment is not supported";
    static final String PERCENT = "a percent sign in a package specifier is not supported: it could encode a path";
    /**
     * The built-in modules of Node.js 26 that may be named without the {@code node:} prefix, with their subpaths
     * ({@code module.builtinModules}, without the underscored internal ones).
     */
    static final Set<String> NODE_BUILTINS = Set.of("assert", "assert/strict", "async_hooks", "buffer",
            "child_process", "cluster", "console", "constants", "crypto", "dgram", "diagnostics_channel", "dns",
            "dns/promises", "domain", "events", "fs", "fs/promises", "http", "http2", "https", "inspector",
            "inspector/promises", "module", "net", "os", "path", "path/posix", "path/win32", "perf_hooks", "process",
            "punycode", "querystring", "readline", "readline/promises", "repl", "stream", "stream/consumers",
            "stream/promises", "stream/web", "string_decoder", "sys", "timers", "timers/promises", "tls",
            "trace_events", "tty", "url", "util", "util/types", "v8", "vm", "wasi", "worker_threads", "zlib");
    /** The built-in modules of Node.js 26 that exist only with the {@code node:} prefix. */
    static final Set<String> NODE_PREFIX_ONLY = Set.of("sea", "sqlite", "test", "test/reporters");
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
        if (specifier.startsWith("node:")) {
            String name = specifier.substring("node:".length());
            return NODE_BUILTINS.contains(name) || NODE_PREFIX_ONLY.contains(name) ? new Resolution.ToBuiltin(specifier)
                    : new Resolution.Unsupported("'" + specifier + "' is not a built-in module of Node.js");
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
            if (name.endsWith(TS)) {
                return files.contains(target) ? new Resolution.ToFile(target)
                        : new Resolution.Unresolved("matches no file: " + target + " does not exist");
            }
            int dot = name.lastIndexOf('.');
            String written = dot < 0 ? "" : name.substring(dot + 1);
            String extension = written.toLowerCase(Locale.ROOT);
            if (CODE_EXTENSIONS.contains(extension)) {
                return new Resolution.Unsupported("a module specifier with the file extension ." + written
                        + " is not supported: name a .ts file with its extension, or write the specifier without one");
            }
            if (extension.equals("css")) {
                return new Resolution.Unsupported(STYLE_SHEET);
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
     * A built-in module of Node.js named without its prefix, or the package a bare specifier names, by the npm rules
     * for new package names (lower case; letters, digits, '-', '.' and '_'; not starting with '.' or '_'; at most 214
     * characters). A subpath may follow the package name; each of its segments must be a name, so that the specifier
     * cannot reach past the package, and no percent sign may encode one.
     */
    private static Resolution bare(String specifier) {
        if (NODE_BUILTINS.contains(specifier)) {
            return new Resolution.ToBuiltin(specifier);
        }
        if (specifier.contains("%")) {
            return new Resolution.Unsupported(PERCENT);
        }
        String[] parts = specifier.split("/", -1);
        boolean scoped = specifier.startsWith("@");
        String name = scoped && parts.length > 1 ? parts[0] + "/" + parts[1] : parts[0];
        boolean valid = name.length() <= 214 && (scoped
                ? parts.length > 1 && PACKAGE_PART.matcher(parts[0].substring(1)).matches()
                        && PACKAGE_PART.matcher(parts[1]).matches()
                : PACKAGE_PART.matcher(name).matches());
        if (!valid) {
            return new Resolution.Unsupported("'" + name + "' is not a valid npm package name");
        }
        for (int i = scoped ? 2 : 1; i < parts.length; i++) {
            if (parts[i].isEmpty() || parts[i].equals(".") || parts[i].equals("..")) {
                return new Resolution.Unsupported(SUBPATH);
            }
        }
        if (parts.length > (scoped ? 2 : 1) && parts[parts.length - 1].toLowerCase(Locale.ROOT).endsWith(".css")) {
            return new Resolution.Unsupported(STYLE_SHEET);
        }
        return new Resolution.ToPackage(name);
    }
}
