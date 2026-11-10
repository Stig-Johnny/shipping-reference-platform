package io.github.stigjohnny.docs.repo;

import io.github.stigjohnny.docs.DocsException;
import io.github.stigjohnny.docs.yaml.SafeYaml;
import io.github.stigjohnny.docs.yaml.YamlInputException;
import io.github.stigjohnny.docs.yaml.YamlValue;
import io.github.stigjohnny.docs.yaml.YamlValue.YBool;
import io.github.stigjohnny.docs.yaml.YamlValue.YMap;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A fresh, data-only snapshot of bounded declaration inputs, independent of review scope and permission.
 * Locks establish declarations, not imports or execution. No network, command, cache or review-file scan occurs.
 */
public final class DependencyUsage {

    private static final List<String> LOCKS = List.of("settings-gradle.lockfile", "apps/shipping-api/gradle.lockfile",
            "libs/c4-contract-kit/gradle.lockfile", "tools/docs/gradle.lockfile");
    private static final String NPM = "package-lock.json";
    private static final String WORKFLOWS = ".github/workflows";
    private static final String WRAPPER = "gradle/wrapper/gradle-wrapper.properties";
    private static final String MERMAID = "tools/docs/vendor/mermaid/11.17.2/mermaid.min.js";
    private static final String TOKEN = "[A-Za-z0-9_.+~-]+";
    private static final String CONFIGURATIONS = "[A-Za-z0-9_.-]+(?:,[A-Za-z0-9_.-]+)*";
    private static final Pattern LOCK = Pattern.compile("(" + TOKEN + ":" + TOKEN + "):(" + TOKEN + ")=("
            + CONFIGURATIONS + ")");
    private static final Pattern PACKAGE = Pattern.compile("(?:@[A-Za-z0-9_.-]+/)?[A-Za-z0-9_.-]+");
    private static final Pattern ACTION = Pattern.compile("([A-Za-z0-9_.-]+/[A-Za-z0-9_./-]+)@([0-9a-fA-F]{40})");
    private static final Pattern CARRIER = Pattern.compile("(?:inlined in )?(playwright-core|playwright|axe-core)-("
            + TOKEN + ")\\.tgz: [^;]+(?:; |$)");
    private static final Comparator<Location> ORDER = Comparator.comparing(Location::path).thenComparingInt(Location::line)
            .thenComparing(Location::context).thenComparing(Location::excerpt);

    private final List<Declaration> declarations;

    private DependencyUsage(List<Declaration> declarations) {
        this.declarations = List.copyOf(declarations);
    }

    /**
     * Reads only the named lockfiles, root npm lock, immediate workflow files, wrapper properties and Mermaid file.
     * Missing inputs contribute no declaration; malformed, unreadable or symlinked supported inputs fail the build.
     */
    public static DependencyUsage read(Path repo) {
        List<Declaration> found = new ArrayList<>();
        for (String path : LOCKS) {
            text(repo, path).ifPresent(source -> locks(found, path, source));
        }
        text(repo, NPM).ifPresent(source -> npm(found, source));
        workflows(repo, found);
        text(repo, WRAPPER).ifPresent(source -> wrapper(found, source));
        bytes(repo, MERMAID, 16_777_216).ifPresent(content -> {
            String digest = Allowlist.sha256(content);
            found.add(new Declaration("vendored-js", "mermaid", "11.17.2", "dist/mermaid.min.js", digest,
                    new Location(MERMAID, 0, "Vendored file SHA-256 observed; file contents are not reproduced",
                            "SHA-256 " + digest)));
        });
        return new DependencyUsage(found);
    }

    /** Current evidence for this exact review row; neither a missing match nor a match changes its verdict. */
    public List<Location> locations(AllowlistEntry entry) {
        if (entry.kind().equals("bundled")) {
            return carriers(entry);
        }
        return declarations.stream().filter(declaration -> declaration.matches(entry)).map(Declaration::location)
                .distinct().sorted(ORDER).toList();
    }

    /** Whether this reader has a bounded evidence source for the kind, not a promise of a match for every entry. */
    public static boolean supportsKind(String kind) {
        return switch (kind) {
            case "maven", "npm", "github-action", "bundled", "gradle-distribution", "vendored-js" -> true;
            default -> false;
        };
    }

    /** Coverage text suitable beside both matches and empty results; unsupported does not mean unused. */
    public static String limitation(String kind) {
        return switch (kind) {
            case "maven" -> "The four supported Gradle lockfiles establish exact module versions and configurations, "
                    + "not direct imports, execution, artifact variants or artifact bytes.";
            case "npm" -> "Root package-lock.json v3 package records establish exact package versions and resolved "
                    + "archives, not installation, direct imports or execution. Workspace links and records without "
                    + "a resolved archive provide no package evidence.";
            case "github-action" -> "Immediate .github/workflows YAML files establish structured step or reusable "
                    + "workflow uses for the reviewed owner/repository and full commit SHA, not workflow execution. "
                    + "Subpaths remain in the declaration; tags do not match.";
            case "bundled" -> "Only reviewed playwright, playwright-core and axe-core archive carriers are matched "
                    + "to current npm locks. Embedded contents and component versions are not re-inspected; this "
                    + "does not establish standalone package use. Other carrier formats are not inspected.";
            case "gradle-distribution" -> "Wrapper properties establish the configured distribution archive and "
                    + "SHA-256, not installation or execution. The bootstrap wrapper JAR is not inferred from them.";
            case "vendored-js" -> "Only the supported vendored Mermaid file is inspected, by exact SHA-256; "
                    + "presence does not establish browser execution.";
            default -> "Evidence not collected for this kind. Review scope does not establish current use or absence.";
        };
    }

    /**
     * An extracted declaration or observed digest. A positive line is an actual source line; zero means a structural
     * pointer in context, or a computed file digest, rather than a fabricated source line. Excerpts are normalized
     * declaration fields, never entire workflow scripts, resolved URLs or minified vendor contents.
     */
    public record Location(String path, int line, String context, String excerpt) {
        public Location {
            if (!safePath(path) || line < 0 || !printable(context) || !printable(excerpt)) {
                throw new IllegalArgumentException("unsafe dependency evidence location");
            }
        }
    }

    private record Declaration(String kind, String coordinate, String version, String artifact, String digest,
            Location location) {
        boolean matches(AllowlistEntry entry) {
            if (!kind.equals(entry.kind()) || !coordinate.equals(entry.coordinate())) {
                return false;
            }
            // A reviewed action's immutable identity is its commit, not the release label alongside that commit.
            if (kind.equals("github-action")) {
                return artifact.equals(entry.artifact()) && entry.artifact().matches("[0-9a-fA-F]{40}");
            }
            if (!version.equals(entry.version())) {
                return false;
            }
            if (kind.equals("maven")) {
                return true; // Explicitly coordinate-level evidence, including separately reviewed metadata rows.
            }
            return artifact.equals(entry.artifact()) && (digest.isEmpty() || digest.equals(entry.sha256()));
        }
    }

    private static void locks(List<Declaration> found, String path, String source) {
        List<String> lines = source.lines().toList();
        String project = path.equals("settings-gradle.lockfile") ? "root settings"
                : ":" + path.substring(0, path.lastIndexOf('/')).replace('/', ':');
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("#") || line.matches("empty=(?:" + CONFIGURATIONS + ")?")) {
                continue;
            }
            Matcher match = LOCK.matcher(line);
            if (!match.matches()) {
                throw invalid(path, "malformed lock entry at line " + (i + 1));
            }
            found.add(new Declaration("maven", match.group(1), match.group(2), "", "",
                    new Location(path, i + 1, project + ": " + match.group(3)
                            + "; locked module; artifact variant/bytes not established", line)));
        }
    }

    private static void npm(List<Declaration> found, String source) {
        try {
            YMap root = SafeYaml.json(source, NPM).asMap(NPM);
            if (!root.get("lockfileVersion").asText("lockfileVersion").equals("3")) {
                throw invalid(NPM, "only lockfileVersion 3 is supported");
            }
            for (var item : root.map("packages").entries().entrySet()) {
                String path = item.getKey();
                YMap itemValue = item.getValue().asMap("package record");
                int at = path.lastIndexOf("node_modules/");
                if (at < 0 || (at > 0 && path.charAt(at - 1) != '/')) {
                    continue; // Root/workspace declarations are not resolved third-party package records.
                }
                if (!safePath(path)) {
                    throw invalid(NPM, "unsafe package path");
                }
                if (itemValue.find("link").filter(value -> value.equals(new YBool(true))).isPresent()) {
                    continue;
                }
                String name = itemValue.optionalString("name").orElse(path.substring(at + "node_modules/".length()));
                String version = itemValue.string("version");
                String archive = itemValue.optionalString("resolved").orElse("");
                if (!PACKAGE.matcher(name).matches() || !version.matches(TOKEN)) {
                    throw invalid(NPM, "unsupported package identity");
                }
                if (archive.isEmpty()) {
                    continue;
                }
                String flags = itemValue.find("dev").filter(value -> value.equals(new YBool(true)))
                        .map(ignored -> "; dev").orElse("");
                found.add(new Declaration("npm", name, version, archive, "",
                        new Location(NPM, 0, "packages[\"" + path + "\"].version; locked package" + flags,
                                "\"version\": \"" + version + "\"")));
            }
        } catch (YamlInputException e) {
            throw invalid(NPM, "malformed supported package lock");
        }
    }

    private static void workflows(Path repo, List<Declaration> found) {
        if (!exists(repo, WORKFLOWS)) {
            return;
        }
        if (!Files.isDirectory(repo.resolve(WORKFLOWS), LinkOption.NOFOLLOW_LINKS)) {
            throw invalid(WORKFLOWS, "expected a directory");
        }
        try (Stream<Path> files = Files.list(repo.resolve(WORKFLOWS))) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (name.endsWith(".yml") || name.endsWith(".yaml")) {
                    String relative = WORKFLOWS + "/" + name;
                    if (!safePath(relative)) {
                        throw invalid(WORKFLOWS, "unsafe workflow filename");
                    }
                    text(repo, relative).ifPresent(source -> workflow(found, relative, source));
                }
            }
        } catch (IOException e) {
            throw invalid(WORKFLOWS, "cannot read workflow directory");
        }
    }

    private static void workflow(List<Declaration> found, String path, String source) {
        try {
            YMap root = SafeYaml.yaml(source, path).asMap(path);
            for (var job : root.map("jobs").entries().entrySet()) {
                if (!job.getKey().matches("[A-Za-z_][A-Za-z0-9_-]*")) {
                    throw invalid(path, "unsupported job ID");
                }
                YMap value = job.getValue().asMap("job");
                String pointer = "jobs." + job.getKey();
                value.optionalString("uses").ifPresent(uses -> action(found, path, pointer + ".uses", uses));
                List<YamlValue> steps = value.find("steps").map(v -> v.asList("steps")).orElse(List.of());
                for (int i = 0; i < steps.size(); i++) {
                    YMap step = steps.get(i).asMap("step");
                    String context = pointer + ".steps[" + i + "].uses (step " + (i + 1) + ")";
                    step.optionalString("uses").ifPresent(uses -> action(found, path, context, uses));
                }
            }
        } catch (YamlInputException e) {
            throw invalid(path, "malformed supported workflow");
        }
    }

    private static void action(List<Declaration> found, String path, String context, String uses) {
        Matcher match = ACTION.matcher(uses);
        if (match.matches() && safePath(match.group(1))) {
            String[] parts = match.group(1).split("/", 3);
            found.add(new Declaration("github-action", parts[0] + "/" + parts[1], "", match.group(2), "",
                    new Location(path, 0, context + "; pinned declaration", "uses: " + uses)));
        }
    }

    private static void wrapper(List<Declaration> found, String source) {
        Properties properties = new Properties();
        List<String> lines = source.lines().toList();
        int line = 0;
        for (int i = 0; i < lines.size(); i++) {
            String current = lines.get(i).strip();
            if (current.isEmpty() || current.startsWith("#") || current.startsWith("!")) {
                continue;
            }
            if (current.endsWith("\\")) {
                throw invalid(WRAPPER, "continued property declarations are not supported");
            }
            Properties item = new Properties();
            try {
                item.load(new StringReader(current));
            } catch (IOException | IllegalArgumentException e) {
                throw invalid(WRAPPER, "malformed properties");
            }
            if (item.size() != 1) {
                throw invalid(WRAPPER, "expected one property per line");
            }
            String key = item.stringPropertyNames().iterator().next();
            if (properties.containsKey(key)) {
                throw invalid(WRAPPER, "duplicate property key");
            }
            properties.setProperty(key, item.getProperty(key));
            if (key.equals("distributionUrl")) {
                line = i + 1;
            }
        }
        String url = properties.getProperty("distributionUrl", "");
        String digest = properties.getProperty("distributionSha256Sum", "");
        Matcher match = Pattern.compile("https://services\\.gradle\\.org/distributions/(gradle-(" + TOKEN
                + ")-bin\\.zip)").matcher(url);
        if (!match.matches() || !digest.matches("[0-9a-f]{64}")) {
            throw invalid(WRAPPER, "expected an exact distribution URL and SHA-256");
        }
        found.add(new Declaration("gradle-distribution", "gradle (services.gradle.org/distributions)", match.group(2),
                match.group(1), digest, new Location(WRAPPER, line, "Configured distribution; URL and SHA-256 match",
                        "distributionUrl=" + match.group(1) + "; distributionSha256Sum=" + digest)));
    }

    private List<Location> carriers(AllowlistEntry entry) {
        Matcher match = CARRIER.matcher(entry.artifact());
        List<Location> found = new ArrayList<>();
        int end = 0;
        while (match.find() && match.start() == end) {
            String name = match.group(1);
            String version = match.group(2);
            String archive = "https://registry.npmjs.org/" + name + "/-/" + name + "-" + version + ".tgz";
            for (Declaration declaration : declarations) {
                if (declaration.kind().equals("npm") && declaration.coordinate().equals(name)
                        && declaration.version().equals(version) && declaration.artifact().equals(archive)) {
                    Location source = declaration.location();
                    found.add(new Location(source.path(), source.line(), source.context()
                            + "; Reviewed bundle carrier " + name + " " + version
                            + " is locked; embedded contents not re-inspected", source.excerpt()));
                }
            }
            end = match.end();
        }
        return end == entry.artifact().length() ? found.stream().distinct().sorted(ORDER).toList() : List.of();
    }

    private static Optional<String> text(Path repo, String relative) {
        return bytes(repo, relative, 1_048_576).map(content -> {
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(content)).toString();
            } catch (CharacterCodingException e) {
                throw invalid(relative, "input is not valid UTF-8");
            }
        });
    }

    private static Optional<byte[]> bytes(Path repo, String relative, int maximum) {
        if (!exists(repo, relative)) {
            return Optional.empty();
        }
        Path file = repo.resolve(relative);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > maximum) {
                throw invalid(relative, "expected a regular input within its size limit");
            }
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw invalid(relative, "cannot read input");
        }
    }

    private static boolean exists(Path repo, String relative) {
        Path path = repo;
        for (String segment : relative.split("/", -1)) {
            path = path.resolve(segment);
            if (Files.isSymbolicLink(path)) {
                throw invalid(relative, "symlinked input is not allowed");
            }
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
        }
        return true;
    }

    private static boolean safePath(String path) {
        return path.matches("[A-Za-z0-9_.@/-]+") && !path.startsWith("/")
                && Stream.of(path.split("/", -1)).noneMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."));
    }

    private static boolean printable(String text) {
        return text.codePoints().noneMatch(Character::isISOControl);
    }

    private static DocsException invalid(String relative, String reason) {
        return new DocsException(relative + ": " + reason);
    }
}
