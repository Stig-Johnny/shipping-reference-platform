package io.github.stigjohnny.c4contractkit.sources;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The repository's dependency allowlist, {@code security/allowlist.json}, read as data: the reviewed artifacts with the
 * project and the configurations each review approves. The web import check asks it whether an npm package, at the
 * exact version the app declares, is approved for the app and the scope it is imported in, and whether the Node.js
 * runtime whose built-in modules tests import is approved. Only an approving verdict counts. A file that is missing,
 * not plain JSON or not shaped as expected makes the allowlist unavailable, and every question then has no answer.
 *
 * <p>Expiry is the allowlist check's to enforce (PolicyCheck, before the build): this reader takes no date, so that
 * identical inputs give identical reports.
 *
 * @param entries the entries of the file, in order
 * @param unavailable why the file could not be read, when it could not
 */
public record Allowlist(List<Entry> entries, Optional<String> unavailable) {

    /** Where the allowlist lies, relative to the repository root. */
    public static final String PATH = "security/allowlist.json";

    /** The runtime entry of the Node.js that runs the web app's tests in CI, the {@code web} job. */
    public static final String NODE_RUNTIME = "nodejs.org/dist node";

    private static final Set<String> APPROVING = Set.of("APPROVE", "APPROVE-WITH-CONDITIONS");

    /**
     * One reviewed artifact.
     *
     * @param project the project the review approves it for; for an entry that names several, each of them
     * @param configuration the configurations (scopes) the review approves it in, as written
     */
    public record Entry(String kind, String coordinate, String version, List<String> project,
                        List<String> configuration, String verdict, String reviewRecord) {

        public Entry {
            project = List.copyOf(project);
            configuration = List.copyOf(configuration);
        }

        /** The entry in one line, for reports: kind, coordinate, version, project, configuration, verdict, review. */
        public String summary() {
            return kind + " " + coordinate + " " + version + " for " + String.join(", ", project) + " ("
                    + String.join(", ", configuration.stream().map(String::strip).toList()) + "), " + verdict
                    + ", " + reviewRecord;
        }

        boolean approves() {
            return APPROVING.contains(verdict);
        }
    }

    public Allowlist {
        entries = List.copyOf(entries);
    }

    /**
     * Reads the allowlist; a file that is missing or cannot be read makes it unavailable.
     *
     * @param file the allowlist on disk
     */
    public static Allowlist read(Path file) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return unavailable(PATH + " does not exist or is not a regular file");
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            return unavailable(PATH + " is not valid UTF-8");
        } catch (IOException e) {
            throw new UncheckedIOException("The allowlist " + PATH + " could not be read", e);
        }
        Json.Value root;
        try {
            root = Json.parse(text);
        } catch (Json.Invalid e) {
            return unavailable(PATH + " is not plain JSON (line " + e.line() + ": " + e.getMessage() + ")");
        }
        Json.Value list = root.isObject() ? root.object().get("entries") : null;
        if (list == null || !list.isArray()) {
            return unavailable(PATH + " has no list of entries");
        }
        List<Entry> entries = new ArrayList<>();
        for (Json.Value value : list.array()) {
            Optional<Entry> entry = entry(value);
            if (entry.isEmpty()) {
                return unavailable(PATH + ": the entry on line " + value.line() + " lacks a kind, coordinate, "
                        + "version, project, configuration, verdict or review record");
            }
            entries.add(entry.get());
        }
        return new Allowlist(entries, Optional.empty());
    }

    /** An allowlist that answers nothing, for this reason. */
    public static Allowlist unavailable(String reason) {
        return new Allowlist(List.of(), Optional.of(reason));
    }

    /**
     * The approving entry for an npm package at an exact version, for a project, in one of these configurations, which
     * must be written exactly so: {@code dependencies} for code that runs in the browser, {@code devDependencies} too
     * for tests. A review of the package as another package's dependency does not count.
     */
    public Optional<Entry> npm(String name, String version, String project, Set<String> configurations) {
        return entries.stream().filter(entry -> entry.kind().equals("npm") && entry.coordinate().equals(name)
                && entry.version().equals(version) && entry.project().contains(project) && entry.approves()
                && entry.configuration().stream().map(String::strip).anyMatch(configurations::contains))
                .findFirst();
    }

    /** The approving entry for Node.js at an exact version, for project ci in the web job. */
    public Optional<Entry> nodeRuntime(String version) {
        return entries.stream().filter(entry -> entry.kind().equals("runtime")
                && entry.coordinate().equals(NODE_RUNTIME) && entry.version().equals(version) && entry.approves()
                && entry.project().contains("ci")
                && entry.configuration().stream().map(String::strip).anyMatch("web"::equals)).findFirst();
    }

    private static Optional<Entry> entry(Json.Value value) {
        if (!value.isObject()) {
            return Optional.empty();
        }
        Map<String, Json.Value> members = value.object();
        Optional<String> kind = string(members.get("kind"));
        Optional<String> coordinate = string(members.get("coordinate"));
        Optional<String> version = string(members.get("version"));
        Optional<List<String>> project = strings(members.get("project"));
        Optional<List<String>> configuration = strings(members.get("configuration"));
        Optional<String> verdict = string(members.get("verdict"));
        Optional<String> reviewRecord = string(members.get("reviewRecord"));
        if (kind.isEmpty() || coordinate.isEmpty() || version.isEmpty() || project.isEmpty()
                || configuration.isEmpty() || verdict.isEmpty() || reviewRecord.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Entry(kind.get(), coordinate.get(), version.get(), project.get(),
                configuration.get(), verdict.get(), reviewRecord.get()));
    }

    private static Optional<String> string(Json.@org.jspecify.annotations.Nullable Value value) {
        return value != null && value.isString() ? Optional.of(value.string()) : Optional.empty();
    }

    /** A string, or a list of strings, as a list. */
    private static Optional<List<String>> strings(Json.@org.jspecify.annotations.Nullable Value value) {
        if (value == null) {
            return Optional.empty();
        }
        if (value.isString()) {
            return Optional.of(List.of(value.string()));
        }
        if (!value.isArray()) {
            return Optional.empty();
        }
        List<String> strings = new ArrayList<>();
        for (Json.Value element : value.array()) {
            if (!element.isString()) {
                return Optional.empty();
            }
            strings.add(element.string());
        }
        return Optional.of(List.copyOf(strings));
    }
}
