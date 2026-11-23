package io.github.stigjohnny.docs.check;

import io.github.stigjohnny.docs.markdown.ReferenceMarkers;
import io.github.stigjohnny.docs.markdown.ReferenceMarkers.Anchor;
import io.github.stigjohnny.docs.markdown.ReferenceMarkers.PathReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The paths that inline code names (ADR 0023), read against one inventory of the working tree: its files, the
 * directories that hold them, and its links and other special files, each by its exact name, outside the directories
 * the walk skips, which are kept by their place. A directory without a file or a link does not exist, as for git. The inventory is the working tree, untracked
 * files included; a clean checkout, as in CI, is the authority.
 *
 * <ul>
 *   <li>A span is a path when the grammar of {@link ReferenceMarkers#path} reads one with an anchor or a slash. Written
 *       {@code /path}, it is read from the root, and only when its first name is a top-level directory, so that a
 *       route such as {@code /api/v1} is no path; written {@code ./path}, it is read from the document's directory,
 *       always. Without an anchor, it is read from the root when its first name is a top-level directory, else from
 *       the document's directory when its first name is a directory there, and else it is no path: a path under a
 *       directory that does not exist is read only when it is marked.
 *   <li>Without an anchor, the root comes first: a path read from the root that the document's directory also has is
 *       ambiguous, and one that only the document's directory has must say so with {@code ./}.
 *   <li>A path must exist in exact letter case; one that differs only in case fails, and so does one that writes a
 *       directory on its way in another case. A trailing slash names a directory.
 *       A pattern, a path with a glob or a placeholder, proves only that the directory before them exists.
 *   <li>Build output and tool state are never read as paths: a name {@code build} outside a source tree named
 *       {@code src}, {@code dist}, {@code node_modules}, {@code .gradle}, {@code .git} and {@code .claude}, in exact
 *       letter case as the walk and git see them; a marker on such a path fails, in any letter case, since nothing
 *       there can prove it, and a path that writes such a name in another letter case fails with the spelling to use.
 *       Before a path counts as output, a real path in another letter case is named. A link or another special file on the way fails, in any letter case, never counting
 *       as absent. Files, directories and special files are matched in other letter cases apart, so that a file never
 *       hides a directory.
 *   <li>{@code planned:path:} and {@code absent:path:} must exist in neither reading, in no letter case;
 *       {@code illustrative:path:} needs a placeholder and nothing else.
 * </ul>
 */
final class PathReferences {

    private static final Set<String> EXCLUDED = Set.of(".git", ".gradle", "node_modules", "dist", ".claude");
    private static final String BUILD = "build";
    private static final String SOURCES = "src";

    private final Set<String> files;
    private final Set<String> directories = new HashSet<>();
    private final Set<String> special;
    private final Map<String, String> foldedFiles = new HashMap<>();
    private final Map<String, String> foldedDirectories = new HashMap<>();
    private final Map<String, String> foldedSpecial = new HashMap<>();
    private final Map<String, String> foldedSkipped = new HashMap<>();

    /**
     * The inventory: the regular files and the special files of the walk, and the directories it skipped, by their
     * paths from the root. A directory exists when it holds a file or a special file, whose target is never followed;
     * a skipped directory is kept by its place only, so that no path through it in another letter case can pass.
     */
    PathReferences(Set<String> files, Set<String> special, Set<String> skipped) {
        this.files = Set.copyOf(files);
        this.special = Set.copyOf(special);
        directories.add("");
        for (Set<String> entries : List.of(files, special)) {
            for (String entry : entries) {
                for (int slash = entry.indexOf('/'); slash > 0; slash = entry.indexOf('/', slash + 1)) {
                    directories.add(entry.substring(0, slash));
                }
            }
        }
        fold(files, foldedFiles);
        fold(directories, foldedDirectories);
        fold(special, foldedSpecial);
        fold(skipped, foldedSkipped);
    }

    /**
     * Each entry by its name in lower case, files, directories and special files apart, so that a file never hides a
     * directory whose name differs only in letter case; of entries that differ only in case, the first in order.
     */
    private static void fold(Set<String> entries, Map<String, String> folded) {
        for (String entry : new TreeSet<>(entries)) {
            folded.putIfAbsent(entry.toLowerCase(Locale.ROOT), entry);
        }
    }

    /**
     * What a reading finds: {@code CASE} is the path in another letter case, {@code DIRECTORY_CASE} a directory on its
     * way, and {@code OUTPUT_CASE} build output or tool state written in another letter case.
     */
    private enum State { EXISTS, MISSING, CASE, DIRECTORY_CASE, SPECIAL, EXCLUDED, OUTPUT_CASE }

    /** A path read from one base: the root ("") or a document's directory, with what was found there. */
    private record Reading(String base, String object, State state, String found) {
    }

    /** Checks inline code that is no marker: a path when its shape and its first name say so (ADR 0023). */
    void bare(String text, String document, String where, Problems problems) {
        PathReference path = ReferenceMarkers.path(text);
        if (path == null || !path.shaped()) {
            return;
        }
        String directory = directoryOf(document);
        String first = path.names().getFirst();
        boolean top = directoryNamed("", first);
        boolean beside = !directory.isEmpty() && directoryNamed(directory, first);
        @Nullable Reading primary = switch (path.anchor()) {
            case ROOT -> top ? read(path, "", false) : null;
            case DOCUMENT -> read(path, directory, false);
            case NONE -> top ? read(path, "", false) : beside ? read(path, directory, false) : null;
        };
        if (primary == null) {
            return;
        }
        @Nullable Reading other = path.anchor() == Anchor.NONE && top && !directory.isEmpty()
                ? read(path, directory, false) : null;
        boolean otherExists = other != null && other.state() == State.EXISTS;
        String quoted = where + ": `" + text + "` ";
        switch (primary.state()) {
            case EXCLUDED -> {
                // Build output and tool state are never read as paths.
            }
            case OUTPUT_CASE -> problems.add(quoted + "names build output or tool state in another letter case; write "
                    + primary.found() + ", which no check reads (ADR 0023)");
            case SPECIAL -> problems.add(quoted + special(primary));
            case CASE -> problems.add(quoted + subject(path) + "differs only in letter case from "
                    + found(path, primary) + ", which exists (ADR 0023)");
            case DIRECTORY_CASE -> problems.add(quoted + directoryCase(primary));
            case EXISTS -> {
                if (otherExists) {
                    problems.add(quoted + (path.pattern() ? "is a pattern whose directory " + fixed(path)
                            + " exists both " : "names a file or directory both ") + from("") + " and "
                            + from(directory) + ": write /" + text + " for the first or ./" + text
                            + " for the second (ADR 0023)");
                }
            }
            case MISSING -> {
                String missing = path.pattern() ? "is a pattern whose directory " + fixed(path) + " does not exist "
                        : "names no " + (path.directory() ? "directory " : "file or directory ");
                problems.add(quoted + missing + from(primary.base()) + (otherExists ? ", but does "
                        + from(directory) + ": write ./" + text + " (ADR 0023)" : markers(text)));
            }
        }
    }

    /**
     * Checks the path of a {@code planned:path:}, {@code absent:path:} or {@code illustrative:path:} marker; {@code state}
     * is the marker's first word and {@code payload} the path after it.
     */
    void marked(String text, String state, String payload, String document, String where, Problems problems) {
        String quoted = where + ": `" + text + "` ";
        PathReference path = ReferenceMarkers.markedPath(state, payload);
        if (path == null) {
            PathReference written = ReferenceMarkers.path(payload);
            problems.add(quoted + (state.equals("illustrative") && written != null
                    ? "needs a placeholder in angle brackets, such as illustrative:path:docs/adr/<NNNN>-<title>.md"
                    : "needs one path, such as planned:path:policy/rulesets.json") + " (ADR 0023)");
            return;
        }
        if (state.equals("illustrative")) {
            return;
        }
        String directory = directoryOf(document);
        List<String> bases = switch (path.anchor()) {
            case ROOT -> List.of("");
            case DOCUMENT -> List.of(directory);
            case NONE -> directory.isEmpty() ? List.of("") : List.of("", directory);
        };
        String marked = where + ": `" + text + "`: ";
        String consequence = state.equals("planned") ? ", so name it without planned: (ADR 0023)"
                : ", so it is not absent (ADR 0023)";
        for (String base : bases) {
            Reading reading = read(path, base, true);
            switch (reading.state()) {
                case EXCLUDED -> {
                    problems.add(marked + payload + " is build output or tool state, which no check reads; write it "
                            + "without a marker (ADR 0023)");
                    return;
                }
                case SPECIAL -> {
                    problems.add(marked + payload + " " + special(reading));
                    return;
                }
                case EXISTS -> {
                    problems.add(marked + (path.pattern() ? "its directory " + fixed(path) : payload) + " exists "
                            + from(base) + consequence);
                    return;
                }
                case CASE -> {
                    problems.add(marked + (path.pattern() ? "its directory " + fixed(path) : payload)
                            + " differs only in letter case from " + found(path, reading) + ", which exists (ADR 0023)");
                    return;
                }
                case DIRECTORY_CASE -> {
                    problems.add(marked + payload + " " + directoryCase(reading));
                    return;
                }
                case MISSING, OUTPUT_CASE -> {
                    // Absent from this reading, as the marker says; a marker's path is never OUTPUT_CASE.
                }
            }
        }
    }

    /**
     * Reads a path from one base: what it names there, or why that cannot be told. A marker's path, {@code marked}, is
     * build output or tool state in any letter case, since a file system that ignores case finds it there; a link or
     * special file on the way counts in any letter case for every path.
     */
    private Reading read(PathReference path, String base, boolean marked) {
        List<String> names = new ArrayList<>();
        if (!base.isEmpty()) {
            names.addAll(List.of(base.split("/", -1)));
        }
        int start = names.size();
        names.addAll(path.names());
        int end = start + path.fixed();
        String object = String.join("/", names.subList(0, end));
        boolean directory = path.pattern() || path.directory();
        for (int i = start + 1; i <= end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            String skipped = foldedSkipped.get(prefix.toLowerCase(Locale.ROOT));
            if (skipped != null) {
                // A directory the walk skipped, in this letter case or another: nothing under it is known.
                String rest = String.join("/", names.subList(i, names.size()));
                String spelled = skipped + (rest.isEmpty() ? "" : "/" + rest) + (path.directory() ? "/" : "");
                return marked || skipped.equals(prefix) ? new Reading(base, "", State.EXCLUDED, "")
                        : new Reading(base, "", State.OUTPUT_CASE, (base.isEmpty() ? anchor(path) : "/") + spelled);
            }
        }
        boolean exact = excluded(names, false);
        if (exact || excluded(names, true)) {
            // Output as the walk and git see it, or in another letter case: a real path in another case is named first.
            Reading variant = variant(names, start, end, base, object, directory);
            if (variant != null) {
                return variant;
            }
            return exact || marked ? new Reading(base, "", State.EXCLUDED, "")
                    : new Reading(base, "", State.OUTPUT_CASE, canonical(path, names.subList(0, start)));
        }
        for (int i = start + 1; i <= end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            String link = special.contains(prefix) ? prefix : foldedSpecial.get(prefix.toLowerCase(Locale.ROOT));
            if (link != null) {
                return new Reading(base, object, State.SPECIAL, link);
            }
        }
        if (directories.contains(object) || (!directory && files.contains(object))) {
            return new Reading(base, object, State.EXISTS, object);
        }
        String folded = object.toLowerCase(Locale.ROOT);
        String other = foldedDirectories.get(folded);
        if (other == null && !directory) {
            other = foldedFiles.get(folded);
        }
        if (other != null) {
            return new Reading(base, object, State.CASE, other);
        }
        for (int i = start + 1; i < end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            if (!directories.contains(prefix)) {
                String directoryInCase = foldedDirectories.get(prefix.toLowerCase(Locale.ROOT));
                return directoryInCase == null ? new Reading(base, object, State.MISSING, "")
                        : new Reading(base, prefix, State.DIRECTORY_CASE, directoryInCase);
            }
        }
        return new Reading(base, object, State.MISSING, "");
    }

    /**
     * A path through output, read as the inventory has it in another letter case: the whole path, or a directory on its
     * way, that exists outside build output and tool state; null when there is none. A path written with a source
     * tree named SRC thus fails on its case when the real one is src, and never passes as output.
     */
    private @Nullable Reading variant(List<String> names, int start, int end, String base, String object,
            boolean directory) {
        String folded = object.toLowerCase(Locale.ROOT);
        String whole = foldedDirectories.get(folded);
        if (whole == null && !directory) {
            whole = foldedFiles.get(folded);
        }
        if (whole != null && !whole.equals(object) && !excluded(List.of(whole.split("/", -1)), false)) {
            return new Reading(base, object, State.CASE, whole);
        }
        for (int i = start + 1; i < end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            if (!directories.contains(prefix)) {
                String found = foldedDirectories.get(prefix.toLowerCase(Locale.ROOT));
                return found == null || excluded(List.of(found.split("/", -1)), false) ? null
                        : new Reading(base, prefix, State.DIRECTORY_CASE, found);
            }
        }
        return null;
    }

    /**
     * Whether a name of a path, from the root, is build output or tool state, in exact letter case as the walk and git
     * see it or, with {@code ignoreCase}, in any; a build directory in a source tree is not, and a source tree is a
     * directory named src in exact letter case in both modes, as the walk sees it.
     */
    private static boolean excluded(List<String> names, boolean ignoreCase) {
        boolean sources = false;
        for (String written : names) {
            String name = ignoreCase ? written.toLowerCase(Locale.ROOT) : written;
            if (EXCLUDED.contains(name) || (name.equals(BUILD) && !sources)) {
                return true;
            }
            sources |= written.equals(SOURCES);
        }
        return false;
    }

    /** Whether a directory in {@code base} has this name in some letter case, outside build output and tool state. */
    private boolean directoryNamed(String base, String name) {
        String path = base.isEmpty() ? name : base + "/" + name;
        String found = foldedDirectories.get(path.toLowerCase(Locale.ROOT));
        return found != null && !excluded(List.of(found.split("/", -1)), false);
    }

    /**
     * The spelling to use for a path that writes build output or tool state in another letter case: each such name in
     * its own case, so that the path is never read. {@code base} holds the names of the directory it is read from; when
     * one of them is such a name, the path is written from the root, since no spelling from that directory helps.
     */
    private static String canonical(PathReference path, List<String> base) {
        List<String> names = new ArrayList<>(base);
        names.addAll(path.names());
        boolean sources = false;
        boolean inBase = false;
        List<String> spelled = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String lower = names.get(i).toLowerCase(Locale.ROOT);
            boolean output = EXCLUDED.contains(lower) || (lower.equals(BUILD) && !sources);
            inBase |= output && i < base.size();
            spelled.add(output ? lower : names.get(i));
            sources |= names.get(i).equals(SOURCES);
        }
        String directory = path.directory() ? "/" : "";
        return inBase ? "/" + String.join("/", spelled) + directory
                : anchor(path) + String.join("/", spelled.subList(base.size(), spelled.size())) + directory;
    }

    private static String directoryOf(String document) {
        int slash = document.lastIndexOf('/');
        return slash < 0 ? "" : document.substring(0, slash);
    }

    private static String from(String base) {
        return base.isEmpty() ? "from the repository's root" : "from " + base + "/, this document's directory";
    }

    /** A pattern's directory as written, with its anchor and a trailing slash. */
    private static String fixed(PathReference path) {
        return anchor(path) + String.join("/", path.names().subList(0, path.fixed())) + "/";
    }

    private static String anchor(PathReference path) {
        return switch (path.anchor()) {
            case NONE -> "";
            case ROOT -> "/";
            case DOCUMENT -> "./";
        };
    }

    /** The markers to use for a path that does not exist. */
    private static String markers(String text) {
        return "; if it is planned, write planned:path:" + text + ", and if it is meant to be absent, absent:path:" + text
                + " (ADR 0023)";
    }

    private static String subject(PathReference path) {
        return path.pattern() ? "is a pattern whose directory " + fixed(path) + " " : "";
    }

    /** What a reading found, from the root, with a trailing slash where the path names a directory. */
    private static String found(PathReference path, Reading reading) {
        return reading.found() + (path.pattern() || path.directory() ? "/" : "");
    }

    /** A directory on a path's way, written in another letter case than the one that exists. */
    private static String directoryCase(Reading reading) {
        return "writes the directory " + reading.object() + "/ in another letter case: " + reading.found()
                + "/ exists (ADR 0023)";
    }

    private static String special(Reading reading) {
        return "reaches " + reading.found() + ", a link or special file, which the check does not follow (ADR 0023)";
    }
}
