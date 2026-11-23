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
 * the walk skips, which are kept by their place. A directory without a file or a link outside build output and tool
 * state does not exist, as for git. The inventory is the working tree, untracked
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
 *   <li>A path must exist in exact letter case, and one outside output that does is found before any rule about
 *       output; one that differs only in case fails, and so does one that writes a directory on its way in another
 *       case, every spelling of it counting. A trailing slash names a directory.
 *       A pattern, a path with a glob or a placeholder, proves only that the directory before them exists.
 *   <li>Build output and tool state are never read as paths: a name {@code build} outside a source tree named
 *       {@code src}, {@code dist}, {@code node_modules}, {@code .gradle}, {@code .git} and {@code .claude}, in exact
 *       letter case as the walk and git see them; a marker on such a path fails, in any letter case where no real
 *       directory has the name as written, since nothing there can prove it, and a path that writes such a name in
 *       another letter case there fails with the spelling to use.
 *       Before a path counts as output, a real path in another letter case is named. A directory the walk skipped
 *       stops a marker in any letter case, the document's own directory included, and a bare path only where no real
 *       directory has the name. A link or another special file on the way fails, in any letter case, never counting
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
    private final Map<String, List<String>> foldedFiles = new HashMap<>();
    private final Map<String, List<String>> foldedDirectories = new HashMap<>();
    private final Map<String, List<String>> foldedSpecial = new HashMap<>();
    private final Map<String, List<String>> foldedSkipped = new HashMap<>();

    /**
     * The inventory: the regular files and the special files of the walk, and the directories it skipped, by their
     * paths from the root. A directory exists when it holds a file or a special file outside build output and tool
     * state, whose target is never followed; a skipped directory is kept by its place only, so that no path through it
     * in another letter case can pass.
     */
    PathReferences(Set<String> files, Set<String> special, Set<String> skipped) {
        this.files = Set.copyOf(files);
        this.special = Set.copyOf(special);
        directories.add("");
        for (Set<String> entries : List.of(files, special)) {
            for (String entry : entries) {
                // Only what lies outside build output and tool state makes a directory exist, as for git: a file the
                // walk collects in dist never makes its directories appear.
                if (excluded(List.of(entry.split("/", -1)))) {
                    continue;
                }
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
     * directory whose name differs only in letter case; every spelling is kept, in order, so that none hides another.
     */
    private static void fold(Set<String> entries, Map<String, List<String>> folded) {
        for (String entry : new TreeSet<>(entries)) {
            folded.computeIfAbsent(entry.toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(entry);
        }
    }

    /** The first spelling of a path in {@code folded}, in any letter case, or null. */
    private static @Nullable String any(Map<String, List<String>> folded, String path) {
        List<String> spellings = folded.get(path.toLowerCase(Locale.ROOT));
        return spellings == null ? null : spellings.getFirst();
    }

    /**
     * The first spelling of a path in {@code folded}, in any letter case, outside build output and tool state, and
     * other than {@code written}; null when there is none.
     */
    private static @Nullable String real(Map<String, List<String>> folded, String path, String written) {
        for (String spelling : folded.getOrDefault(path.toLowerCase(Locale.ROOT), List.of())) {
            if (!spelling.equals(written) && !excluded(List.of(spelling.split("/", -1)))) {
                return spelling;
            }
        }
        return null;
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
        // A link on the document's reading leaves that reading unknown, so it decides nothing in the path's favour.
        @Nullable Reading otherLink = other != null && other.state() == State.SPECIAL ? other : null;
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
                if (otherLink != null) {
                    problems.add(quoted + special(otherLink));
                } else if (otherExists) {
                    problems.add(quoted + (path.pattern() ? "is a pattern whose directory " + fixed(path)
                            + " exists both " : "names a file or directory both ") + from("") + " and "
                            + from(directory) + ": write /" + text + " for the first or ./" + text
                            + " for the second (ADR 0023)");
                }
            }
            case MISSING -> {
                if (otherLink != null) {
                    problems.add(quoted + special(otherLink));
                    return;
                }
                String missing = path.pattern() ? "is a pattern whose directory " + fixed(path) + " does not exist "
                        : "names no " + (path.directory() ? "directory " : "file or directory ");
                // The markers are offered only when one would hold; otherwise the message says why none can.
                @Nullable Reading marker = otherExists ? null : markerReading(path, document);
                problems.add(quoted + missing + from(primary.base()) + (otherExists ? ", but does "
                        + from(directory) + ": write ./" + text + " (ADR 0023)"
                        : marker != null ? ", and no marker can say so: " + markerReason(path, marker)
                        : markers(text)));
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
        Reading reading = markerReading(path, document);
        if (reading == null) {
            return;
        }
        String marked = where + ": `" + text + "`: ";
        String consequence = state.equals("planned") ? ", so name it without planned: (ADR 0023)"
                : ", so it is not absent (ADR 0023)";
        switch (reading.state()) {
            case EXCLUDED -> problems.add(marked + payload + " is build output or tool state, which no check reads; "
                    + "write it without a marker (ADR 0023)");
            case SPECIAL -> problems.add(marked + payload + " " + special(reading));
            case EXISTS -> problems.add(marked + (path.pattern() ? "its directory " + fixed(path) : payload) + " exists "
                    + from(reading.base()) + consequence);
            case CASE -> problems.add(marked + (path.pattern() ? "its directory " + fixed(path) : payload)
                    + " differs only in letter case from " + found(path, reading) + ", which exists (ADR 0023)");
            case DIRECTORY_CASE -> problems.add(marked + payload + " " + directoryCase(reading));
            case MISSING, OUTPUT_CASE -> throw new IllegalStateException("a marker's reading that holds: " + payload);
        }
    }

    /**
     * The first reading that stops a planned or absent marker on this path, over the bases the marker is read from:
     * the root and the document's directory without an anchor, or the one its anchor names; null when the marker holds.
     */
    private @Nullable Reading markerReading(PathReference path, String document) {
        String directory = directoryOf(document);
        List<String> bases = switch (path.anchor()) {
            case ROOT -> List.of("");
            case DOCUMENT -> List.of(directory);
            case NONE -> directory.isEmpty() ? List.of("") : List.of("", directory);
        };
        for (String base : bases) {
            Reading reading = read(path, base, true);
            if (reading.state() != State.MISSING && reading.state() != State.OUTPUT_CASE) {
                return reading;
            }
        }
        return null;
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
        // A link or special file on the way, in any letter case, the document's own directory included, before all.
        for (int i = 1; i <= end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            String link = special.contains(prefix) ? prefix : any(foldedSpecial, prefix);
            if (link != null) {
                return new Reading(base, object, State.SPECIAL, link);
            }
        }
        // A path outside output that exists exactly is found next: a real directory named Dist is no output. A file in
        // output that the walk collects is no shortcut, so that it never hides a real spelling.
        boolean exact = excluded(names);
        if (!exact && (directories.contains(object) || (!directory && files.contains(object)))) {
            return new Reading(base, object, State.EXISTS, object);
        }
        // Output by its names, as the walk and git see them, or in another letter case: a real path in another case is
        // named first, so that a source path written SRC fails on its case.
        if (exact || outputInAnotherCase(names)) {
            Reading variant = variant(names, start, end, base, object, directory);
            if (variant != null) {
                return variant;
            }
            return exact || marked ? new Reading(base, "", State.EXCLUDED, "")
                    : new Reading(base, "", State.OUTPUT_CASE, canonical(path, names, start));
        }
        Reading found = lookup(names, start, end, base, object, directory);
        if (found != null) {
            return found;
        }
        // Missing from the inventory: a directory the walk skipped may hold it. A marker stops at any of them in any
        // letter case, the document's own directory included; a bare path only where no real directory has the name.
        for (int i = 1; i <= end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            String skipped = any(foldedSkipped, prefix);
            if (skipped != null && (marked || !directories.contains(prefix))) {
                String rest = String.join("/", names.subList(i, names.size()));
                String spelled = skipped + (rest.isEmpty() ? "" : "/" + rest) + (path.directory() ? "/" : "");
                return marked || skipped.equals(prefix) ? new Reading(base, "", State.EXCLUDED, "")
                        : new Reading(base, "", State.OUTPUT_CASE, (base.isEmpty() ? anchor(path) : "/") + spelled);
            }
        }
        return new Reading(base, object, State.MISSING, "");
    }

    /**
     * The path in the inventory in another letter case (CASE), or with a directory on its way in another letter case
     * (DIRECTORY_CASE); null when it is missing. A path that exists as written is found before.
     */
    private @Nullable Reading lookup(List<String> names, int start, int end, String base, String object,
            boolean directory) {
        String other = any(foldedDirectories, object);
        if (other == null && !directory) {
            other = any(foldedFiles, object);
        }
        if (other != null) {
            return new Reading(base, object, State.CASE, other);
        }
        for (int i = start + 1; i < end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            if (!directories.contains(prefix)) {
                String directoryInCase = any(foldedDirectories, prefix);
                return directoryInCase == null ? null
                        : new Reading(base, prefix, State.DIRECTORY_CASE, directoryInCase);
            }
        }
        return null;
    }

    /**
     * A path through output, read as the inventory has it in another letter case: the whole path, or a directory on its
     * way, that exists outside build output and tool state; null when there is none. A path written with a source
     * tree named SRC thus fails on its case when the real one is src, and never passes as output.
     */
    private @Nullable Reading variant(List<String> names, int start, int end, String base, String object,
            boolean directory) {
        String whole = real(foldedDirectories, object, object);
        if (whole == null && !directory) {
            whole = real(foldedFiles, object, object);
        }
        if (whole != null) {
            return new Reading(base, object, State.CASE, whole);
        }
        for (int i = start + 1; i < end; i++) {
            String prefix = String.join("/", names.subList(0, i));
            if (directories.contains(prefix) && !excluded(List.of(prefix.split("/", -1)))) {
                continue;
            }
            // Missing as written, or output as written: a real directory in another letter case is named, so that an
            // output directory written exactly never hides it.
            String found = real(foldedDirectories, prefix, prefix);
            return found == null ? null : new Reading(base, prefix, State.DIRECTORY_CASE, found);
        }
        return null;
    }

    /**
     * Whether a name of a path, from the root, is build output or tool state in exact letter case, as the walk and git
     * see it: build outside a source tree named src exactly, dist, node_modules, .gradle, .git or .claude.
     */
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
        return real(foldedDirectories, path, "") != null;
    }

    /**
     * Whether a name of a path, read from the root, is build output or tool state in another letter case: an output
     * name in any case, where no real directory exists as written. A real directory named Dist or BUILD, which holds a
     * file outside output, is a directory like any other; a source tree is a directory named src exactly, as the walk
     * sees it.
     */
    private boolean outputInAnotherCase(List<String> names) {
        boolean sources = false;
        for (int i = 0; i < names.size(); i++) {
            String lower = names.get(i).toLowerCase(Locale.ROOT);
            boolean output = EXCLUDED.contains(lower) || (lower.equals(BUILD) && !sources);
            if (output && !directories.contains(String.join("/", names.subList(0, i + 1)))) {
                return true;
            }
            sources |= names.get(i).equals(SOURCES);
        }
        return false;
    }

    /**
     * The spelling to use for a path that writes build output or tool state in another letter case: each such name in
     * its own case, where no real directory exists as written, so that the path is never read. The names before
     * {@code start} are the document's directory, which holds the document and so always exists as written.
     */
    private String canonical(PathReference path, List<String> names, int start) {
        boolean sources = false;
        List<String> spelled = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String lower = names.get(i).toLowerCase(Locale.ROOT);
            boolean output = (EXCLUDED.contains(lower) || (lower.equals(BUILD) && !sources))
                    && !directories.contains(String.join("/", names.subList(0, i + 1)));
            if (i >= start) {
                spelled.add(output ? lower : names.get(i));
            }
            sources |= names.get(i).equals(SOURCES);
        }
        return anchor(path) + String.join("/", spelled) + (path.directory() ? "/" : "");
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

    /** Why no planned or absent marker could hold for a path, from the reading that stops it. */
    private String markerReason(PathReference path, Reading reading) {
        return switch (reading.state()) {
            case EXCLUDED -> "it lies in build output or tool state, or under a directory the walk skips, in some letter "
                    + "case (ADR 0023)";
            case SPECIAL -> "it " + special(reading);
            case EXISTS -> "it exists " + from(reading.base()) + " (ADR 0023)";
            case CASE -> "it differs only in letter case from " + found(path, reading) + ", which exists (ADR 0023)";
            case DIRECTORY_CASE -> "it " + directoryCase(reading);
            case MISSING, OUTPUT_CASE -> throw new IllegalStateException("a marker's reading that holds");
        };
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
