package io.github.stigjohnny.build;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * The provider index of a Java project: which resolved artifact provides each class on its classpaths.
 *
 * <p>For every artifact of the configurations it is given, the index records the component (a Maven coordinate and
 * version, or a project with its build identity), the file's SHA-256 and the packages and classes it provides.
 * The classes come from the names of the JAR's entries, read with {@link JarFile}: no class is loaded, defined or parsed. Gradle has
 * checked every downloaded file against {@code gradle/verification-metadata.xml} (strict) when the configurations
 * resolved, before this task reads it.
 *
 * <p>Multi-release JARs are read as the JDK reads them for the project's release: with {@code Multi-Release: true}
 * in the main manifest section, a class under {@code META-INF/versions/N/} with 9 &lt;= N &lt;= release is a class
 * of the JAR. Every other versioned entry provides nothing and is listed under {@code ignoredReleases}.
 *
 * <p>A class that two artifacts provide on one configuration is listed under {@code duplicates}: it has no single
 * provider there. The index only records it; ExternalImportPolicyTest fails while any duplicate exists, and fails
 * each use of such a class.
 */
@DisableCachingByDefault(because = "It reads every artifact it indexes, and this repository's build cache is off.")
public abstract class ProviderIndexTask extends DefaultTask {

    private static final String VERSIONS = "META-INF/versions/";
    private static final Pattern RELEASE = Pattern.compile("[1-9][0-9]{0,3}");
    private static final int FIRST_VERSIONED_RELEASE = 9;

    /** One tab-separated line per artifact: configuration, kind, coordinate, version and build identity. */
    @Input
    public abstract ListProperty<String> getArtifacts();

    /** The artifacts' files, in order; location is an input because repository ownership depends on it. */
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ListProperty<File> getArtifactFiles();

    /** The Java release the project compiles for ({@code --release}): the view of a multi-release JAR. */
    @Input
    public abstract Property<Integer> getRelease();

    /** The project's Gradle path, as the index names it. */
    @Input
    public abstract Property<String> getProjectPath();

    /** The build's root directory: a file of one of its projects is named relative to it, any other by name. */
    @Internal
    public abstract DirectoryProperty getRootDirectory();

    /** Exact output paths, relative to the repository root, registered for repository-owned projects. */
    @Input
    public abstract MapProperty<String, String> getRepositoryOutputs();

    @OutputFile
    public abstract RegularFileProperty getIndexFile();

    /** Adds every artifact of one resolvable configuration. */
    public void configuration(String name, Provider<Set<ResolvedArtifactResult>> artifacts) {
        getArtifacts().addAll(artifacts.map(resolved -> resolved.stream()
                .map(artifact -> name + "\t" + component(artifact.getId().getComponentIdentifier())).toList()));
        getArtifactFiles().addAll(artifacts.map(resolved -> resolved.stream()
                .map(ResolvedArtifactResult::getFile).toList()));
    }

    @TaskAction
    void write() throws IOException {
        List<String> artifacts = getArtifacts().get();
        List<File> files = getArtifactFiles().get();
        if (artifacts.size() != files.size()) {
            throw new GradleException("The provider index got " + artifacts.size() + " artifacts and " + files.size()
                    + " files: they must pair up.");
        }
        int release = getRelease().get();
        Path root = getRootDirectory().get().getAsFile().toPath().toAbsolutePath().normalize();
        Map<String, Provided> providers = new TreeMap<>();
        for (int i = 0; i < artifacts.size(); i++) {
            String[] fields = artifacts.get(i).split("\t", -1);
            Path file = files.get(i).toPath().toAbsolutePath().normalize();
            String name = file.startsWith(root) ? root.relativize(file).toString().replace(File.separatorChar, '/')
                    : file.getFileName().toString();
            String identity = String.join("\t", fields[1], fields[2], fields[3], fields[4], name);
            Provided provided = providers.computeIfAbsent(identity,
                    key -> read(fields[1], fields[2], fields[3], fields[4],
                            repositoryOwned(fields[1], fields[4], fields[2], name, file, root), name, file, release));
            provided.configurations().add(fields[0]);
        }
        try (BufferedWriter out = Files.newBufferedWriter(getIndexFile().get().getAsFile().toPath(),
                StandardCharsets.UTF_8)) {
            out.write("{\n  \"schema\": \"provider-index/1\",\n  \"project\": " + quote(getProjectPath().get())
                    + ",\n  \"release\": " + release + ",\n  \"providers\": [");
            String separator = "\n";
            for (Provided provided : providers.values()) {
                out.write(separator + provided.json());
                separator = ",\n";
            }
            out.write("\n  ],\n  \"duplicates\": [");
            separator = "\n";
            for (String duplicate : duplicates(providers.values())) {
                out.write(separator + duplicate);
                separator = ",\n";
            }
            out.write("\n  ]\n}\n");
        }
    }

    /** The kind, coordinate, version and build identity of a component, separated by tabs. */
    static String component(ComponentIdentifier id) {
        if (id instanceof ModuleComponentIdentifier module) {
            return "maven\t" + module.getGroup() + ":" + module.getModule() + "\t" + module.getVersion() + "\t";
        }
        if (id instanceof ProjectComponentIdentifier project) {
            return "project\t" + project.getProjectPath() + "\t\t" + project.getBuild().getBuildPath();
        }
        // A file dependency or another kind that no review covers: ExternalImportPolicyTest refuses its classes.
        return "unknown\t" + id.getDisplayName() + "\t\t";
    }

    private boolean repositoryOwned(String kind, String buildPath, String coordinate, String name,
                                    Path file, Path root) {
        if (!kind.equals("project") || !buildPath.equals(":")
                || !coordinate.equals(getRepositoryOutputs().get().get(name))) {
            return false;
        }
        try {
            // An output redirected through a symbolic link is not a repository-owned output.
            return file.toRealPath().equals(root.toRealPath().resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** What one artifact provides. */
    record Provided(String kind, String coordinate, String version, String buildPath, boolean repositoryOwned,
                    String file, String sha256, boolean multiRelease,
                    SortedSet<Integer> ignoredReleases, SortedSet<Integer> appliedReleases, SortedSet<String> classes,
                    SortedSet<String> configurations) {

        String label() {
            return kind + " " + (kind.equals("project") ? buildPath + " " : "") + coordinate
                    + (version.isEmpty() ? "" : ":" + version) + " (" + file + ")";
        }

        String json() {
            SortedSet<String> packages = new TreeSet<>();
            for (String name : classes) {
                packages.add(name.lastIndexOf('.') < 0 ? "" : name.substring(0, name.lastIndexOf('.')));
            }
            return "    {\n      \"kind\": " + quote(kind) + ",\n      \"coordinate\": " + quote(coordinate)
                    + ",\n      \"version\": " + quote(version) + ",\n      \"file\": " + quote(file)
                    + ",\n      \"sha256\": " + (sha256.isEmpty() ? "null" : quote(sha256))
                    + ",\n      \"buildPath\": " + quote(buildPath)
                    + ",\n      \"repositoryOwned\": " + repositoryOwned
                    + ",\n      \"configurations\": " + strings(configurations, "")
                    + ",\n      \"multiRelease\": " + multiRelease
                    + ",\n      \"appliedReleases\": " + appliedReleases
                    + ",\n      \"ignoredReleases\": " + ignoredReleases
                    + ",\n      \"packages\": " + strings(packages, "      ")
                    + ",\n      \"classes\": " + strings(classes, "      ") + "\n    }";
        }
    }

    /** The classes that more than one artifact provides on one configuration, as JSON objects, sorted. */
    static List<String> duplicates(Iterable<Provided> providers) {
        Map<String, Map<String, SortedSet<String>>> byConfiguration = new TreeMap<>();
        for (Provided provided : providers) {
            for (String configuration : provided.configurations()) {
                Map<String, SortedSet<String>> classes = byConfiguration.computeIfAbsent(configuration,
                        key -> new TreeMap<>());
                for (String name : provided.classes()) {
                    classes.computeIfAbsent(name, key -> new TreeSet<>()).add(provided.label());
                }
            }
        }
        List<String> duplicates = new ArrayList<>();
        byConfiguration.forEach((configuration, classes) -> classes.forEach((name, labels) -> {
            if (labels.size() > 1) {
                duplicates.add("    {\"configuration\": " + quote(configuration) + ", \"class\": " + quote(name)
                        + ", \"providers\": " + strings(labels, "") + "}");
            }
        }));
        return duplicates;
    }

    private static Provided read(String kind, String coordinate, String version, String buildPath,
                                 boolean repositoryOwned, String name, Path file,
                                 int release) {
        SortedSet<Integer> ignored = new TreeSet<>();
        SortedSet<Integer> applied = new TreeSet<>();
        SortedSet<String> classes = new TreeSet<>();
        try {
            if (Files.isDirectory(file)) {
                try (Stream<Path> walk = Files.walk(file)) {
                    for (Path path : walk.filter(Files::isRegularFile).toList()) {
                        addClass(classes, file.relativize(path).toString().replace(File.separatorChar, '/'));
                    }
                }
                return new Provided(kind, coordinate, version, buildPath, repositoryOwned, name, "", false, ignored,
                        applied, classes, new TreeSet<>());
            }
            if (!Files.isRegularFile(file) || !name.endsWith(".jar")) {
                throw new GradleException("The provider index cannot read " + name + ": it is neither a JAR nor a "
                        + "directory of classes.");
            }
            boolean multiRelease;
            try (JarFile jar = new JarFile(file.toFile(), false)) {
                Manifest manifest = jar.getManifest();
                multiRelease = manifest != null && "true".equalsIgnoreCase(
                        manifest.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE));
                for (ZipEntry entry : jar.stream().filter(entry -> !entry.isDirectory()).toList()) {
                    String entryName = entry.getName();
                    if (!entryName.startsWith(VERSIONS)) {
                        if (!entryName.startsWith("META-INF/")) {
                            addClass(classes, entryName);
                        }
                        continue;
                    }
                    String rest = entryName.substring(VERSIONS.length());
                    int slash = rest.indexOf('/');
                    if (slash < 0 || !RELEASE.matcher(rest.substring(0, slash)).matches()
                            || !rest.endsWith(".class")) {
                        continue;
                    }
                    int entryRelease = Integer.parseInt(rest.substring(0, slash));
                    if (multiRelease && entryRelease >= FIRST_VERSIONED_RELEASE && entryRelease <= release) {
                        applied.add(entryRelease);
                        addClass(classes, rest.substring(slash + 1));
                    } else {
                        ignored.add(entryRelease);
                    }
                }
            }
            return new Provided(kind, coordinate, version, buildPath, repositoryOwned, name, sha256(file), multiRelease,
                    ignored, applied, classes, new TreeSet<>());
        } catch (IOException e) {
            throw new UncheckedIOException("The provider index cannot read " + name, e);
        }
    }

    /** A class file's binary name, from its path inside the JAR or directory; module descriptors are not classes. */
    private static void addClass(SortedSet<String> classes, String path) {
        if (path.endsWith(".class") && !path.equals("module-info.class")) {
            classes.add(path.substring(0, path.length() - ".class".length()).replace('/', '.'));
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A JSON array of strings: on one line without an indent, else one value per line below it. */
    private static String strings(Iterable<String> values, String indent) {
        List<String> quoted = new ArrayList<>();
        values.forEach(value -> quoted.add(quote(value)));
        if (indent.isEmpty() || quoted.isEmpty()) {
            return "[" + String.join(", ", quoted) + "]";
        }
        return "[\n" + indent + "  " + String.join(",\n" + indent + "  ", quoted) + "\n" + indent + "]";
    }

    static String quote(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                default -> json.append(c < 0x20 ? "\\u%04x".formatted((int) c) : String.valueOf(c));
            }
        }
        return json.append('"').toString();
    }
}
