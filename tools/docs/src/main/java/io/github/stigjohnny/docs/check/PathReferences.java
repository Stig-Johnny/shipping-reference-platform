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
import org.jspecify.annotations.Nullable;

/**
 * The paths that inline code names (ADR 0023), read against one inventory of the working tree: its files, the
 * directories that hold them, and its links and other special files, each by its exact name, outside the directories
 * the walk skips. A directory without a file does not exist, as for git. The inventory is the working tree, untracked
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
 *   <li>A path must exist in exact letter case; one that differs only in case fails. A trailing slash names a directory.
 *       A pattern, a path with a glob or a placeholder, proves only that the directory before them exists.
 *   <li>Build output and tool state are never read as paths: a name {@code build} outside a source tree, {@code dist},
 *       {@code node_modules}, {@code .gradle}, {@code .git} and {@code .claude}; a marker on such a path fails, since
 *       nothing there can prove it. A link or another special file on the way fails, never counting as absent.
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
    private final Map<String, String> folded = new HashMap<>();

    /** The inventory: the regular files and the special files of the walk, by their paths from the root. */
    PathReferences(Set<String> files, Set<String> special) {
        this.files = Set.copyOf(files);
        this.special = Set.copyOf(special);
        directories.add("");
        for (String file : files) {
            for (int slash = file.indexOf('/'); slash > 0; slash = file.indexOf('/', slash + 1)) {
                directories.add(file.substring(0, slash));
            }
        }
        for (String entry : files) {
            folded.putIfAbsent(entry.toLowerCase(Locale.ROOT), entry);
        }
        for (String entry : directories) {
            folded.putIfAbsent(entry.toLowerCase(Locale.ROOT), entry);
        }
    }

    private enum State { EXISTS, MISSING, CASE, SPECIAL, EXCLUDED }

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
            case ROOT -> top ? read(path, "") : null;
            case DOCUMENT -> read(path, directory);
            case NONE -> top ? read(path, "") : beside ? read(path, directory) : null;
        };
        if (primary == null) {
            return;
        }
        @Nullable Reading other = path.anchor() == Anchor.NONE && top && !directory.isEmpty()
                ? read(path, directory) : null;
        boolean otherExists = other != null && other.state() == State.EXISTS;
        String quoted = where + ": `" + text + "` ";
        switch (primary.state()) {
            case EXCLUDED -> {
                // Build output and tool state are never read as paths.
            }
            case SPECIAL -> problems.add(quoted + special(primary));
            case CASE -> problems.add(quoted + subject(path) + "differs only in letter case from "
                    + found(path, primary) + ", which exists (ADR 0023)");
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
            Reading reading = read(path, base);
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
                case MISSING -> {
                    // Absent from this reading, as the marker says.
                }
            }
        }
    }

    /** Reads a path from one base: what it names there, or why that cannot be told. */
    private Reading read(PathReference path, String base) {
        List<String> names = new ArrayList<>();
        if (!base.isEmpty()) {
            names.addAll(List.of(base.split("/", -1)));
        }
        int start = names.size();
        names.addAll(path.names());
        if (excluded(names)) {
            return new Reading(base, "", State.EXCLUDED, "");
        }
        int end = start + path.fixed();
        String object = String.join("/", names.subList(0, end));
        for (int i = start + 1; i <= end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            if (special.contains(prefix)) {
                return new Reading(base, object, State.SPECIAL, prefix);
            }
        }
        boolean directory = path.pattern() || path.directory();
        if (directories.contains(object) || (!directory && files.contains(object))) {
            return new Reading(base, object, State.EXISTS, object);
        }
        String other = folded.get(object.toLowerCase(Locale.ROOT));
        if (other != null && (directories.contains(other) || !directory)) {
            return new Reading(base, object, State.CASE, other);
        }
        return new Reading(base, object, State.MISSING, "");
    }

    /** Whether a name of a path, from the root, is build output or tool state; a build directory in sources is not. */
    private static boolean excluded(List<String> names) {
        boolean sources = false;
        for (String name : names) {
            if (EXCLUDED.contains(name) || (name.equals(BUILD) && !sources)) {
                return true;
            }
            sources |= name.equals(SOURCES);
        }
        return false;
    }

    /** Whether a directory in {@code base} has this name in some letter case, outside build output and tool state. */
    private boolean directoryNamed(String base, String name) {
        String path = base.isEmpty() ? name : base + "/" + name;
        String found = folded.get(path.toLowerCase(Locale.ROOT));
        return found != null && directories.contains(found) && !excluded(List.of(found.split("/", -1)));
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
        String anchor = switch (path.anchor()) {
            case NONE -> "";
            case ROOT -> "/";
            case DOCUMENT -> "./";
        };
        return anchor + String.join("/", path.names().subList(0, path.fixed())) + "/";
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

    private static String special(Reading reading) {
        return "reaches " + reading.found() + ", a link or special file, which the check does not follow (ADR 0023)";
    }
}
