package io.github.stigjohnny.c4contractkit.sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The web app's configuration, read as data from its directory: the dependencies and the Node.js version that
 * {@code package.json} declares, and every setting of {@code package.json}, the {@code tsconfig*.json} files and the
 * {@code *.html} pages that would change what a specifier names or add an import that no source file shows. The check
 * resolves specifiers itself, by the rules of {@link Specifiers}; a setting that could make TypeScript, Node.js or the
 * browser resolve them otherwise is refused, as is a file that is not plain JSON or a page the reader cannot read.
 *
 * <p>Refused in {@code tsconfig*.json}: {@code extends} and {@code references} (settings from elsewhere), and in
 * {@code compilerOptions} {@code paths}, {@code baseUrl}, {@code rootDirs}, {@code typeRoots}, {@code moduleSuffixes},
 * {@code customConditions}, {@code allowArbitraryExtensions} and {@code jsxImportSource}; {@code types} unless it is
 * empty, {@code importHelpers} when true, and {@code jsx} when it adds an import of a JSX runtime. In
 * {@code package.json}: {@code imports}, {@code overrides}, {@code browser} and dependencies other than
 * {@code dependencies} and {@code devDependencies}, whose versions must be exact. In a page: an import map, an inline
 * script, a {@code <base>} element, a script or link from another origin, and a style element that names
 * {@code @import}.
 *
 * @param app how reports name the directory, for example {@code apps/shipping-web}
 * @param dependencies the runtime dependencies, by name, with their exact versions
 * @param devDependencies the development dependencies, by name, with their exact versions
 * @param node the exact version of Node.js that {@code engines.node} names, if it names one
 * @param refusals what is refused, by file (relative to the directory) and line
 */
public record AppConfiguration(String app, Map<String, String> dependencies, Map<String, String> devDependencies,
        Optional<String> node, List<FileRefusal> refusals) {

    private static final Pattern EXACT_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?");
    private static final Set<String> COMPILER_OPTIONS_REFUSED = Set.of("paths", "baseUrl", "rootDirs", "typeRoots",
            "moduleSuffixes", "customConditions", "allowArbitraryExtensions", "jsxImportSource");
    private static final Set<String> JSX_RUNTIMES = Set.of("react", "react-jsx", "react-jsxdev", "react-native");
    private static final Set<String> PACKAGE_FIELDS_REFUSED = Set.of("imports", "overrides", "browser",
            "optionalDependencies", "peerDependencies", "bundleDependencies", "bundledDependencies");
    private static final Pattern TAG = Pattern.compile("<(script|base|link|style)\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\s*([A-Za-z_:][-A-Za-z0-9_:.]*)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s\"'=<>`]+))?");
    private static final Pattern OTHER_ORIGIN = Pattern.compile("^(?:[A-Za-z][A-Za-z0-9+.-]*:|//)");

    public AppConfiguration {
        dependencies = Map.copyOf(dependencies);
        devDependencies = Map.copyOf(devDependencies);
        refusals = refusals.stream().sorted(Comparator.comparing(FileRefusal::file)
                .thenComparingInt(FileRefusal::line).thenComparing(FileRefusal::reason)).toList();
    }

    /**
     * Reads the configuration of the web app in this directory; a directory that does not exist has none.
     *
     * @param directory the web app's directory on disk
     * @param app how reports name it
     * @throws UncheckedIOException if a file cannot be read
     */
    public static AppConfiguration read(Path directory, String app) {
        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> devDependencies = new TreeMap<>();
        List<FileRefusal> refusals = new ArrayList<>();
        Optional<String> node = Optional.empty();
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new AppConfiguration(app, dependencies, devDependencies, node, refusals);
        }
        for (Path file : topLevelFiles(directory)) {
            String name = file.getFileName().toString();
            boolean configuration = name.equals("package.json")
                    || (name.startsWith("tsconfig") && name.endsWith(".json"))
                    || name.toLowerCase(Locale.ROOT).endsWith(".html");
            if (!configuration) {
                continue;
            }
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                refusals.add(new FileRefusal(name, 1, "is not a regular file: the check reads only regular files"));
                continue;
            }
            Optional<String> read = text(file, app + "/" + name);
            if (read.isEmpty()) {
                refusals.add(new FileRefusal(name, 1, "is not valid UTF-8"));
                continue;
            }
            String text = read.get();
            if (name.equals("package.json")) {
                node = packageJson(text, dependencies, devDependencies, refusals);
            } else if (name.endsWith(".json")) {
                tsconfig(name, text, refusals);
            } else {
                page(name, text, refusals);
            }
        }
        return new AppConfiguration(app, dependencies, devDependencies, node, refusals);
    }

    private static Optional<String> packageJson(String text, Map<String, String> dependencies,
            Map<String, String> devDependencies, List<FileRefusal> refusals) {
        Json.Value root;
        try {
            root = Json.parse(text);
        } catch (Json.Invalid e) {
            refusals.add(new FileRefusal("package.json", e.line(), "is not plain JSON: " + e.getMessage()));
            return Optional.empty();
        }
        if (!root.isObject()) {
            refusals.add(new FileRefusal("package.json", root.line(), "is not a JSON object"));
            return Optional.empty();
        }
        Map<String, Json.Value> members = root.object();
        for (Map.Entry<String, Json.Value> member : members.entrySet()) {
            if (PACKAGE_FIELDS_REFUSED.contains(member.getKey())) {
                refusals.add(new FileRefusal("package.json", member.getValue().line(), member.getKey()
                        + " is not supported: it changes what a specifier names or what is installed"));
            }
        }
        versions(members.get("dependencies"), "dependencies", dependencies, refusals);
        versions(members.get("devDependencies"), "devDependencies", devDependencies, refusals);
        Json.Value engines = members.get("engines");
        if (engines == null) {
            return Optional.empty();
        }
        Json.Value version = engines.isObject() ? engines.object().get("node") : null;
        if (version == null) {
            return Optional.empty();
        }
        if (!version.isString() || !EXACT_VERSION.matcher(version.string()).matches()) {
            refusals.add(new FileRefusal("package.json", version.line(), "engines.node must name one exact version of "
                    + "Node.js, whose review approves its built-in modules"));
            return Optional.empty();
        }
        return Optional.of(version.string());
    }

    private static void versions(Json.@Nullable Value field, String name, Map<String, String> into,
            List<FileRefusal> refusals) {
        if (field == null) {
            return;
        }
        if (!field.isObject()) {
            refusals.add(new FileRefusal("package.json", field.line(), name + " is not a JSON object"));
            return;
        }
        for (Map.Entry<String, Json.Value> dependency : field.object().entrySet()) {
            Json.Value version = dependency.getValue();
            if (!version.isString() || !EXACT_VERSION.matcher(version.string()).matches()) {
                refusals.add(new FileRefusal("package.json", version.line(), name + ": the version of "
                        + dependency.getKey() + " must be exact, so that it names one reviewed release"));
            } else {
                into.put(dependency.getKey(), version.string());
            }
        }
    }

    private static void tsconfig(String file, String text, List<FileRefusal> refusals) {
        Json.Value root;
        try {
            root = Json.parse(text);
        } catch (Json.Invalid e) {
            refusals.add(new FileRefusal(file, e.line(), "is not plain JSON (no comments, no trailing commas): "
                    + e.getMessage()));
            return;
        }
        if (!root.isObject()) {
            refusals.add(new FileRefusal(file, root.line(), "is not a JSON object"));
            return;
        }
        for (String key : List.of("extends", "references")) {
            Json.Value value = root.object().get(key);
            if (value != null) {
                refusals.add(new FileRefusal(file, value.line(), key + " is not supported: settings from another "
                        + "file could change what a specifier names"));
            }
        }
        Json.Value options = root.object().get("compilerOptions");
        if (options == null || !options.isObject()) {
            return;
        }
        for (Map.Entry<String, Json.Value> option : options.object().entrySet()) {
            String key = option.getKey();
            Json.Value value = option.getValue();
            @Nullable String reason = null;
            if (COMPILER_OPTIONS_REFUSED.contains(key)) {
                reason = "changes what a specifier names or adds an import that no source file shows";
            } else if (key.equals("types") && !(value.isArray() && value.array().isEmpty())) {
                reason = "must be empty: each name in it adds the types of a package to every file";
            } else if (key.equals("importHelpers") && value.isTrue()) {
                reason = "imports tslib into every file that needs a helper";
            } else if (key.equals("jsx") && value.isString()
                    && JSX_RUNTIMES.contains(value.string().toLowerCase(Locale.ROOT))) {
                reason = "makes every .tsx file import a JSX runtime";
            }
            if (reason != null) {
                refusals.add(new FileRefusal(file, value.line(), "compilerOptions." + key + " is not supported: it "
                        + reason));
            }
        }
    }

    private static void page(String file, String text, List<FileRefusal> refusals) {
        Matcher tag = TAG.matcher(text);
        int searched = 0;
        while (tag.find()) {
            int line = lineOf(text, tag.start());
            String name = tag.group(1).toLowerCase(Locale.ROOT);
            Map<String, String> attributes = attributes(tag.group(2));
            searched = tag.end();
            if (attributes == null) {
                refusals.add(new FileRefusal(file, line, "the attributes of a <" + name + "> element cannot be read "
                        + "with certainty"));
                continue;
            }
            switch (name) {
                case "base" -> refusals.add(new FileRefusal(file, line, "a <base> element is not supported: it "
                        + "changes what every URL of the page names"));
                case "link" -> {
                    String href = attributes.getOrDefault("href", "");
                    if (OTHER_ORIGIN.matcher(href.strip()).find()) {
                        refusals.add(new FileRefusal(file, line, "a link to another origin (" + href + ") is not "
                                + "supported"));
                    }
                }
                case "style" -> {
                    int end = indexOfIgnoreCase(text, "</style", tag.end());
                    String css = text.substring(tag.end(), end < 0 ? text.length() : end);
                    int offset = line - 1 + (int) text.substring(tag.start(), tag.end()).chars()
                            .filter(c -> c == '\n').count();
                    for (Refusal refusal : ImportScanner.scanStyle(css)) {
                        refusals.add(new FileRefusal(file, offset + refusal.line(), refusal.reason()));
                    }
                }
                default -> script(file, text, tag, line, attributes, refusals);
            }
        }
        int open = indexOfIgnoreCase(text, "<script", searched);
        if (open >= 0) {
            refusals.add(new FileRefusal(file, lineOf(text, open), "a <script> tag that the reader cannot read with "
                    + "certainty"));
        }
    }

    private static void script(String file, String text, Matcher tag, int line, Map<String, String> attributes,
            List<FileRefusal> refusals) {
        String type = attributes.getOrDefault("type", "").strip().toLowerCase(Locale.ROOT);
        if (type.equals("importmap")) {
            refusals.add(new FileRefusal(file, line, "an import map is not supported: it changes what a specifier "
                    + "names"));
            return;
        }
        String src = attributes.get("src");
        if (src == null) {
            int end = indexOfIgnoreCase(text, "</script", tag.end());
            String content = text.substring(tag.end(), end < 0 ? text.length() : end);
            if (!content.isBlank()) {
                refusals.add(new FileRefusal(file, line, "an inline script is not supported: the check never reads "
                        + "its imports"));
            }
        } else if (OTHER_ORIGIN.matcher(src.strip()).find()) {
            refusals.add(new FileRefusal(file, line, "a script from another origin (" + src + ") is not supported"));
        }
    }

    /** The attributes of a tag, names in lower case, or null when the text holds anything else. */
    private static @Nullable Map<String, String> attributes(String text) {
        String body = text.endsWith("/") ? text.substring(0, text.length() - 1) : text;
        Map<String, String> attributes = new TreeMap<>();
        Matcher attribute = ATTRIBUTE.matcher(body);
        int at = 0;
        while (at < body.length() && attribute.find(at) && attribute.start() == at) {
            if (attribute.end() == at) {
                break;
            }
            String value = attribute.group(2) == null ? "" : attribute.group(2);
            if (value.startsWith("\"") || value.startsWith("'")) {
                value = value.substring(1, value.length() - 1);
            }
            attributes.put(attribute.group(1).toLowerCase(Locale.ROOT), value);
            at = attribute.end();
        }
        return body.substring(at).isBlank() ? attributes : null;
    }

    private static int indexOfIgnoreCase(String text, String needle, int from) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle, from);
    }

    private static int lineOf(String text, int index) {
        return 1 + (int) text.substring(0, index).chars().filter(c -> c == '\n').count();
    }

    private static List<Path> topLevelFiles(Path directory) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            entries.forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("The web app's directory could not be read", e);
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    /** The file as UTF-8, or empty when it is not valid UTF-8. */
    private static Optional<String> text(Path file, String shown) {
        try {
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (CharacterCodingException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("The file " + shown + " could not be read", e);
        }
    }
}
